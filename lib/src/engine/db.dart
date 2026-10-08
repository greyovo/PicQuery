// SQLite + sqlite_vector database layer.
// Dart vector storage and KNN search using the
// sqlite_vector extension instead of sqlite-vec's vec0 virtual tables.
//
// Distance metric (measured on 2026-09-15 via smoke test with known vector
// pairs cos=1/0/-1): sqlite_vector's `distance` for FLOAT32 columns is the
// **L2 distance** between the stored and query vectors. For L2-normalized
// vectors L2 = sqrt(2 - 2*cos), so cosine similarity = 1 - d²/2.
// (sqlite-vec used squared L2, cos_sim = 1 - d/2 — see similarityFromDistance.)

import 'dart:collection';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';
import 'package:sqlite3/sqlite3.dart';
import 'package:sqlite_vector/sqlite_vector.dart';

const int kBatchSize = 10;
const int kEmbeddingDim = 512;

final _log = Logger('engine.db');

/// Convert a measured `vector_full_scan` distance (L2, FLOAT32 metric) to
/// cosine similarity. Vectors are L2-normalized, so d = sqrt(2 - 2*cos) =>
/// cos = 1 - d²/2.
double similarityFromDistance(double distance) {
  final cos = 1.0 - (distance * distance) / 2.0;
  // Guard against float drift pushing slightly outside [-1, 1].
  return cos.clamp(-1.0, 1.0);
}

/// Serialize a float vector to a little-endian float32 BLOB.
Uint8List vectorToBlob(List<double> v) {
  final f32 = Float32List.fromList(v);
  return f32.buffer.asUint8List(f32.offsetInBytes, f32.lengthInBytes);
}

class FolderRow {
  final int id;
  final String folderPath;
  final String? displayName;
  final int indexedAt;
  final int imageCount;
  final int totalImageCount;
  final bool isIndexComplete;
  final String? coverPath;
  const FolderRow({
    required this.id,
    required this.folderPath,
    this.displayName,
    required this.indexedAt,
    required this.imageCount,
    required this.totalImageCount,
    required this.isIndexComplete,
    this.coverPath,
  });
}

class KnnHit {
  final int rowid;
  final double distance;
  const KnnHit({required this.rowid, required this.distance});
}

/// Image record used for batch insertion (fields mirror rust db.rs tuple).
class ImageRowData {
  final String filePath;
  final String fileName;
  final int fileSize;
  final int modifiedTime;
  final int width;
  final int height;
  final String format;
  final int indexedAt;
  const ImageRowData({
    required this.filePath,
    required this.fileName,
    required this.fileSize,
    required this.modifiedTime,
    required this.width,
    required this.height,
    required this.format,
    required this.indexedAt,
  });
}

class Db {
  Database? _db;
  final String path;

  Db._(this.path);

  static Db? _instance;

  static Db get instance {
    final db = _instance;
    if (db == null) {
      throw StateError('Database not initialized. Call initDb() first.');
    }
    return db;
  }

  static bool get isInitialized => _instance != null;

  /// Initialize the database, load the sqlite_vector extension, and create
  /// tables. Idempotent: re-calling on the same open path is a no-op; a
  /// closed instance (or a different path) is re-opened.
  static Future<void> init(String dbPath) async {
    final existing = _instance;
    if (existing != null) {
      if (existing.path == dbPath && existing._db != null) return;
      existing.close();
      _instance = null;
    }
    final db = Db._(dbPath);
    db._openAndInit();
    _instance = db;
  }

  /// Open an in-memory database (for tests / verification).
  static Db openInMemoryForTest() {
    final db = Db._(':memory:');
    db._openAndInit();
    return db;
  }

  void close() {
    _db?.close();
    _db = null;
  }

  Database get _conn {
    final db = _db;
    if (db == null) {
      throw StateError('Database not initialized');
    }
    return db;
  }

