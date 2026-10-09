// Indexing service.
// Album/album scanning, per-image encoding, batched DB insertion,
// IndexProgress streaming with cancel semantics matching the Rust version:
// when the listener cancels the subscription, the encode loop breaks and
// already-encoded-but-not-inserted results are NOT written; post-loop cleanup
// (deleted-record removal, album record refresh) still runs, then the
// function completes with cancelled=true (mirrors Rust returning Ok(false)).

import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';
import 'package:path/path.dart' as p;
import 'package:picquery_app/src/engine/db.dart';
import 'package:picquery_app/src/engine/image_preprocess.dart';
import 'package:picquery_app/src/engine/models.dart';
import 'package:picquery_app/src/engine/ort_engine.dart';

final _log = Logger('engine.indexer');

const List<String> _supportedExtensions = ['jpg', 'jpeg', 'png', 'webp', 'bmp'];

bool _isSupportedImage(String path) {
  final ext = p.extension(path).replaceFirst('.', '').toLowerCase();
  return _supportedExtensions.contains(ext);
}

String _formatFromPath(String path) {
  final ext = p.extension(path).replaceFirst('.', '').toLowerCase();
  switch (ext) {
    case 'jpg':
    case 'jpeg':
      return 'jpeg';
    case 'png':
      return 'png';
    case 'webp':
      return 'webp';
    case 'bmp':
      return 'bmp';
    default:
      return ext;
  }
}

int _nowSeconds() => DateTime.now().millisecondsSinceEpoch ~/ 1000;

/// Recursively scan a directory for supported image files.
List<String> _scanImages(String dirPath) {
  final dir = Directory(dirPath);
  if (!dir.existsSync()) {
    throw Exception('Not a directory: $dirPath');
  }
  final images = <String>[];
  _scanImagesRecursive(dir, images);
  return images;
}

void _scanImagesRecursive(Directory dir, List<String> images) {
  List<FileSystemEntity> entries;
  try {
    entries = dir.listSync(followLinks: true);
  } catch (e) {
    throw Exception("Failed to read directory '${dir.path}': $e");
  }
  for (final entity in entries) {
    if (entity is Directory) {
      _scanImagesRecursive(entity, images);
    } else if (entity is File && _isSupportedImage(entity.path)) {
      images.add(entity.path);
    }
  }
}

/// Checks a dropped folder using the same recursive scan as indexing.
Future<bool> hasIndexableImages(String albumPath) =>
    Isolate.run(() => _scanImages(albumPath).isNotEmpty);

/// Scans all indexed albums away from the UI isolate.
///
/// Update checks can walk a large directory tree. Keeping synchronous file
/// system traversal here prevents the startup check from delaying frames.
Future<Map<String, List<String>>> _scanAlbumsInBackground(
  List<String> albumPaths,
) {
  return Isolate.run(() {
    final pathsByAlbum = <String, List<String>>{};
    for (final albumPath in albumPaths) {
      if (Directory(albumPath).existsSync()) {
        pathsByAlbum[albumPath] = _scanImages(albumPath);
      }
    }
    return pathsByAlbum;
  });
}

/// Metadata for an image pending indexing.
class _PendingImage {
  final String pathStr;
  final String fileName;
  final int fileSize;
  final int modifiedTime;
  final String format;
  final int albumId;
  final String albumPath;
  const _PendingImage({
    required this.pathStr,
    required this.fileName,
    required this.fileSize,
    required this.modifiedTime,
    required this.format,
    required this.albumId,
    required this.albumPath,
  });
}

/// Result of encoding an image.
class _EncodedImage {
  final String pathStr;
  final String fileName;
  final int fileSize;
  final int modifiedTime;
  final int width;
  final int height;
  final String format;
  final Float32List embedding;
  final int albumId;
  const _EncodedImage({
    required this.pathStr,
    required this.fileName,
    required this.fileSize,
    required this.modifiedTime,
    required this.width,
    required this.height,
    required this.format,
    required this.embedding,
    required this.albumId,
  });
}

/// Pending update info for a album, stored after checkForUpdates().
class _AlbumUpdatePending {
  final int albumId;
  final String albumPath;
  final List<String> newPhotos;
  final List<String> deletedPaths;
  const _AlbumUpdatePending({
    required this.albumId,
    required this.albumPath,
    required this.newPhotos,
    required this.deletedPaths,
  });
}

