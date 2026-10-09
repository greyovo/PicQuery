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
    final album = albumManager.albums.value
        .singleWhere((album) => album.albumPath == recovered.path);
    final done = Completer<void>();
    manager.startIndexing(
      album.albumPath,
      'Recovered',
      alreadyIndexed: album.imageCount,
      isUpdate: true,
      onDone: () => done.complete(),
    );
    await done.future.timeout(const Duration(seconds: 5));

    expect(Db.instance.findFolderByPath(recovered.path)!.isIndexComplete, isTrue);
    expect(manager.errorForAlbum(recovered.path), isNull);
    expect(manager.isIndexing, isFalse);
    expect(manager.updateAvailableAlbumPaths, {other.path});
    expect(manager.pendingUpdateCountsByAlbum.containsKey(recovered.path), isFalse);
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

  test('normal completion does not log cancellation; explicit cancel does',
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
  });
}