  void _openAndInit() {
    sqlite3.loadSqliteVectorExtension();
    final conn = path == ':memory:'
        ? sqlite3.openInMemory()
        : sqlite3.open(path);

    conn.execute('PRAGMA journal_mode=WAL;');
    conn.execute('PRAGMA foreign_keys=ON;');

    _db = conn;
    _initSchema();
    final vectorVersion = _conn.select('SELECT vector_version() AS version');
    final vectorBackend = _conn.select('SELECT vector_backend() AS backend');
    _log.info(
      'Database ready: sqlite=${sqlite3.version}; '
      'sqlite-vector=${vectorVersion.first['version']}; '
      'backend=${vectorBackend.first['backend']}.',
    );
  }

  void _initSchema() {
    _conn.execute('''
      CREATE TABLE IF NOT EXISTS folders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        folder_path TEXT NOT NULL UNIQUE,
        indexed_at INTEGER NOT NULL,
        image_count INTEGER NOT NULL DEFAULT 0
        ,display_name TEXT
        ,total_image_count INTEGER NOT NULL DEFAULT 0
        ,is_index_complete INTEGER NOT NULL DEFAULT 1
      );
    ''');
    // Existing v2 databases need these checkpoint columns too.
    try {
      _conn.execute('ALTER TABLE folders ADD COLUMN display_name TEXT');
    } catch (_) {}
    try {
      _conn.execute(
        'ALTER TABLE folders ADD COLUMN total_image_count INTEGER NOT NULL DEFAULT 0',
      );
    } catch (_) {}
    try {
      _conn.execute(
        'ALTER TABLE folders ADD COLUMN is_index_complete INTEGER NOT NULL DEFAULT 1',
      );
    } catch (_) {}
    _conn.execute('''
      CREATE TABLE IF NOT EXISTS images (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
        file_path TEXT NOT NULL UNIQUE,
        file_name TEXT NOT NULL,
        file_size INTEGER NOT NULL,
        modified_time INTEGER NOT NULL,
        width INTEGER NOT NULL,
        height INTEGER NOT NULL,
        format TEXT NOT NULL,
        indexed_at INTEGER NOT NULL,
        ocr_text TEXT
      );
    ''');
    _conn.execute(
      'CREATE INDEX IF NOT EXISTS idx_images_modified ON images(file_path, modified_time);',
    );
    _conn.execute(
      'CREATE INDEX IF NOT EXISTS idx_images_folder ON images(folder_id);',
    );
    _conn.execute('''
      CREATE TABLE IF NOT EXISTS vector_images (
        rowid INTEGER PRIMARY KEY,
        embedding BLOB NOT NULL
      );
    ''');

    // sqlite-vector keeps this table context on the SQLite connection, so it
    // must be initialized every time the database is opened. A persistent
    // marker cannot represent that state: after an app restart it would make
    // vector_full_scan fail with "unable to retrieve context".
    _conn.select(
      "SELECT vector_init('vector_images', 'embedding', 'type=FLOAT32,dimension=$kEmbeddingDim')",
    );
  }

  // ---- Folders ----

  /// Insert a folder record and return its id.
  int insertFolder(String folderPath, int indexedAt, {String? displayName}) {
    _conn.execute(
      'INSERT INTO folders (folder_path, indexed_at, display_name) VALUES (?1, ?2, ?3)',
      [folderPath, indexedAt, displayName],
    );
    return _conn.lastInsertRowId;
  }

  /// Find a folder by path; returns (id, imageCount) or null.
  FolderRow? findFolderByPath(String folderPath) {
    final rows = _conn.select(
      '''SELECT id, folder_path, display_name, indexed_at, image_count, total_image_count, is_index_complete,
          (SELECT file_path FROM images WHERE folder_id = folders.id
           ORDER BY indexed_at DESC, id DESC LIMIT 1) AS cover_path
         FROM folders WHERE folder_path = ?1''',
      [folderPath],
    );
    if (rows.isEmpty) return null;
    final r = rows.first;
    return FolderRow(
      id: r['id'] as int,
      folderPath: r['folder_path'] as String,
      displayName: r['display_name'] as String?,
      indexedAt: r['indexed_at'] as int,
      imageCount: r['image_count'] as int,
      totalImageCount: r['total_image_count'] as int,
      isIndexComplete: (r['is_index_complete'] as int) == 1,
      coverPath: r['cover_path'] as String?,
    );
  }