/// Global storage for pending updates, populated by checkForUpdates() and
/// consumed by indexPendingUpdates().
List<_AlbumUpdatePending>? _pendingUpdates;

/// Mutable cancellation flag shared between the stream controller's onCancel
/// callback and the indexing body.
class _CancelFlag {
  bool value = false;
  bool failed = false;
}

String _formatMilliseconds(Duration duration) =>
    (duration.inMicroseconds / 1000).toStringAsFixed(1);

/// Send a progress event; returns false when the subscription was cancelled
/// (mirrors the Rust send_progress sink-closed check).
bool _send(
  StreamController<IndexProgress> controller,
  _CancelFlag cancelled,
  IndexProgress progress,
) {
  if (cancelled.value || controller.isClosed) return false;
  controller.add(progress);
  return true;
}

Future<_PendingImage?> _buildPendingImage({
  required String pathStr,
  required int albumId,
  required String albumPath,
}) async {
  final file = File(pathStr);
  final FileStat stat;
  try {
    stat = file.statSync();
  } catch (_) {
    _log.severe('Failed to read image metadata.');
    return null;
  }

  return _PendingImage(
    pathStr: pathStr,
    fileName: p.basename(pathStr),
    fileSize: stat.size,
    modifiedTime: stat.modified.millisecondsSinceEpoch ~/ 1000,
    format: _formatFromPath(pathStr),
    albumId: albumId,
    albumPath: albumPath,
  );
}

