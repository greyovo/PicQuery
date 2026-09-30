// Indexing service.
// Folder/album scanning, per-image encoding, batched DB insertion,
// IndexProgress streaming with cancel semantics matching the Rust version:
// when the listener cancels the subscription, the encode loop breaks and
// already-encoded-but-not-inserted results are NOT written; post-loop cleanup
// (deleted-record removal, folder record refresh) still runs, then the
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

/// Scans all indexed folders away from the UI isolate.
///
/// Update checks can walk a large directory tree. Keeping synchronous file
/// system traversal here prevents the startup check from delaying frames.
Future<Map<String, List<String>>> _scanFoldersInBackground(
  List<String> folderPaths,
) {
  return Isolate.run(() {
    final pathsByFolder = <String, List<String>>{};
    for (final folderPath in folderPaths) {
      if (Directory(folderPath).existsSync()) {
        pathsByFolder[folderPath] = _scanImages(folderPath);
      }
    }
    return pathsByFolder;
  });
}

/// Metadata for an image pending indexing.
class _PendingImage {
  final String pathStr;
  final String fileName;
  final int fileSize;
  final int modifiedTime;
  final String format;
  final int folderId;
  final String folderPath;
  const _PendingImage({
    required this.pathStr,
    required this.fileName,
    required this.fileSize,
    required this.modifiedTime,
    required this.format,
    required this.folderId,
    required this.folderPath,
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
  final int folderId;
  const _EncodedImage({
    required this.pathStr,
    required this.fileName,
    required this.fileSize,
    required this.modifiedTime,
    required this.width,
    required this.height,
    required this.format,
    required this.embedding,
    required this.folderId,
  });
}

/// Pending update info for a folder, stored after checkForUpdates().
class _FolderUpdatePending {
  final int folderId;
  final String folderPath;
  final List<String> newPhotos;
  final List<String> deletedPaths;
  const _FolderUpdatePending({
    required this.folderId,
    required this.folderPath,
    required this.newPhotos,
    required this.deletedPaths,
  });
}

/// Global storage for pending updates, populated by checkForUpdates() and
/// consumed by indexPendingUpdates().
List<_FolderUpdatePending>? _pendingUpdates;

/// Mutable cancellation flag shared between the stream controller's onCancel
/// callback and the indexing body.
class _CancelFlag {
  bool value = false;
}

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
  required int folderId,
  required String folderPath,
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
    folderId: folderId,
    folderPath: folderPath,
  );
}

/// Generic batch image indexing: encodes each pending image, sends progress,
/// and inserts encoded results in batches of [kBatchSize] per folder.
/// Returns true if completed normally, false if cancelled by the listener.
Future<bool> _indexPendingImages(
  StreamController<IndexProgress> controller,
  _CancelFlag cancelled,
  List<_PendingImage> pending, {
  required int alreadyIndexed,
  required int totalImages,
}
) async {
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
  final batch = <_EncodedImage>[];

  Future<bool> flushBatch() async {
    if (batch.isEmpty) return true;
    try {
      final first = batch.first;
      Db.instance.insertImagesAndVectorsBatch(
        first.folderId,
        batch.map((e) => ImageRowData(
          filePath: e.pathStr, fileName: e.fileName, fileSize: e.fileSize,
          modifiedTime: e.modifiedTime, width: e.width, height: e.height,
          format: e.format, indexedAt: now,
        )).toList(),
        batch.map((e) => e.embedding).toList(),
      );
      batch.clear();
      return true;
    } catch (_) {
      _log.severe('Failed to insert an image batch.');
      errorsCount += batch.length;
      batch.clear();
      return false;
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

      final embedding = await OrtEngine.instance.encodeImage(
        preprocessed.tensor,
      );
      encoded = (
        embedding: embedding,
        width: preprocessed.width,
        height: preprocessed.height,
      );
    } catch (_) {
      _log.severe('Failed to encode image.');
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
          currentFolder: img.folderPath,
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
        currentFolder: img.folderPath,
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
        folderId: img.folderId,
      ),
    );
    if (batch.length == kBatchSize) {
      await flushBatch();
    }
  }

  if (!cancelled.value) await flushBatch();
  return !cancelled.value && errorsCount == 0;
}

/// Update the folder record with the current image count.
void _updateFolderRecord(Db db, int folderId, int now, int total, bool complete) {
  final totalIndexed = db.getFolderImageCount(folderId);
  db.updateFolder(folderId, now, totalIndexed,
      totalImageCount: total, isIndexComplete: complete);
}

