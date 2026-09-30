// Data types exposed by the Dart engine.

class IndexProgress {
  final int current;
  final int total;
  final int errors;
  final String? currentPath;
  final String? currentFolder;

  const IndexProgress({
    required this.current,
    required this.total,
    required this.errors,
    this.currentPath,
    this.currentFolder,
  });

  @override
  int get hashCode =>
      current.hashCode ^
      total.hashCode ^
      errors.hashCode ^
      currentPath.hashCode ^
      currentFolder.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is IndexProgress &&
          runtimeType == other.runtimeType &&
          current == other.current &&
          total == other.total &&
          errors == other.errors &&
          currentPath == other.currentPath &&
          currentFolder == other.currentFolder;
}

class IndexStatus {
  final int totalImages;
  final String? lastIndexedPath;

  const IndexStatus({required this.totalImages, this.lastIndexedPath});

  @override
  int get hashCode => totalImages.hashCode ^ lastIndexedPath.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is IndexStatus &&
          runtimeType == other.runtimeType &&
          totalImages == other.totalImages &&
          lastIndexedPath == other.lastIndexedPath;
}

class Folder {
  final int id;
  final String folderPath;
  final int indexedAt;
  final int imageCount;
  final int totalImageCount;
  final bool isIndexComplete;
  final String? coverPath;

  const Folder({
    required this.id,
    required this.folderPath,
    required this.indexedAt,
    required this.imageCount,
    required this.totalImageCount,
    required this.isIndexComplete,
    this.coverPath,
  });

  @override
  int get hashCode =>
      id.hashCode ^
      folderPath.hashCode ^
      indexedAt.hashCode ^
      imageCount.hashCode ^
      totalImageCount.hashCode ^
      isIndexComplete.hashCode ^
      coverPath.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Folder &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          folderPath == other.folderPath &&
          indexedAt == other.indexedAt &&
          imageCount == other.imageCount &&
          totalImageCount == other.totalImageCount &&
          isIndexComplete == other.isIndexComplete &&
          coverPath == other.coverPath;
}

class FolderUpdateInfo {
  final int folderId;
  final String folderPath;
  final int newCount;
  final int deletedCount;

  const FolderUpdateInfo({
    required this.folderId,
    required this.folderPath,
    required this.newCount,
    required this.deletedCount,
  });

  @override
  int get hashCode =>
      folderId.hashCode ^
      folderPath.hashCode ^
      newCount.hashCode ^
      deletedCount.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FolderUpdateInfo &&
          runtimeType == other.runtimeType &&
          folderId == other.folderId &&
          folderPath == other.folderPath &&
          newCount == other.newCount &&
          deletedCount == other.deletedCount;
}

class SearchResult {
  final String filePath;
  final String fileName;
  final double similarity;

  const SearchResult({
    required this.filePath,
    required this.fileName,
    required this.similarity,
  });

  @override
  int get hashCode =>
      filePath.hashCode ^ fileName.hashCode ^ similarity.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SearchResult &&
          runtimeType == other.runtimeType &&
          filePath == other.filePath &&
          fileName == other.fileName &&
          similarity == other.similarity;
}
