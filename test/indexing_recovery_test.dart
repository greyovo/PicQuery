import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:picquery_app/l10n/generated/app_localizations.dart';
import 'package:picquery_app/src/pages/album_manage_page.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:image/image.dart' as img;
import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/db.dart';
import 'package:picquery_app/src/engine/api.dart' show IndexProgress;
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

  test(
    'each unreadable file is skipped once and completion is recoverable',
    () async {
      final missing = '${temporary.path}/missing.png';
      final corrupt = File('${temporary.path}/corrupt.png')
        ..writeAsBytesSync([0]);
      final unsupported = File('${temporary.path}/unsupported.heic')
        ..writeAsBytesSync([1]);
      final done = Completer<void>();
      manager.startIndexing(
        'mobile-album',
        'Photos',
        imagePaths: [missing, corrupt.path, unsupported.path],
        onDone: done.complete,
        onError: done.completeError,
      );
      await done.future.timeout(const Duration(seconds: 5));
      expect(manager.lastIndexingError, isNull);
      expect(manager.errorForAlbum('mobile-album'), isNull);
      expect(
        manager.failuresByAlbum['mobile-album']!.map((image) => image.filePath),
        [missing, corrupt.path, unsupported.path],
      );
      expect(manager.current, 3);
      expect(manager.total, 3);
      expect(Db.instance.findFolderByPath('mobile-album')!.imageCount, 0);
      expect(
        Db.instance.findFolderByPath('mobile-album')!.isIndexComplete,
        isFalse,
      );

      final retry = Completer<void>();
      manager.startIndexing(
        'mobile-album',
        'Photos',
        imagePaths: [],
        isUpdate: true,
        onDone: retry.complete,
        onError: retry.completeError,
      );
      await retry.future.timeout(const Duration(seconds: 5));
      expect(manager.failuresByAlbum, isEmpty);
    },
  );

  test('a corrupt image does not prevent the following image reaching inference', () async {
    final corrupt = File('${temporary.path}/corrupt.png')
      ..writeAsBytesSync([0]);
    final valid = File('${temporary.path}/valid.png')
      ..writeAsBytesSync(img.encodePng(img.Image(width: 32, height: 32)));
    final events = <IndexProgress>[];
    // No model is loaded: reaching inference on the valid file must propagate
    // the session error, while only the corrupt file is a recoverable failure.
    await expectLater(
      indexer
          .indexImages(
            albumName: 'photos',
            imagePaths: [corrupt.path, valid.path],
            isUpdate: false,
          )
          .map((event) {
            events.add(event);
            return event;
          })
          .drain<void>(),
      throwsStateError,
    );
    expect(
      events
          .where((event) => event.failure != null)
          .map((event) => event.failure!.filePath),
      [corrupt.path],
    );
  });

  test(
    'incremental failures belong only to their album and retain total counts',
    () async {
      final badAlbum = Directory('${temporary.path}/bad')..createSync();
      final goodAlbum = Directory('${temporary.path}/good')..createSync();
      final badId = Db.instance.insertFolder(badAlbum.path, 1);
      final goodId = Db.instance.insertFolder(goodAlbum.path, 1);
      final corrupt = File('${badAlbum.path}/broken.heic')
        ..writeAsBytesSync([0]);
      final removed = File('${goodAlbum.path}/removed.jpg')
        ..writeAsBytesSync([0]);
      insertIndexedImage(goodId, removed);
      removed.deleteSync();
      await manager.checkForUpdates(showToast: false);
      final done = Completer<void>();
      manager.startUpdateIndexing(
        onDone: done.complete,
        onError: done.completeError,
      );
      await done.future.timeout(const Duration(seconds: 5));
      expect(manager.failuresByAlbum.keys, [badAlbum.path]);
      expect(
        manager.failuresByAlbum[badAlbum.path]!.single.filePath,
        corrupt.path,
      );
      expect(manager.lastIndexingError, isNull);
      expect(Db.instance.findFolderByPath(badAlbum.path)!.totalImageCount, 1);
      expect(
        Db.instance.findFolderByPath(badAlbum.path)!.isIndexComplete,
        isFalse,
      );
      expect(
        Db.instance.findFolderByPath(goodAlbum.path)!.isIndexComplete,
        isTrue,
      );
      expect(Db.instance.getFolderImageCount(badId), 0);
    },
  );

  testWidgets('album warning dialog shows counts and the first three names', (
    tester,
  ) async {
    di.registerSingleton<IndexingManager>(manager);
    await tester.runAsync(() async {
      final paths = List.generate(
        4,
        (index) => '${temporary.path}/photo$index.png',
      );
      final done = Completer<void>();
      manager.startIndexing(
        'mobile-album',
        'My Photos',
        imagePaths: paths,
        onDone: done.complete,
        onError: done.completeError,
      );
      await done.future.timeout(const Duration(seconds: 5));
    });
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const AlbumManagePage(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Some images were skipped'), findsOneWidget);
    await tester.tap(find.byTooltip('Some images were skipped'));
    await tester.pumpAndSettle();
    expect(find.text('My Photos: 4 images failed'), findsOneWidget);
    expect(
      find.text('photo0.png, photo1.png, photo2.png, etc.'),
      findsOneWidget,
    );
    expect(find.textContaining('photo3.png'), findsNothing);
    expect(
      find.text('Indexing failed. You can report error logs below.'),
      findsNothing,
    );
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    di.unregister<IndexingManager>();
  });

  test(
    'skip logs identify header formats independently of extensions',
    () async {
      final mislabeled = File('${temporary.path}/mislabeled.jpg')
        ..writeAsBytesSync([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
      final heic = File('${temporary.path}/unsupported.heic')
        ..writeAsBytesSync([
          0,
          0,
          0,
          20,
          ...'ftypmif1'.codeUnits,
          0,
          0,
          0,
          0,
          ...'heic'.codeUnits,
        ]);
      final missing = '${temporary.path}/missing.png';
      final logs = <LogRecord>[];
      final subscription = Logger.root.onRecord.listen((record) {
        if (record.loggerName == 'engine.indexer' &&
            record.message.startsWith('Skipping image:')) {
          logs.add(record);
        }
      });
      addTearDown(subscription.cancel);

      await indexer
          .indexImages(
            albumName: 'diagnostic-album',
            imagePaths: [mislabeled.path, heic.path, missing],
            isUpdate: false,
          )
          .drain<void>();

      expect(logs, hasLength(3));
      expect(
        logs[0].message,
        contains('extensionFormat=jpeg detectedFormat=png'),
      );
      expect(logs[0].message, contains('stage=preprocess'));
      expect(logs[0].message, contains('fileSize=8'));
      expect(
        logs[1].message,
        contains('extensionFormat=heic detectedFormat=heic'),
      );
      expect(logs[2].message, contains('stage=metadata'));
      expect(logs[2].message, contains('detectedFormat=unreadable'));
      for (final record in logs) {
        expect(record.message, contains('album=diagnostic-album'));
        expect(record.message, contains('errorType='));
        expect(record.error, isNotNull);
        expect(record.stackTrace, isNotNull);
      }
    },
  );

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