/// Generic batch image indexing: encodes each pending image, sends progress,
/// and inserts encoded results in batches of [kBatchSize] per album.
/// Returns true if completed normally, false if cancelled by the listener.
Future<bool> _indexPendingImages(
  StreamController<IndexProgress> controller,
  _CancelFlag cancelled,
  List<_PendingImage> pending, {
  required int alreadyIndexed,
  required int totalImages,
}) async {
  final now = _nowSeconds();

  final totalPending = pending.length;
  if (totalPending == 0) {
    return true;
  }

  if (!_send(
    controller,
    cancelled,
    IndexProgress(current: alreadyIndexed, total: totalImages, errors: 0),
  )) {
    return false;
  }

  var completed = 0;
  var errorsCount = 0;
  var timedImages = 0;
  var totalImageTimeUs = 0;
  var minImageTimeUs = 0;
  var maxImageTimeUs = 0;
  var lastLoggedProgressBucket = -1;
  final batch = <_EncodedImage>[];

  Future<void> flushBatch() async {
    if (batch.isEmpty) return;
    try {
      final first = batch.first;
      Db.instance.insertImagesAndVectorsBatch(
        first.albumId,
        batch
            .map(
              (e) => ImageRowData(
                filePath: e.pathStr,
                fileName: e.fileName,
                fileSize: e.fileSize,
                modifiedTime: e.modifiedTime,
                width: e.width,
                height: e.height,
                format: e.format,
                indexedAt: now,
              ),
            )
            .toList(),
        batch.map((e) => e.embedding).toList(),
      );
      batch.clear();
    } catch (error, stackTrace) {
      _log.severe('Failed to insert an image batch.', error, stackTrace);
      rethrow;
    }
  }

  // Keep one decode in flight while ONNX Runtime evaluates the preceding
  // image. On Windows, JPEG decode and CPU/DirectML inference use different
  // native workers, so this overlaps the two dominant costs without running
  // two model evaluations against the same session concurrently.
  var nextPreprocessed = preprocessImageWithMetadata(pending.first.pathStr);

  for (var index = 0; index < pending.length; index++) {
    final img = pending[index];
    final ({Float32List embedding, int width, int height}) encoded;
    try {
      final preprocessed = await nextPreprocessed;

      // Start the next file read/decode before inference. The tensor worker is
      // persistent and serial, while the codec and ORT run outside the Dart UI
      // isolate, so this is bounded to one look-ahead image in memory.
      if (index + 1 < pending.length && !cancelled.value) {
        nextPreprocessed = preprocessImageWithMetadata(
          pending[index + 1].pathStr,
        );
      }

      final encodeWatch = Stopwatch()..start();
      final embedding = await OrtEngine.instance.encodeImage(
        preprocessed.tensor,
      );
      final encodeElapsed = encodeWatch.elapsed;
      encoded = (
        embedding: embedding,
        width: preprocessed.width,
        height: preprocessed.height,
      );

      final imageTimeUs =
          preprocessed.timings.total.inMicroseconds +
          encodeElapsed.inMicroseconds;
      timedImages++;
      totalImageTimeUs += imageTimeUs;
      if (timedImages == 1 || imageTimeUs < minImageTimeUs) {
        minImageTimeUs = imageTimeUs;
      }
      if (imageTimeUs > maxImageTimeUs) maxImageTimeUs = imageTimeUs;

      final processed = index + 1;
      final progressBucket = processed * 10 ~/ totalPending;
      final shouldLog =
          index == 0 ||
          processed == totalPending ||
          progressBucket > lastLoggedProgressBucket;
      if (shouldLog) {
        lastLoggedProgressBucket = progressBucket;
        final averageUs = totalImageTimeUs ~/ timedImages;
        _log.info(
          'Index timing: photo=$processed/$totalPending '
          'progress=${(processed * 100 / totalPending).toStringAsFixed(1)}% '
          'path=${img.pathStr} '
          'preprocess(read+header=${_formatMilliseconds(preprocessed.timings.readAndHeader)}ms, '
          'decode=${_formatMilliseconds(preprocessed.timings.decode)}ms, '
          'crop=${_formatMilliseconds(preprocessed.timings.crop)}ms, '
          'tensor=${_formatMilliseconds(preprocessed.timings.tensor)}ms, '
          'total=${_formatMilliseconds(preprocessed.timings.total)}ms) '
          'encode=${_formatMilliseconds(encodeElapsed)}ms '
          'image(avg=${_formatMilliseconds(Duration(microseconds: averageUs))}ms, '
          'max=${_formatMilliseconds(Duration(microseconds: maxImageTimeUs))}ms, '
          'min=${_formatMilliseconds(Duration(microseconds: minImageTimeUs))}ms)',
        );
      }
    } catch (error, stackTrace) {
      _log.severe('Failed to encode image.', error, stackTrace);
      // Model/session failures affect every following image. Propagate them
      // through the stream instead of reporting normal completion.
      if (error is StateError) rethrow;
      errorsCount++;
      completed++;
      if (!_send(
        controller,
        cancelled,
        IndexProgress(
          current: alreadyIndexed + completed,
          total: totalImages,
          errors: errorsCount,
          currentPath: img.pathStr,
          currentAlbum: img.albumPath,
        ),
      )) {
        break;
      }
      continue;
    }

    completed++;
    if (!_send(
      controller,
      cancelled,
      IndexProgress(
        current: alreadyIndexed + completed,
        total: totalImages,
        errors: errorsCount,
        currentPath: img.pathStr,
        currentAlbum: img.albumPath,
      ),
    )) {
      break;
    }

    batch.add(
      _EncodedImage(
        pathStr: img.pathStr,
        fileName: img.fileName,
        fileSize: img.fileSize,
        modifiedTime: img.modifiedTime,
        width: encoded.width,
        height: encoded.height,
        format: img.format,
        embedding: encoded.embedding,
        albumId: img.albumId,
      ),
    );
    if (batch.length == kBatchSize) {
      await flushBatch();
    }
  }

  if (!cancelled.value) await flushBatch();
  return !cancelled.value && errorsCount == 0;
}

/// Update the album record with the current image count.
void _updateAlbumRecord(Db db, int albumId, int now, int total, bool complete) {
  final totalIndexed = db.getFolderImageCount(albumId);
  db.updateFolder(
    albumId,
    now,
    totalIndexed,
    totalImageCount: total,
    isIndexComplete: complete,
  );
}

