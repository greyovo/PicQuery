// Public facade for the Dart image search engine.
//
// Logs use the application's logging pipeline with module-specific names.

import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/db.dart';
import 'package:picquery_app/src/engine/indexer.dart' as indexer;
import 'package:picquery_app/src/engine/models.dart';
import 'package:picquery_app/src/engine/ort_engine.dart';
import 'package:picquery_app/src/engine/translator.dart';

export 'models.dart';

final _log = Logger('engine.api');

/// Initialize the database at the given path.
/// Must be called before any indexing or search operations.
Future<void> initDb({required String dbPath}) => Db.init(dbPath);

/// Load ONNX models from the given paths.
/// Must be called once before any encoding operations.
/// When [force] is true, will attempt to reload models even if already
/// initialized.
Future<void> loadClipModels({
  required String textModelPath,
  required String visualModelPath,
  required bool force,
}) => OrtEngine.instance.loadClipModels(
  textModelPath: textModelPath,
  visualModelPath: visualModelPath,
  force: force,
);

/// Whether a folder or its subfolders contain supported image files.
Future<bool> hasIndexableImages({required String albumPath}) =>
    indexer.hasIndexableImages(albumPath);

/// Index all supported images in a album (recursively).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
/// The stream completes normally when done; when cancelled by the listener,
/// already-encoded-but-not-inserted results are discarded.
Stream<IndexProgress> indexAlbum({
  required String albumPath,
  required bool isUpdate,
}) => indexer.indexAlbum(albumPath: albumPath, isUpdate: isUpdate);

/// Index a list of image file paths directly (for mobile album selection).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
Stream<IndexProgress> indexImages({
  required String albumName,
  String? displayName,
  required List<String> imagePaths,
  required bool isUpdate,
}) => indexer.indexImages(
  albumName: albumName,
  displayName: displayName,
  imagePaths: imagePaths,
  isUpdate: isUpdate,
);

/// Get the index status (total image count and last indexed path).
/// Returns an empty status when the database is not initialized.
Future<IndexStatus> getIndexStatus() async {
  if (!Db.isInitialized) {
    return const IndexStatus(totalImages: 0);
  }
  return IndexStatus(
    totalImages: Db.instance.getTotalImageCount(),
    lastIndexedPath: Db.instance.getLastIndexedPath(),
  );
}

/// Delete an indexed album and all its associated image records and vectors.
Future<void> deleteAlbum({required int albumId}) async {
  Db.instance.deleteFolder(albumId);
}

/// Delete all indexed albums and their associated image records and vectors.
/// For debug use only.
Future<void> deleteAllAlbums() async {
  Db.instance.deleteAllFolders();
}

/// Get all indexed albums. Returns an empty list when the database is not
/// initialized.
Future<List<Album>> getAllAlbums() async {
  if (!Db.isInitialized) return const [];
  return Db.instance
      .getAllFolders()
      .map(
        (f) => Album(
          id: f.id,
          albumPath: f.folderPath,
          displayName: f.displayName,
          indexedAt: f.indexedAt,
          imageCount: f.imageCount,
          totalImageCount: f.totalImageCount,
          isIndexComplete: f.isIndexComplete,
          coverPath: f.coverPath,
        ),
      )
      .toList();
}

/// Check for index updates across all indexed albums. Returns an empty list
/// when the database is not initialized or the check fails.
Future<List<AlbumUpdateInfo>> checkForUpdates() async {
  if (!Db.isInitialized) return const [];
  try {
    return await indexer.checkForUpdates();
  } catch (_) {
    _log.severe('Failed to check for index updates.');
    return const [];
  }
}

/// Index pending updates stored by checkForUpdates().
Stream<IndexProgress> indexPendingUpdates() => indexer.indexPendingUpdates();

/// Load the MarianMT Chinese→English translation ONNX model and SentencePiece
/// tokenizers. Must be called once before any translation operations.
/// When [force] is true, will attempt to reload even if already initialized.
Future<void> loadTranslationModel({
  required String modelPath,
  required String sourceSpPath,
  required String targetSpPath,
  required bool force,
}) => Translator.instance.load(
  modelPath: modelPath,
  sourceSpPath: sourceSpPath,
  targetSpPath: targetSpPath,
  force: force,
);