  void updateFolderDisplayName(int folderId, String displayName) {
    _conn.execute('UPDATE folders SET display_name = ?1 WHERE id = ?2', [
      displayName,
      folderId,
    ]);
  }

  /// Update folder's indexed_at timestamp and image_count.
  void updateFolder(
    int folderId,
    int indexedAt,
    int imageCount, {
    required int totalImageCount,
    required bool isIndexComplete,
  }) {
    _conn.execute(
      'UPDATE folders SET indexed_at = ?1, image_count = ?2, total_image_count = ?3, is_index_complete = ?4 WHERE id = ?5',
      [
        indexedAt,
        imageCount,
        totalImageCount,
        isIndexComplete ? 1 : 0,
        folderId,
      ],
    );
  }

  /// Get all indexed folders ordered by indexed_at desc.
  List<FolderRow> getAllFolders() {
    final rows = _conn.select('''SELECT id, folder_path, display_name, indexed_at, image_count, total_image_count, is_index_complete,
          (SELECT file_path FROM images WHERE folder_id = folders.id
           ORDER BY indexed_at DESC, id DESC LIMIT 1) AS cover_path
         FROM folders ORDER BY indexed_at DESC''');
    return rows
        .map(
          (r) => FolderRow(
            id: r['id'] as int,
            folderPath: r['folder_path'] as String,
            displayName: r['display_name'] as String?,
            indexedAt: r['indexed_at'] as int,
            imageCount: r['image_count'] as int,
            totalImageCount: r['total_image_count'] as int,
            isIndexComplete: (r['is_index_complete'] as int) == 1,
            coverPath: r['cover_path'] as String?,
          ),
        )
        .toList();
  }

  /// Delete a folder and all associated images (CASCADE) and vectors.
  void deleteFolder(int folderId) {
    final rowids = _conn
        .select('SELECT id FROM images WHERE folder_id = ?1', [folderId])
        .map((r) => r['id'] as int)
        .toList();
    _conn.execute('BEGIN');
    try {
      for (final rowid in rowids) {
        _conn.execute('DELETE FROM vector_images WHERE rowid = ?1', [rowid]);
      }
      _conn.execute('DELETE FROM folders WHERE id = ?1', [folderId]);
      _conn.execute('COMMIT');
    } catch (_) {
      _conn.execute('ROLLBACK');
      rethrow;
    }
  }

  /// Delete all folders, images, and vectors. For debug use only.
  void deleteAllFolders() {
    _conn.execute('DELETE FROM vector_images;');
    _conn.execute('DELETE FROM folders;');
  }

  // ---- Images & vectors ----

  /// Insert multiple images and their vectors in a single transaction.
  /// Returns the number of successfully inserted images.
  int insertImagesAndVectorsBatch(
    int folderId,
    List<ImageRowData> images,
    List<List<double>> vectors,
  ) {
    assert(images.length == vectors.length);
    _conn.execute('BEGIN');
    try {
      var count = 0;
      for (var i = 0; i < images.length; i++) {
        final img = images[i];
        _conn.execute(
          'INSERT INTO images (folder_id, file_path, file_name, file_size, modified_time, width, height, format, indexed_at) '
          'VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)',
          [
            folderId,
            img.filePath,
            img.fileName,
            img.fileSize,
            img.modifiedTime,
            img.width,
            img.height,
            img.format,
            img.indexedAt,
          ],
        );
        final rowid = _conn.lastInsertRowId;
        _conn.execute(
          'INSERT INTO vector_images (rowid, embedding) VALUES (?1, vector_as_f32(?2))',
          [rowid, vectorToBlob(vectors[i])],
        );
        count++;
      }
      _conn.execute('COMMIT');
      return count;
    } catch (_) {
      _conn.execute('ROLLBACK');
      rethrow;
    }
  }