/// Index all supported images in a album (recursively).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
Stream<IndexProgress> indexAlbum({
  required String albumPath,
  required bool isUpdate,
}) {
  return _runStream((controller, cancelled) async {
    if (!Directory(albumPath).existsSync()) {
      throw Exception('Not a directory: $albumPath');
    }

    final db = Db.instance;
    final now = _nowSeconds();

    var albumId = 0;
    var existingPaths = <String>{};
    final existing = db.findFolderByPath(albumPath);
    if (existing != null) {
      albumId = existing.id;
      existingPaths = isUpdate
          ? db.getIndexedFilePaths(albumId).toSet()
          : <String>{};
    } else {
      albumId = db.insertFolder(albumPath, now);
    }

    final imagePaths = _scanImages(albumPath);
    final total = imagePaths.length;

    final pending = <_PendingImage>[];
    for (final pathStr in imagePaths) {
      if (isUpdate && existingPaths.contains(pathStr)) {
        continue;
      }
      final img = await _buildPendingImage(
        pathStr: pathStr,
        albumId: albumId,
        albumPath: albumPath,
      );
      if (img != null) {
        pending.add(img);
      }
    }

    final toEncode = isUpdate
        ? pending
        : pending
              .where((img) => !db.isImageIndexed(img.pathStr, img.modifiedTime))
              .toList();
    final alreadyIndexed = total - toEncode.length;
    _updateAlbumRecord(db, albumId, now, total, false);

    final result = await _indexPendingImages(
      controller,
      cancelled,
      toEncode,
      alreadyIndexed: alreadyIndexed,
      totalImages: total,
    );

    // Handle deleted files.
    if (isUpdate) {
      final toDelete = existingPaths
          .where((path) => !imagePaths.contains(path))
          .toList();
      if (toDelete.isNotEmpty) {
        final deleted = db.deleteImagesByPaths(albumId, toDelete);
        _log.info('Deleted $deleted removed image records.');
      }
    }

    if (alreadyIndexed > 0) {
      final totalIndexed = db.getFolderImageCount(albumId);
      _send(
        controller,
        cancelled,
        IndexProgress(
          current: totalIndexed,
          total: total,
          errors: 0,
          currentAlbum: albumPath,
        ),
      );
    }

    _updateAlbumRecord(db, albumId, now, total, result);

    return result;
  });
}

/// Index a list of image file paths directly (for mobile album selection).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
Stream<IndexProgress> indexImages({
  required String albumName,
  String? displayName,
  required List<String> imagePaths,
  required bool isUpdate,
}) {
  return _runStream((controller, cancelled) async {
    final db = Db.instance;
    final now = _nowSeconds();
    final effectiveDisplayName = displayName ?? albumName;

    var albumId = 0;
    var existingPaths = <String>{};
    final existing = db.findFolderByPath(albumName);
    if (existing != null) {
      albumId = existing.id;
      db.updateFolderDisplayName(albumId, effectiveDisplayName);
      existingPaths = isUpdate
          ? db.getIndexedFilePaths(albumId).toSet()
          : <String>{};
    } else {
      albumId = db.insertFolder(
        albumName,
        now,
        displayName: effectiveDisplayName,
      );
    }

    final total = imagePaths.length;

    final pending = <_PendingImage>[];
    for (final pathStr in imagePaths) {
      if (isUpdate && existingPaths.contains(pathStr)) {
        continue;
      }
      final img = await _buildPendingImage(
        pathStr: pathStr,
        albumId: albumId,
        albumPath: albumName,
      );
      if (img != null) {
        pending.add(img);
      }
    }

    final toEncode = isUpdate
        ? pending
        : pending
              .where((img) => !db.isImageIndexed(img.pathStr, img.modifiedTime))
              .toList();
    final alreadyIndexed = total - toEncode.length;
    _updateAlbumRecord(db, albumId, now, total, false);

    final result = await _indexPendingImages(
      controller,
      cancelled,
      toEncode,
      alreadyIndexed: alreadyIndexed,
      totalImages: total,
    );

    // Handle deleted files.
    if (isUpdate) {
      final toDelete = existingPaths
          .where((path) => !imagePaths.contains(path))
          .toList();
      if (toDelete.isNotEmpty) {
        final deleted = db.deleteImagesByPaths(albumId, toDelete);
        _log.info('Deleted $deleted removed image records.');
      }
    }

    if (alreadyIndexed > 0) {
      final totalIndexed = db.getFolderImageCount(albumId);
      _send(
        controller,
        cancelled,
        IndexProgress(
          current: totalIndexed,
          total: total,
          errors: 0,
          currentAlbum: albumName,
        ),
      );
    }

    _updateAlbumRecord(db, albumId, now, total, result);

    return result;
  });
}

