import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/db.dart';
import 'package:picquery_app/src/engine/indexer.dart' as indexer;
import 'package:picquery_app/src/managers/album_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:watch_it/watch_it.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory temporary;
  late IndexingManager manager;

  setUp(() async {
    temporary = Directory.systemTemp.createTempSync('picquery-recovery-');
    Hive.init('${temporary.path}/settings');
    await Hive.openBox('settings');
    await Db.init('${temporary.path}/index.db');
    di.registerSingleton<AlbumManager>(AlbumManager());
    await albumManager.reload();
    manager = IndexingManager();
  });

  tearDown(() async {
    manager.dispose();
    await di.reset();
    Db.instance.close();
    await Hive.close();
    temporary.deleteSync(recursive: true);
  });

  void insertIndexedImage(int albumId, File file) {
    final stat = file.statSync();
    final vector = Float32List(kEmbeddingDim)..[0] = 1;
    Db.instance.insertImagesAndVectorsBatch(
      albumId,
      [
        ImageRowData(
          filePath: file.path,
          fileName: file.uri.pathSegments.last,
          fileSize: stat.size,
          modifiedTime: stat.modified.millisecondsSinceEpoch ~/ 1000,
          width: 1,
          height: 1,
          format: 'jpeg',
          indexedAt: 1,
        ),
      ],
      [vector],
    );
  }

  test('mobile albums reuse shared photos during indexing and updates', () async {
    final photo = File('${temporary.path}/shared.jpg')..writeAsBytesSync([0]);
    final camera = Db.instance.insertFolder('camera', 1);
    insertIndexedImage(camera, photo);
    // The invalid image bytes and absent ONNX sessions ensure a reused photo
    // never reaches preprocessing or inference.
    for (final isUpdate in [false, true]) {
      final progress = await indexer
          .indexImages(
            albumName: 'recent',
            imagePaths: [photo.path, photo.path],
            isUpdate: isUpdate,
          )
          .toList();
      expect(progress.every((p) => p.errors == 0), isTrue);
      final recent = Db.instance.findFolderByPath('recent')!;
      expect(recent.imageCount, 1);
      expect(Db.instance.getIndexedFilePaths(recent.id), [photo.path]);
      expect(Db.instance.getTotalImageCount(), 1);
      expect(Db.instance.getTotalVectorCount(), 1);
      if (!isUpdate) {
        // Simulate the photo leaving the virtual album before its next update.
        Db.instance.deleteImagesByPaths(recent.id, [photo.path]);
      }
    }
    await indexer
        .indexImages(albumName: 'recent', imagePaths: [], isUpdate: true)
        .drain<void>();
    expect(Db.instance.getIndexedFilePaths(camera), [photo.path]);
    expect(Db.instance.findFolderByPath('recent')!.imageCount, 0);
    expect(Db.instance.getTotalVectorCount(), 1);
    Db.instance.deleteFolder(Db.instance.findFolderByPath('recent')!.id);
    expect(Db.instance.getTotalImageCount(), 1);
  });

  test('recovery refreshes stale updates and preserves other albums', () async {
    final recovered = Directory('${temporary.path}/recovered');
    final failed = Completer<void>();
    manager.startIndexing(
      recovered.path,
      'Recovered',
      onError: (_) => failed.complete(),
    );
    await failed.future.timeout(const Duration(seconds: 5));
    expect(manager.errorForAlbum(recovered.path), isNotNull);

    recovered.createSync();
    final photo = File('${recovered.path}/photo.jpg')..writeAsBytesSync([0]);
    final albumId = Db.instance.insertFolder(recovered.path, 1);
    Db.instance.updateFolder(
      albumId,
      1,
      0,
      totalImageCount: 1,
      isIndexComplete: false,
    );

    // A second album still has a deleted file to synchronize.
    final other = Directory('${temporary.path}/other')..createSync();
    final deleted = File('${other.path}/deleted.jpg')..writeAsBytesSync([0]);
    final otherId = Db.instance.insertFolder(other.path, 1);
    insertIndexedImage(otherId, deleted);
    Db.instance.updateFolder(
      otherId,
      1,
      1,
      totalImageCount: 1,
      isIndexComplete: true,
    );
    deleted.deleteSync();

    await manager.checkForUpdates(showToast: false);
    expect(manager.updateAvailableAlbumPaths, {recovered.path, other.path});

    // Simulate the persisted checkpoint from an earlier indexing run. Recovery
    // can now finish without encoding, while the old update snapshot is stale.
    insertIndexedImage(albumId, photo);
    await albumManager.reload();
    final album = albumManager.albums.value.singleWhere(
      (album) => album.albumPath == recovered.path,
    );
    final done = Completer<void>();
    manager.startIndexing(
      album.albumPath,
      'Recovered',
      alreadyIndexed: album.imageCount,
      isUpdate: true,
      onDone: () => done.complete(),
    );
    await done.future.timeout(const Duration(seconds: 5));

    expect(
      Db.instance.findFolderByPath(recovered.path)!.isIndexComplete,
      isTrue,
    );
    expect(manager.errorForAlbum(recovered.path), isNull);
    expect(manager.isIndexing, isFalse);
    expect(manager.updateAvailableAlbumPaths, {other.path});
    expect(
      manager.pendingUpdateCountsByAlbum.containsKey(recovered.path),
      isFalse,
    );
    expect(manager.albumUpdateStatus, AlbumUpdateStatus.updateAvailable);

    // The global action must consume the refreshed snapshot, rather than trying
    // to reinsert the recovered photo from the original snapshot.
    final updated = Completer<void>();
    manager.startUpdateIndexing(onDone: () => updated.complete());
    await updated.future.timeout(const Duration(seconds: 5));
    expect(manager.lastIndexingError, isNull);
    expect(manager.updateAvailableAlbumPaths, isEmpty);
    expect(manager.pendingUpdateCountsByAlbum, isEmpty);
    expect(manager.pendingUpdateCount, 0);
    expect(manager.albumUpdateStatus, AlbumUpdateStatus.upToDate);
    expect(Db.instance.getFolderImageCount(albumId), 1);
    expect(Db.instance.getFolderImageCount(otherId), 0);
  });

  test(
    'mixed-album writes do not create phantom additions or deletions',
    () async {
      final albums = [
        Directory('${temporary.path}/a')..createSync(),
        Directory('${temporary.path}/b')..createSync(),
      ];
      final photos = [
        File('${albums[0].path}/a.jpg')..writeAsBytesSync([0]),
        File('${albums[1].path}/b.jpg')..writeAsBytesSync([0]),
      ];
      final ids = albums
          .map((album) => Db.instance.insertFolder(album.path, 1))
          .toList();
      final rows = photos.map((photo) {
        final stat = photo.statSync();
        return ImageRowData(
          filePath: photo.path,
          fileName: photo.uri.pathSegments.last,
          fileSize: stat.size,
          modifiedTime: stat.modified.millisecondsSinceEpoch ~/ 1000,
          width: 1,
          height: 1,
          format: 'jpeg',
          indexedAt: 1,
        );
      }).toList();
      Db.instance.insertImagesAndVectorsForFoldersBatch(ids, rows, [
        Float32List(kEmbeddingDim)..[0] = 1,
        Float32List(kEmbeddingDim)..[1] = 1,
      ]);
      for (final id in ids) {
        Db.instance.updateFolder(
          id,
          1,
          1,
          totalImageCount: 1,
          isIndexComplete: true,
        );
      }

      expect(await indexer.checkForUpdates(), isEmpty);
      await albumManager.reload();
      await manager.checkForUpdates(showToast: false);
      expect(manager.updateAvailableAlbumPaths, isEmpty);
      expect(manager.albumUpdateStatus, AlbumUpdateStatus.upToDate);
      expect(albumManager.albums.value.map((album) => album.imageCount), [
        1,
        1,
      ]);
    },
  );

  test(
    'checking hides old updates and an unchanged album stays up to date',
    () async {
      final album = Directory('${temporary.path}/latest')..createSync();
      final photo = File('${album.path}/photo.jpg')..writeAsBytesSync([0]);
      final albumId = Db.instance.insertFolder(album.path, 1);
      insertIndexedImage(albumId, photo);
      Db.instance.updateFolder(
        albumId,
        1,
        1,
        totalImageCount: 1,
        isIndexComplete: true,
      );
      await albumManager.reload();
      manager.updateAvailableAlbumPaths = {album.path};
      manager.pendingUpdateCountsByAlbum = {album.path: 1};
      manager.pendingUpdateCount = 1;
      manager.albumUpdateStatus = AlbumUpdateStatus.updateAvailable;

      final check = manager.checkForUpdates(showToast: false);
      expect(manager.albumUpdateStatus, AlbumUpdateStatus.checking);
      expect(manager.updateAvailableAlbumPaths, isEmpty);
      expect(manager.pendingUpdateCountsByAlbum, isEmpty);
      expect(manager.pendingUpdateCount, 0);
      await check;
      expect(manager.albumUpdateStatus, AlbumUpdateStatus.upToDate);
      expect(manager.updateAvailableAlbumPaths, isEmpty);

      // Reload is a database refresh; it must not resurrect the cleared hint.
      await albumManager.reload();
      await manager.checkForUpdates(showToast: false);
      expect(manager.updateAvailableAlbumPaths, isEmpty);
      expect(manager.pendingUpdateCount, 0);
    },
  );

  test('incremental indexing skips paths indexed after the update check', () async {
    final album = Directory('${temporary.path}/stale')..createSync();
    final photo = File('${album.path}/photo.jpg')..writeAsBytesSync([0]);
    final albumId = Db.instance.insertFolder(album.path, 1);
    Db.instance.updateFolder(
      albumId,
      1,
      0,
      totalImageCount: 1,
      isIndexComplete: false,
    );
    final updates = await indexer.checkForUpdates();
    expect(updates.single.newCount, 1);

    // Recovery writes the image after the snapshot was created. Its contents
    // are deliberately invalid: trying to encode it again would report errors.
    insertIndexedImage(albumId, photo);
    final events = await indexer.indexPendingUpdates().toList();
    expect(events.every((event) => event.errors == 0), isTrue);
    expect(Db.instance.getFolderImageCount(albumId), 1);
    expect(Db.instance.findFolderByPath(album.path)!.isIndexComplete, isTrue);
    expect(await indexer.checkForUpdates(), isEmpty);
  });

  test(
    'normal completion does not log cancellation; explicit cancel does',
    () async {
      final album = Directory('${temporary.path}/empty')..createSync();
      final cancellationLogs = <LogRecord>[];
      final logs = Logger.root.onRecord.listen((record) {
        if (record.loggerName == 'engine.indexer' &&
            record.message == 'Index stream cancelled by listener.') {
          cancellationLogs.add(record);
        }
      });
      addTearDown(logs.cancel);

      await indexer.indexAlbum(albumPath: album.path, isUpdate: false).toList();
      expect(cancellationLogs, isEmpty);

      final subscription = indexer
          .indexAlbum(albumPath: album.path, isUpdate: true)
          .listen((_) {});
      await subscription.cancel();
      expect(cancellationLogs, hasLength(1));
    },
  );
}
