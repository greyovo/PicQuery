// Tests for lib/src/engine/db.dart (sqlite3 + sqlite_vector).
// Uses an in-memory database. Also verifies the measured sqlite_vector
// distance metric (plain L2, so cos = 1 - d²/2 for L2-normalized vectors).

import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/engine/db.dart';
import 'package:sqlite3/sqlite3.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Db db;

  setUp(() {
    db = Db.openInMemoryForTest();
  });

  tearDown(() {
    db.close();
  });

  Float32List unitVector(int dim, int activeIndex, {double sign = 1.0}) {
    final v = Float32List(dim);
    v[activeIndex] = sign;
    return v;
  }

  ImageRowData image(String path, {int mtime = 100}) => ImageRowData(
    filePath: path,
    fileName: path.split('/').last,
    fileSize: 123,
    modifiedTime: mtime,
    width: 10,
    height: 10,
    format: 'jpeg',
    indexedAt: 1000,
  );

  group('schema init', () {
    test('resolves mobile album names from indexed image membership', () {
      final albumId = db.insertFolder('mobile-album-id', 1, displayName: '旅行');
      db.insertImagesAndVectorsBatch(
        albumId,
        [image('/cache/photo.jpg')],
        [unitVector(kEmbeddingDim, 0)],
      );
      expect(db.getImageAlbum('/cache/photo.jpg'), ('mobile-album-id', '旅行'));
      expect(db.getImageAlbum('/cache/missing.jpg'), isNull);
    });

    test('migrates legacy album ownership without losing IDs or vectors', () async {
      final directory = Directory.systemTemp.createTempSync();
      addTearDown(() => directory.deleteSync(recursive: true));
      final path = '${directory.path}/legacy.db';
      final legacy = sqlite3.open(path);
      legacy.execute(
        '''CREATE TABLE folders (
        id INTEGER PRIMARY KEY AUTOINCREMENT, folder_path TEXT NOT NULL UNIQUE,
        indexed_at INTEGER NOT NULL, image_count INTEGER NOT NULL DEFAULT 0)''',
      );
      legacy.execute('''CREATE TABLE images (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
        file_path TEXT NOT NULL UNIQUE, file_name TEXT NOT NULL,
        file_size INTEGER NOT NULL, modified_time INTEGER NOT NULL,
        width INTEGER NOT NULL, height INTEGER NOT NULL, format TEXT NOT NULL,
        indexed_at INTEGER NOT NULL, ocr_text TEXT)''');
      legacy.execute(
        'CREATE INDEX idx_images_modified ON images(file_path, modified_time)',
      );
      legacy.execute('CREATE INDEX idx_images_folder ON images(folder_id)');
      legacy.execute(
        'CREATE TABLE vector_images (rowid INTEGER PRIMARY KEY, embedding BLOB NOT NULL)',
      );
      legacy.execute("INSERT INTO folders VALUES (7, 'camera', 1, 1)");
      legacy.execute(
        "INSERT INTO images VALUES (42, 7, '/shared.jpg', 'shared.jpg', 123, 100, 10, 10, 'jpeg', 1000, 'saved OCR')",
      );
      legacy.execute('INSERT INTO vector_images VALUES (?, ?)', [
        42,
        vectorToBlob(unitVector(kEmbeddingDim, 0)),
      ]);
      legacy.close();
      await Db.init(path);
      addTearDown(() => Db.instance.close());
      final migrated = Db.instance;
      expect(migrated.getIndexedFilePaths(7), ['/shared.jpg']);
      expect(migrated.getFolderImageCount(7), 1);
      expect(
        migrated.knnSearch(unitVector(kEmbeddingDim, 0), 1).single.rowid,
        42,
      );
      final recent = migrated.insertFolder('recent', 2);
      expect(migrated.linkIndexedImage(recent, '/shared.jpg'), isTrue);
      migrated.deleteFolder(7);
      expect(migrated.getImageById(42)!.$1, '/shared.jpg');
      migrated.close();
      await Db.init(path);
      expect(Db.instance.getIndexedFilePaths(recent), ['/shared.jpg']);
      expect(
        Db.instance.knnSearch(unitVector(kEmbeddingDim, 0), 1).single.rowid,
        42,
      );
      Db.instance.deleteFolder(recent);
      expect(Db.instance.getTotalVectorCount(), 0);
    });

    test('is idempotent across reopen (file db)', () async {
      final tmpDir = Directory.systemTemp.createTempSync();
      addTearDown(() => tmpDir.deleteSync(recursive: true));
      final path = '${tmpDir.path}/picquery_v2.db';

      await Db.init(path);
      Db.instance.insertFolder('/a', 1);
      final folderId = Db.instance.getAllFolders().single.id;
      Db.instance.insertImagesAndVectorsBatch(
        folderId,
        [image('/a/1.jpg')],
        [unitVector(kEmbeddingDim, 0)],
      );
      Db.instance.close();

      // Re-init on the same path: data must survive and vector_init must
      // rebuild its connection-local context so searches still work.
      await Db.init(path);
      expect(Db.instance.getAllFolders().length, 1);
      final hits = Db.instance.knnSearch(unitVector(kEmbeddingDim, 0), 1);
      expect(hits, hasLength(1));
      expect(Db.instance.getImageById(hits.single.rowid)!.$1, '/a/1.jpg');
      Db.instance.close();

      // Re-init a second time (double idempotency).
      await Db.init(path);
      expect(Db.instance.getAllFolders().length, 1);
    });
  });

  group('folders', () {
    test('insert / find / update / get_all', () {
      final idA = db.insertFolder('/a', 100, displayName: '相机');
      final idB = db.insertFolder('/b', 200);

      expect(
        () => db.insertFolder('/a', 300),
        throwsA(anything),
      ); // UNIQUE path

      final found = db.findFolderByPath('/a');
      expect(found, isNotNull);
      expect(found!.id, idA);
      expect(found.displayName, '相机');
      expect(found.imageCount, 0);
      expect(db.findFolderByPath('/missing'), isNull);

      db.updateFolder(idA, 500, 7, totalImageCount: 7, isIndexComplete: true);
      expect(db.findFolderByPath('/a')!.indexedAt, 500);
      expect(db.findFolderByPath('/a')!.imageCount, 7);
      db.updateFolderDisplayName(idA, 'Camera');
      expect(db.findFolderByPath('/a')!.displayName, 'Camera');

      // Ordered by indexed_at desc.
      final all = db.getAllFolders();
      expect(all.map((f) => f.id).toList(), [idA, idB]);
    });

    test('delete_folder cascades images and vectors', () {
      final idA = db.insertFolder('/a', 100);
      final idB = db.insertFolder('/b', 200);

      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/1.jpg'), image('/a/2.jpg')],
        [unitVector(kEmbeddingDim, 0), unitVector(kEmbeddingDim, 1)],
      );
      db.insertImagesAndVectorsBatch(
        idB,
        [image('/b/1.jpg')],
        [unitVector(kEmbeddingDim, 2)],
      );

      expect(db.findFolderByPath('/a')!.coverPath, '/a/2.jpg');
      expect(db.findFolderByPath('/b')!.coverPath, '/b/1.jpg');

      db.deleteFolder(idA);

      expect(db.findFolderByPath('/a'), isNull);
      expect(db.getIndexedFilePaths(idA), isEmpty);
      // Folder B untouched.
      expect(db.getIndexedFilePaths(idB).length, 1);
      // Vectors of folder A gone: only 1 vector row remains.
      expect(db.getTotalImageCount(), 1);
      final knn = db.knnSearch(unitVector(kEmbeddingDim, 0), 10);
      expect(knn.map((h) => h.rowid).toSet().intersection({1, 2}), isEmpty);
    });

    test('delete_all_folders clears everything', () {
      final idA = db.insertFolder('/a', 100);
      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/1.jpg')],
        [unitVector(kEmbeddingDim, 0)],
      );
      db.deleteAllFolders();
      expect(db.getAllFolders(), isEmpty);
      expect(db.getTotalImageCount(), 0);
      expect(db.knnSearch(unitVector(kEmbeddingDim, 0), 10), isEmpty);
    });
  });

  group('images & vectors', () {
    test('mixed-album batch preserves ownership and vector alignment', () {
      final idA = db.insertFolder('/a', 100);
      final idB = db.insertFolder('/b', 100);
      final inserted = db.insertImagesAndVectorsForFoldersBatch(
        [idA, idB, idA],
        [image('/a/1.jpg'), image('/b/1.jpg'), image('/a/2.jpg')],
        [
          unitVector(kEmbeddingDim, 0),
          unitVector(kEmbeddingDim, 1),
          unitVector(kEmbeddingDim, 2),
        ],
      );

      expect(inserted, 3);
      expect(db.getIndexedFilePaths(idA).toSet(), {'/a/1.jpg', '/a/2.jpg'});
      expect(db.getIndexedFilePaths(idB), ['/b/1.jpg']);
      final hits = db.knnSearchFiltered(unitVector(kEmbeddingDim, 1), 1, [idB]);
      expect(hits, hasLength(1));
      expect(db.getImageById(hits.single.rowid)!.$1, '/b/1.jpg');
      expect(hits.single.distance, closeTo(0, 1e-5));
    });

    test('mixed-album batch rolls back on invalid album membership', () {
      final idA = db.insertFolder('/a', 100);
      final idB = db.insertFolder('/b', 100);
      expect(
        () => db.insertImagesAndVectorsForFoldersBatch(
          [idA, idB, -1],
          [image('/a/1.jpg'), image('/b/1.jpg'), image('/a/1.jpg')],
          [
            unitVector(kEmbeddingDim, 0),
            unitVector(kEmbeddingDim, 1),
            unitVector(kEmbeddingDim, 2),
          ],
        ),
        throwsA(anything),
      );
      expect(db.getIndexedFilePaths(idA), isEmpty);
      expect(db.getIndexedFilePaths(idB), isEmpty);
      expect(db.knnSearch(unitVector(kEmbeddingDim, 0), 10), isEmpty);
    });

    test('duplicate paths reuse the original vector within a batch', () {
      final album = db.insertFolder('/a', 100);
      final inserted = db.insertImagesAndVectorsBatch(
        album,
        [image('/a/1.jpg'), image('/a/2.jpg'), image('/a/1.jpg')],
        [
          unitVector(kEmbeddingDim, 0),
          unitVector(kEmbeddingDim, 1),
          unitVector(kEmbeddingDim, 2),
        ],
      );
      expect(inserted, 2);
      expect(db.getFolderImageCount(album), 2);
      expect(db.getTotalImageCount(), 2);
      expect(db.getTotalVectorCount(), 2);
      final hit = db.knnSearch(unitVector(kEmbeddingDim, 0), 1).single;
      expect(db.getImageById(hit.rowid)!.$1, '/a/1.jpg');
      expect(hit.distance, closeTo(0, 1e-5));
    });

    for (final recentFirst in [false, true]) {
      test('shared photo survives album deletion (recentFirst=$recentFirst)', () {
        final original = db.insertFolder('camera', 1);
        final recent = db.insertFolder('recent', 2);
        final first = recentFirst ? recent : original;
        final second = recentFirst ? original : recent;
        const path = '/cache/shared.jpg';
        db.insertImagesAndVectorsBatch(
          first,
          [image(path)],
          [unitVector(kEmbeddingDim, 0)],
        );
        expect(db.linkIndexedImage(second, path), isTrue);
        expect(db.linkIndexedImage(second, path), isTrue);
        // A later batch with the same path must also reuse the original vector.
        expect(
          db.insertImagesAndVectorsBatch(
            second,
            [image(path)],
            [unitVector(kEmbeddingDim, 1)],
          ),
          0,
        );
        expect(db.getTotalImageCount(), 1);
        expect(db.getTotalVectorCount(), 1);
        for (final album in [original, recent]) {
          expect(db.getIndexedFilePaths(album), [path]);
          expect(db.getFolderImageCount(album), 1);
          expect(
            db.getAllFolders().firstWhere((f) => f.id == album).coverPath,
            path,
          );
          final hit = db.knnSearchFiltered(unitVector(kEmbeddingDim, 0), 10, [
            album,
          ]).single;
          expect(hit.distance, closeTo(0, 1e-5));
        }
        expect(
          db.knnSearchFiltered(unitVector(kEmbeddingDim, 0), 10, [
            original,
            recent,
          ]),
          hasLength(1),
        );
        db.deleteFolder(recent);
        expect(db.getIndexedFilePaths(original), [path]);
        expect(db.getTotalImageCount(), 1);
        expect(db.getTotalVectorCount(), 1);
        expect(
          db.knnSearchFiltered(unitVector(kEmbeddingDim, 0), 10, [original]),
          hasLength(1),
        );
        db.deleteFolder(original);
        expect(db.getTotalImageCount(), 0);
        expect(db.getTotalVectorCount(), 0);
      });
    }

    test('album update removes membership without deleting shared photo', () {
      final original = db.insertFolder('camera', 1);
      final recent = db.insertFolder('recent', 2);
      const path = '/cache/shared.jpg';
      db.insertImagesAndVectorsForFoldersBatch(
        [original, recent],
        [image(path), image(path)],
        [unitVector(kEmbeddingDim, 0), unitVector(kEmbeddingDim, 1)],
      );
      expect(db.deleteImagesByPaths(recent, [path]), 1);
      expect(db.getIndexedFilePaths(recent), isEmpty);
      expect(db.getIndexedFilePaths(original), [path]);
      expect(db.getTotalVectorCount(), 1);
      expect(db.deleteImagesByPaths(recent, [path]), 0);
      expect(db.deleteImagesByPaths(original, [path]), 1);
      expect(db.getTotalImageCount(), 0);
      expect(db.getTotalVectorCount(), 0);
    });

    test('isImageIndexed matches on path AND mtime', () {
      final idA = db.insertFolder('/a', 100);
      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/1.jpg', mtime: 42)],
        [unitVector(kEmbeddingDim, 0)],
      );

      expect(db.isImageIndexed('/a/1.jpg', 42), isTrue);
      expect(db.isImageIndexed('/a/1.jpg', 43), isFalse);
      expect(db.isImageIndexed('/a/other.jpg', 42), isFalse);
    });

    test('deleteImagesByPaths removes records and vectors', () {
      final idA = db.insertFolder('/a', 100);
      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/1.jpg'), image('/a/2.jpg'), image('/a/3.jpg')],
        [
          unitVector(kEmbeddingDim, 0),
          unitVector(kEmbeddingDim, 1),
          unitVector(kEmbeddingDim, 2),
        ],
      );

      final deleted = db.deleteImagesByPaths(idA, ['/a/1.jpg', '/a/3.jpg']);
      expect(deleted, 2);
      expect(db.getIndexedFilePaths(idA), ['/a/2.jpg']);
      // Only the vector of the surviving image remains.
      final knn = db.knnSearch(unitVector(kEmbeddingDim, 1), 10);
      expect(knn.length, 1);
      expect(db.getImageById(knn.first.rowid)!.$1, '/a/2.jpg');
    });

    test('count queries', () {
      final idA = db.insertFolder('/a', 100);
      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/1.jpg'), image('/a/2.jpg')],
        [unitVector(kEmbeddingDim, 0), unitVector(kEmbeddingDim, 1)],
      );
      expect(db.getTotalImageCount(), 2);
      expect(db.getFolderImageCount(idA), 2);
      expect(db.getLastIndexedPath(), '/a');
    });
  });

  group('KNN', () {
    test('distance metric is plain L2; similarity maps via cos = 1 - d²/2', () {
      final idA = db.insertFolder('/a', 100);
      // rowids 1..3 = identical / orthogonal / opposite vectors.
      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/same.jpg'), image('/a/ortho.jpg'), image('/a/oppo.jpg')],
        [
          unitVector(kEmbeddingDim, 0),
          unitVector(kEmbeddingDim, 1),
          unitVector(kEmbeddingDim, 0, sign: -1.0),
        ],
      );

      final knn = db.knnSearch(unitVector(kEmbeddingDim, 0), 3);
      expect(knn.length, 3);
      // Ascending distance.
      expect(knn[0].distance, lessThanOrEqualTo(knn[1].distance));
      expect(knn[1].distance, lessThanOrEqualTo(knn[2].distance));

      // Same vector: d=0 → cos=1.
      final same = knn.firstWhere((h) => h.rowid == 1);
      expect(same.distance, closeTo(0.0, 1e-5));
      expect(similarityFromDistance(same.distance), closeTo(1.0, 1e-4));

      // Orthogonal: d=sqrt(2) → cos=0.
      final ortho = knn.firstWhere((h) => h.rowid == 2);
      expect(ortho.distance, closeTo(math.sqrt(2.0), 1e-4));
      expect(similarityFromDistance(ortho.distance), closeTo(0.0, 1e-4));

      // Opposite: d=2 → cos=-1.
      final oppo = knn.firstWhere((h) => h.rowid == 3);
      expect(oppo.distance, closeTo(2.0, 1e-4));
      expect(similarityFromDistance(oppo.distance), closeTo(-1.0, 1e-4));
    });

    test('respects limit', () {
      final idA = db.insertFolder('/a', 100);
      final imgs = List.generate(5, (i) => image('/a/$i.jpg'));
      final vecs = List.generate(5, (i) => unitVector(kEmbeddingDim, i));
      db.insertImagesAndVectorsBatch(idA, imgs, vecs);

      expect(db.knnSearch(unitVector(kEmbeddingDim, 0), 3).length, 3);
    });

    test('folder filter restricts results to given folders', () {
      final idA = db.insertFolder('/a', 100);
      final idB = db.insertFolder('/b', 200);
      db.insertImagesAndVectorsBatch(
        idA,
        [image('/a/1.jpg'), image('/a/2.jpg')],
        [unitVector(kEmbeddingDim, 0), unitVector(kEmbeddingDim, 1)],
      );
      db.insertImagesAndVectorsBatch(
        idB,
        [image('/b/1.jpg'), image('/b/2.jpg'), image('/b/3.jpg')],
        [
          unitVector(kEmbeddingDim, 2),
          unitVector(kEmbeddingDim, 3),
          unitVector(kEmbeddingDim, 4),
        ],
      );

      // Query equals A's first vector; filter to folder B → only B rows.
      final filtered = db.knnSearchFiltered(unitVector(kEmbeddingDim, 0), 2, [
        idB,
      ]);
      expect(filtered.length, 2);
      for (final hit in filtered) {
        expect(db.getImageById(hit.rowid)!.$1, startsWith('/b/'));
      }

      // Empty filter = unfiltered search.
      final unfiltered = db.knnSearchFiltered(
        unitVector(kEmbeddingDim, 0),
        2,
        [],
      );
      expect(unfiltered.first.rowid, 1); // exact match first
    });

    test('modification time filter restricts results to recent images', () {
      final folderId = db.insertFolder('/a', 100);
      db.insertImagesAndVectorsBatch(
        folderId,
        [image('/a/old.jpg', mtime: 100), image('/a/new.jpg', mtime: 300)],
        [unitVector(kEmbeddingDim, 0), unitVector(kEmbeddingDim, 1)],
      );

      final filtered = db.knnSearchWithFilters(
        unitVector(kEmbeddingDim, 0),
        10,
        [],
        modifiedAfter: 200,
      );

      expect(filtered, hasLength(1));
      expect(db.getImageById(filtered.single.rowid)!.$1, '/a/new.jpg');
    });
  });
}