/// Translate a Chinese sentence to English using autoregressive greedy
/// decoding. Returns the translated English text, or throws if the model is
/// not initialized.
Future<String> translateZhToEn({required String sentence}) =>
    Translator.instance.translate(sentence);

/// Search indexed images by natural language text query.
/// Returns up to [limit] results sorted by descending cosine similarity.
/// If [albumIds] is non-empty, only searches within the specified albums.
Future<List<SearchResult>> searchByText({
  required String query,
  required int limit,
  required List<int> albumIds,
}) async {
  final embedding = await OrtEngine.instance.encodeText(query);
  return _searchByEmbedding(embedding, limit, albumIds);
}

Future<List<SearchResult>> searchByTextWithFilters({
  required String query,
  required int limit,
  required List<int> albumIds,
  int? modifiedAfter,
}) async {
  final stopwatch = Stopwatch()..start();
  _log.info(
    'Text search start: queryLength=${query.length}, limit=$limit, '
    'albums=${albumIds.length}, modifiedAfter=$modifiedAfter.',
  );
  final embedding = await OrtEngine.instance.encodeText(query);
  _log.info('Text embedding completed in ${stopwatch.elapsedMilliseconds}ms.');
  final results = await _searchByEmbedding(
    embedding,
    limit,
    albumIds,
    modifiedAfter: modifiedAfter,
  );
  _log.info(
    'Text search complete: results=${results.length}, '
    'elapsed=${stopwatch.elapsedMilliseconds}ms.',
  );
  return results;
}

/// Search indexed images by image similarity.
/// Returns up to [limit] results sorted by descending cosine similarity.
/// If [albumIds] is non-empty, only searches within the specified albums.
Future<List<SearchResult>> searchByImage({
  required String imagePath,
  required int limit,
  required List<int> albumIds,
}) async {
  final embedding = await OrtEngine.instance.encodeImageFile(imagePath);
  return _searchByEmbedding(embedding, limit, albumIds);
}

Future<List<SearchResult>> searchByImageWithFilters({
  required String imagePath,
  required int limit,
  required List<int> albumIds,
  int? modifiedAfter,
}) async {
  final stopwatch = Stopwatch()..start();
  _log.info(
    'Image search start: limit=$limit, albums=${albumIds.length}, '
    'modifiedAfter=$modifiedAfter.',
  );
  final embedding = await OrtEngine.instance.encodeImageFile(imagePath);
  _log.info('Image embedding completed in ${stopwatch.elapsedMilliseconds}ms.');
  final results = await _searchByEmbedding(
    embedding,
    limit,
    albumIds,
    modifiedAfter: modifiedAfter,
  );
  _log.info(
    'Image search complete: results=${results.length}, '
    'elapsed=${stopwatch.elapsedMilliseconds}ms.',
  );
  return results;
}

/// KNN lookup + distance→similarity conversion + metadata join, shared by
/// both search entry points.
Future<List<SearchResult>> _searchByEmbedding(
  List<double> embedding,
  int limit,
  List<int> albumIds, {
  int? modifiedAfter,
}) async {
  final db = Db.instance;
  final knnResults = db.knnSearchWithFilters(
    embedding,
    limit,
    albumIds,
    modifiedAfter: modifiedAfter,
  );

  final results = <SearchResult>[];
  var missingMetadata = 0;
  for (final hit in knnResults) {
    final image = db.getImageById(hit.rowid);
    if (image == null) {
      missingMetadata++;
      continue;
    }
    results.add(
      SearchResult(
        filePath: image.$1,
        fileName: image.$2,
        similarity: similarityFromDistance(hit.distance),
      ),
    );
  }
  if (missingMetadata != 0) {
    _log.warning(
      'Dropped $missingMetadata KNN hits because image metadata was missing.',
    );
  }
  return results;
}
