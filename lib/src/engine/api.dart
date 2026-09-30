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

/// Index all supported images in a folder (recursively).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
/// The stream completes normally when done; when cancelled by the listener,
/// already-encoded-but-not-inserted results are discarded.
Stream<IndexProgress> indexFolder({
  required String folderPath,
  required bool isUpdate,
}) => indexer.indexFolder(folderPath: folderPath, isUpdate: isUpdate);

/// Index a list of image file paths directly (for mobile album selection).
/// When [isUpdate] is true, skips already-indexed files and removes deleted
/// file records.
Stream<IndexProgress> indexImages({
  required String albumName,
  required List<String> imagePaths,
  required bool isUpdate,
}) => indexer.indexImages(
  albumName: albumName,
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

/// Delete an indexed folder and all its associated image records and vectors.
Future<void> deleteFolder({required int folderId}) async {
  Db.instance.deleteFolder(folderId);
}

/// Delete all indexed folders and their associated image records and vectors.
/// For debug use only.
Future<void> deleteAllFolders() async {
  Db.instance.deleteAllFolders();
}

/// Get all indexed folders. Returns an empty list when the database is not
/// initialized.
Future<List<Folder>> getAllFolders() async {
  if (!Db.isInitialized) return const [];
  return Db.instance
      .getAllFolders()
      .map(
        (f) => Folder(
          id: f.id,
          folderPath: f.folderPath,
          indexedAt: f.indexedAt,
          imageCount: f.imageCount,
          totalImageCount: f.totalImageCount,
          isIndexComplete: f.isIndexComplete,
          coverPath: f.coverPath,
        ),
      )
      .toList();
}

/// Check for index updates across all indexed folders. Returns an empty list
/// when the database is not initialized or the check fails.
Future<List<FolderUpdateInfo>> checkForUpdates() async {
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
/// If [folderIds] is non-empty, only searches within the specified folders.
Future<List<SearchResult>> searchByText({
  required String query,
  required int limit,
  required List<int> folderIds,
}) async {
  final embedding = await OrtEngine.instance.encodeText(query);
  return _searchByEmbedding(embedding, limit, folderIds);
}

Future<List<SearchResult>> searchByTextWithFilters({
  required String query,
  required int limit,
  required List<int> folderIds,
  int? modifiedAfter,
}) async {
  final embedding = await OrtEngine.instance.encodeText(query);
  return _searchByEmbedding(
    embedding,
    limit,
    folderIds,
    modifiedAfter: modifiedAfter,
  );
}

/// Search indexed images by image similarity.
/// Returns up to [limit] results sorted by descending cosine similarity.
/// If [folderIds] is non-empty, only searches within the specified folders.
Future<List<SearchResult>> searchByImage({
  required String imagePath,
  required int limit,
  required List<int> folderIds,
}) async {
  final embedding = await OrtEngine.instance.encodeImageFile(imagePath);
  return _searchByEmbedding(embedding, limit, folderIds);
}

Future<List<SearchResult>> searchByImageWithFilters({
  required String imagePath,
  required int limit,
  required List<int> folderIds,
  int? modifiedAfter,
}) async {
  final embedding = await OrtEngine.instance.encodeImageFile(imagePath);
  return _searchByEmbedding(
    embedding,
    limit,
    folderIds,
    modifiedAfter: modifiedAfter,
  );
}

/// KNN lookup + distance→similarity conversion + metadata join, shared by
/// both search entry points.
Future<List<SearchResult>> _searchByEmbedding(
  List<double> embedding,
  int limit,
  List<int> folderIds, {
  int? modifiedAfter,
}) async {
  final db = Db.instance;
  final knnResults = db.knnSearchWithFilters(
    embedding,
    limit,
    folderIds,
    modifiedAfter: modifiedAfter,
  );

  final results = <SearchResult>[];
  for (final hit in knnResults) {
    final image = db.getImageById(hit.rowid);
    if (image == null) continue;
    results.add(
      SearchResult(
        filePath: image.$1,
        fileName: image.$2,
        similarity: similarityFromDistance(hit.distance),
      ),
    );
  }
  return results;
}