  /// Check if an image is already indexed (by path and unchanged mtime).
  bool isImageIndexed(String filePath, int modifiedTime) {
    final rows = _conn.select(
      'SELECT COUNT(*) AS c FROM images WHERE file_path = ?1 AND modified_time = ?2',
      [filePath, modifiedTime],
    );
    return (rows.first['c'] as int) > 0;
  }

  /// Get all indexed file paths for a given folder_id.
  List<String> getIndexedFilePaths(int folderId) {
    return _conn
        .select('SELECT file_path FROM images WHERE folder_id = ?1', [folderId])
        .map((r) => r['file_path'] as String)
        .toList();
  }

  /// Delete image records and their corresponding vector records for the
  /// given file_paths. Returns the number of deleted image records.
  int deleteImagesByPaths(int folderId, List<String> filePaths) {
    if (filePaths.isEmpty) return 0;
    final placeholders = List.filled(filePaths.length, '?').join(',');
    final rowids = _conn
        .select(
          'SELECT id FROM images WHERE folder_id = ?1 AND file_path IN ($placeholders)',
          [folderId, ...filePaths],
        )
        .map((r) => r['id'] as int)
        .toList();

    _conn.execute('BEGIN');
    try {
      for (final rowid in rowids) {
        _conn.execute('DELETE FROM vector_images WHERE rowid = ?1', [rowid]);
      }
      _conn.execute(
        'DELETE FROM images WHERE folder_id = ?1 AND file_path IN ($placeholders)',
        [folderId, ...filePaths],
      );
      _conn.execute('COMMIT');
    } catch (_) {
      _conn.execute('ROLLBACK');
      rethrow;
    }
    return rowids.length;
  }

  /// Resolve the indexed album, rather than a mobile photo's cache directory.
  (String, String?)? getImageAlbum(String filePath) {
    final rows = _conn.select(
      '''SELECT folders.folder_path, folders.display_name FROM images
         JOIN folders ON folders.id = images.folder_id
         WHERE images.file_path = ?1''',
      [filePath],
    );
    if (rows.isEmpty) return null;
    return (
      rows.first['folder_path'] as String,
      rows.first['display_name'] as String?,
    );
  }

  /// Get image metadata by rowid; returns (filePath, fileName) or null.
  (String, String)? getImageById(int id) {
    final rows = _conn.select(
      'SELECT file_path, file_name FROM images WHERE id = ?1',
      [id],
    );
    if (rows.isEmpty) return null;
    return (
      rows.first['file_path'] as String,
      rows.first['file_name'] as String,
    );
  }

  /// Get total image count across all folders.
  int getTotalImageCount() {
    final rows = _conn.select('SELECT COUNT(*) AS c FROM images');
    return rows.first['c'] as int;
  }

  /// Get the number of stored embeddings. Useful for checking that indexing
  /// committed both halves of an image/vector batch.
  int getTotalVectorCount() {
    final rows = _conn.select('SELECT COUNT(*) AS c FROM vector_images');
    return rows.first['c'] as int;
  }

  /// Get image count for a specific folder.
  int getFolderImageCount(int folderId) {
    final rows = _conn.select(
      'SELECT COUNT(*) AS c FROM images WHERE folder_id = ?1',
      [folderId],
    );
    return rows.first['c'] as int;
  }

  /// Get the last indexed folder path.
  String? getLastIndexedPath() {
    final rows = _conn.select(
      'SELECT folder_path FROM folders ORDER BY indexed_at DESC LIMIT 1',
    );
    if (rows.isEmpty) return null;
    return rows.first['folder_path'] as String;
  }