/// Check for index updates across all indexed albums.
/// Returns (album_id, album_path, new_count, deleted_count) for albums with
/// any changes, and stores the pending updates for indexPendingUpdates().
Future<List<AlbumUpdateInfo>> checkForUpdates() async {
  final db = Db.instance;
  final albums = db.getAllFolders();
  final results = <AlbumUpdateInfo>[];
  final pending = <_AlbumUpdatePending>[];
  final diskPathsByAlbum = await _scanAlbumsInBackground(
    albums.map((album) => album.folderPath).toList(),
  );

  for (final album in albums) {
    final diskPathsList = diskPathsByAlbum[album.folderPath];
    if (diskPathsList == null) {
      continue;
    }

    final diskPaths = diskPathsList.toSet();
    final dbPaths = db.getIndexedFilePaths(album.id).toSet();

    final newPaths = diskPaths.difference(dbPaths).toList();
    final deletedPaths = dbPaths.difference(diskPaths).toList();

    if (newPaths.isNotEmpty || deletedPaths.isNotEmpty) {
      results.add(
        AlbumUpdateInfo(
          albumId: album.id,
          albumPath: album.folderPath,
          newCount: newPaths.length,
          deletedCount: deletedPaths.length,
        ),
      );
      pending.add(
        _AlbumUpdatePending(
          albumId: album.id,
          albumPath: album.folderPath,
          newPhotos: newPaths,
          deletedPaths: deletedPaths,
        ),
      );
    }
  }

  _pendingUpdates = pending;
  return results;
}

/// Index pending updates stored by checkForUpdates().
/// Returns true if completed normally, false if cancelled.
Stream<IndexProgress> indexPendingUpdates() {
  return _runStream((controller, cancelled) async {
    final db = Db.instance;
    final now = _nowSeconds();

    final updates = _pendingUpdates;
    _pendingUpdates = null;

    if (updates == null) {
      throw Exception(
        'No pending updates available. Run check_for_updates() first.',
      );
    }

    // Step 1: Delete removed files for each album.
    final albumIdsToUpdate = <int>[];
    for (final update in updates) {
      if (update.deletedPaths.isNotEmpty) {
        final deleted = db.deleteImagesByPaths(
          update.albumId,
          update.deletedPaths,
        );
        _log.info('Deleted $deleted removed image records.');
      }
      albumIdsToUpdate.add(update.albumId);
    }

    // Step 2: Prepare pending images from new photos.
    final allPending = <_PendingImage>[];
    for (final update in updates) {
      // A single-album recovery may have indexed these paths since the update
      // check. Revalidate the snapshot before preparing or encoding any image.
      final indexedPaths = db.getIndexedFilePaths(update.albumId).toSet();
      for (final pathStr in update.newPhotos) {
        if (!indexedPaths.add(pathStr)) continue;
        final img = await _buildPendingImage(
          pathStr: pathStr,
          albumId: update.albumId,
          albumPath: update.albumPath,
        );
        if (img != null) {
          allPending.add(img);
        }
      }
    }

    // Step 3: Index all pending images.
    final result = await _indexPendingImages(
      controller,
      cancelled,
      allPending,
      alreadyIndexed: 0,
      totalImages: allPending.length,
    );

    // Step 4: Update all album records.
    for (final albumId in albumIdsToUpdate) {
      final indexed = db.getFolderImageCount(albumId);
      _updateAlbumRecord(db, albumId, now, indexed, result);
    }

    return result;
  });
}

/// Wrap an async indexing body in a broadcast-style StreamController with
/// cancellation tracking. Errors are delivered via the stream's error channel.
Stream<IndexProgress> _runStream(
  Future<bool> Function(
    StreamController<IndexProgress> controller,
    _CancelFlag cancelled,
  )
  body,
) {
  final cancelled = _CancelFlag();
  final finished = Completer<void>();
  late final StreamController<IndexProgress> controller;
  controller = StreamController<IndexProgress>(
    onCancel: () async {
      // Normal stream completion also invokes onCancel. Once the indexing body
      // has finished, there is no active work to cancel or wait for.
      if (finished.isCompleted) return;
      cancelled.value = true;
      if (!cancelled.failed) {
        _log.info('Index stream cancelled by listener.');
      }
      // Subscription cancellation is also the synchronization barrier used by
      // pause/delete. Do not let callers mutate the album until the indexing
      // body has finished its current native inference and DB checkpoint.
      await finished.future;
    },
  );

  Future(() async {
    try {
      await body(controller, cancelled);
    } catch (e, st) {
      cancelled.failed = true;
      if (!controller.isClosed) {
        controller.addError(e, st);
      }
    } finally {
      if (!finished.isCompleted) finished.complete();
      await controller.close();
    }
  });

  return controller.stream;
}