/// Index all supported images in a folder (recursively).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
Stream<IndexProgress> indexFolder({
  required String folderPath,
  required bool isUpdate,
}) {
  return _runStream((controller, cancelled) async {
    if (!Directory(folderPath).existsSync()) {
      throw Exception('Not a directory: $folderPath');
    }

    final db = Db.instance;
    final now = _nowSeconds();

    var folderId = 0;
    var existingPaths = <String>{};
    final existing = db.findFolderByPath(folderPath);
    if (existing != null) {
      folderId = existing.id;
      existingPaths = isUpdate
          ? db.getIndexedFilePaths(folderId).toSet()
          : <String>{};
    } else {
      folderId = db.insertFolder(folderPath, now);
    }

    final imagePaths = _scanImages(folderPath);
    final total = imagePaths.length;

    final pending = <_PendingImage>[];
    for (final pathStr in imagePaths) {
      if (isUpdate && existingPaths.contains(pathStr)) {
        continue;
      }
      final img = await _buildPendingImage(
        pathStr: pathStr,
        folderId: folderId,
        folderPath: folderPath,
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
    _updateFolderRecord(db, folderId, now, total, false);

    final result = await _indexPendingImages(
      controller, cancelled, toEncode,
      alreadyIndexed: alreadyIndexed, totalImages: total,
    );

    // Handle deleted files.
    if (isUpdate) {
      final toDelete = existingPaths
          .where((path) => !imagePaths.contains(path))
          .toList();
      if (toDelete.isNotEmpty) {
        final deleted = db.deleteImagesByPaths(folderId, toDelete);
        _log.info('Deleted $deleted removed image records.');
      }
    }

    if (alreadyIndexed > 0) {
      final totalIndexed = db.getFolderImageCount(folderId);
      _send(
        controller,
        cancelled,
        IndexProgress(
          current: totalIndexed,
          total: total,
          errors: 0,
          currentFolder: folderPath,
        ),
      );
    }

    _updateFolderRecord(db, folderId, now, total, result);

    return result;
  });
}

/// Index a list of image file paths directly (for mobile album selection).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
Stream<IndexProgress> indexImages({
  required String albumName,
  required List<String> imagePaths,
  required bool isUpdate,
}) {
  return _runStream((controller, cancelled) async {
    final db = Db.instance;
    final now = _nowSeconds();

    var folderId = 0;
    var existingPaths = <String>{};
    final existing = db.findFolderByPath(albumName);
    if (existing != null) {
      folderId = existing.id;
      existingPaths = isUpdate
          ? db.getIndexedFilePaths(folderId).toSet()
          : <String>{};
    } else {
      folderId = db.insertFolder(albumName, now);
    }

    final total = imagePaths.length;

    final pending = <_PendingImage>[];
    for (final pathStr in imagePaths) {
      if (isUpdate && existingPaths.contains(pathStr)) {
        continue;
      }
      final img = await _buildPendingImage(
        pathStr: pathStr,
        folderId: folderId,
        folderPath: albumName,
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
    _updateFolderRecord(db, folderId, now, total, false);

    final result = await _indexPendingImages(
      controller, cancelled, toEncode,
      alreadyIndexed: alreadyIndexed, totalImages: total,
    );

    // Handle deleted files.
    if (isUpdate) {
      final toDelete = existingPaths
          .where((path) => !imagePaths.contains(path))
          .toList();
      if (toDelete.isNotEmpty) {
        final deleted = db.deleteImagesByPaths(folderId, toDelete);
        _log.info('Deleted $deleted removed image records.');
      }
    }

    if (alreadyIndexed > 0) {
      final totalIndexed = db.getFolderImageCount(folderId);
      _send(
        controller,
        cancelled,
        IndexProgress(
          current: totalIndexed,
          total: total,
          errors: 0,
          currentFolder: albumName,
        ),
      );
    }

    _updateFolderRecord(db, folderId, now, total, result);

    return result;
  });
}

/// Check for index updates across all indexed folders.
/// Returns (folder_id, folder_path, new_count, deleted_count) for folders with
/// any changes, and stores the pending updates for indexPendingUpdates().
Future<List<FolderUpdateInfo>> checkForUpdates() async {
  final db = Db.instance;
  final folders = db.getAllFolders();
  final results = <FolderUpdateInfo>[];
  final pending = <_FolderUpdatePending>[];
  final diskPathsByFolder = await _scanFoldersInBackground(
    folders.map((folder) => folder.folderPath).toList(),
  );

  for (final folder in folders) {
    final diskPathsList = diskPathsByFolder[folder.folderPath];
    if (diskPathsList == null) {
      continue;
    }

    final diskPaths = diskPathsList.toSet();
    final dbPaths = db.getIndexedFilePaths(folder.id).toSet();

    final newPaths = diskPaths.difference(dbPaths).toList();
    final deletedPaths = dbPaths.difference(diskPaths).toList();

    if (newPaths.isNotEmpty || deletedPaths.isNotEmpty) {
      results.add(
        FolderUpdateInfo(
          folderId: folder.id,
          folderPath: folder.folderPath,
          newCount: newPaths.length,
          deletedCount: deletedPaths.length,
        ),
      );
      pending.add(
        _FolderUpdatePending(
          folderId: folder.id,
          folderPath: folder.folderPath,
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

    // Step 1: Delete removed files for each folder.
    final folderIdsToUpdate = <int>[];
    for (final update in updates) {
      if (update.deletedPaths.isNotEmpty) {
        final deleted = db.deleteImagesByPaths(
          update.folderId,
          update.deletedPaths,
        );
        _log.info('Deleted $deleted removed image records.');
      }
      folderIdsToUpdate.add(update.folderId);
    }

    // Step 2: Prepare pending images from new photos.
    final allPending = <_PendingImage>[];
    for (final update in updates) {
      for (final pathStr in update.newPhotos) {
        final img = await _buildPendingImage(
          pathStr: pathStr,
          folderId: update.folderId,
          folderPath: update.folderPath,
        );
        if (img != null) {
          allPending.add(img);
        }
      }
    }

    // Step 3: Index all pending images.
    final result = await _indexPendingImages(
      controller, cancelled, allPending,
      alreadyIndexed: 0, totalImages: allPending.length,
    );

    // Step 4: Update all folder records.
    for (final folderId in folderIdsToUpdate) {
      final indexed = db.getFolderImageCount(folderId);
      _updateFolderRecord(db, folderId, now, indexed, result);
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
  late final StreamController<IndexProgress> controller;
  controller = StreamController<IndexProgress>(
    onCancel: () {
      cancelled.value = true;
      _log.info('Index stream cancelled by listener.');
    },
  );

  Future(() async {
    try {
      await body(controller, cancelled);
    } catch (e, st) {
      if (!controller.isClosed) {
        controller.addError(e, st);
      }
    } finally {
      await controller.close();
    }
  });

  return controller.stream;
}