  // ---- KNN ----

  /// KNN search: find the k nearest vectors to the query embedding.
  /// Returns a list of (rowid, distance) sorted by ascending distance.
  List<KnnHit> knnSearch(List<double> query, int limit) {
    _validateKnnArguments(query, limit);
    final rows = _conn.select(
      "SELECT rowid, distance FROM vector_full_scan('vector_images', 'embedding', vector_as_f32(?), ?)",
      [vectorToBlob(query), limit],
    );
    return rows
        .map(
          (r) => KnnHit(
            rowid: r['rowid'] as int,
            distance: (r['distance'] as num).toDouble(),
          ),
        )
        .toList();
  }

  /// KNN search filtered by folder IDs. Returns up to [limit] hits belonging
  /// to the given folders, using limit*3 candidates before filtering.
  List<KnnHit> knnSearchFiltered(
    List<double> query,
    int limit,
    List<int> folderIds,
  ) => knnSearchWithFilters(query, limit, folderIds);

  /// KNN search constrained by folder and file modification time.
  List<KnnHit> knnSearchWithFilters(
    List<double> query,
    int limit,
    List<int> folderIds, {
    int? modifiedAfter,
  }) {
    _validateKnnArguments(query, limit);
    final imageCount = getTotalImageCount();
    final vectorCount = getTotalVectorCount();
    _log.info(
      'KNN start: limit=$limit, folders=${folderIds.length}, '
      'modifiedAfter=$modifiedAfter, images=$imageCount, vectors=$vectorCount.',
    );
    if (imageCount != vectorCount) {
      _log.warning(
        'Index integrity mismatch: images=$imageCount, vectors=$vectorCount.',
      );
    }
    if (folderIds.isEmpty && modifiedAfter == null) {
      final results = knnSearch(query, limit);
      _log.info('KNN complete: hits=${results.length}.');
      return results;
    }
    final conditions = <String>[];
    final parameters = <Object?>[];
    if (folderIds.isNotEmpty) {
      final placeholders = List.filled(folderIds.length, '?').join(',');
      conditions.add('folder_id IN ($placeholders)');
      parameters.addAll(folderIds);
    }
    if (modifiedAfter != null) {
      conditions.add('modified_time >= ?');
      parameters.add(modifiedAfter);
    }
    final validRowids = HashSet<int>.from(
      _conn
          .select(
            'SELECT id FROM images WHERE ${conditions.join(' AND ')}',
            parameters,
          )
          .map((r) => r['id'] as int),
    );
    _log.info('KNN filter matched ${validRowids.length} image rows.');
    if (validRowids.isEmpty) return const [];

    final rows = _conn.select(
      "SELECT rowid, distance FROM vector_full_scan('vector_images', 'embedding', vector_as_f32(?), ?)",
      [vectorToBlob(query), getTotalImageCount()],
    );
    final results = <KnnHit>[];
    for (final r in rows) {
      final rowid = r['rowid'] as int;
      if (validRowids.contains(rowid)) {
        results.add(
          KnnHit(rowid: rowid, distance: (r['distance'] as num).toDouble()),
        );
        if (results.length >= limit) break;
      }
    }
    _log.info(
      'KNN complete: scanned=${rows.length}, filteredHits=${results.length}.',
    );
    return results;
  }

  void _validateKnnArguments(List<double> query, int limit) {
    if (query.length != kEmbeddingDim) {
      throw ArgumentError.value(
        query.length,
        'query.length',
        'Expected $kEmbeddingDim',
      );
    }
    if (limit <= 0) {
      throw ArgumentError.value(limit, 'limit', 'Must be positive');
    }
    final nonFinite = query.where((value) => !value.isFinite).length;
    if (nonFinite != 0) {
      throw StateError(
        'Query embedding contains $nonFinite non-finite values. '
        'Execution provider may have produced invalid output.',
      );
    }
  }
}
