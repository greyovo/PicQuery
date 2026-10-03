// Data types exposed by the Dart engine.

class IndexProgress {
  final int current;
  final int total;
  final int errors;
  final String? currentPath;
  final String? currentAlbum;

  const IndexProgress({
    required this.current,
    required this.total,
    required this.errors,
    this.currentPath,
    this.currentAlbum,
  });

  @override
  int get hashCode =>
      current.hashCode ^
      total.hashCode ^
      errors.hashCode ^
      currentPath.hashCode ^
      currentAlbum.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is IndexProgress &&
          runtimeType == other.runtimeType &&
          current == other.current &&
          total == other.total &&
          errors == other.errors &&
          currentPath == other.currentPath &&
          currentAlbum == other.currentAlbum;
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

class Album {
  final int id;
  final String albumPath;
  final int indexedAt;
  final int imageCount;
  final int totalImageCount;
  final bool isIndexComplete;
  final String? coverPath;

  const Album({
    required this.id,
    required this.albumPath,
    required this.indexedAt,
    required this.imageCount,
    required this.totalImageCount,
    required this.isIndexComplete,
    this.coverPath,
  });

  @override
  int get hashCode =>
      id.hashCode ^
      albumPath.hashCode ^
      indexedAt.hashCode ^
      imageCount.hashCode ^
      totalImageCount.hashCode ^
      isIndexComplete.hashCode ^
      coverPath.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Album &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          albumPath == other.albumPath &&
          indexedAt == other.indexedAt &&
          imageCount == other.imageCount &&
          totalImageCount == other.totalImageCount &&
          isIndexComplete == other.isIndexComplete &&
          coverPath == other.coverPath;
}

class AlbumUpdateInfo {
  final int albumId;
  final String albumPath;
  final int newCount;
  final int deletedCount;

  const AlbumUpdateInfo({
    required this.albumId,
    required this.albumPath,
    required this.newCount,
    required this.deletedCount,
  });

  @override
  int get hashCode =>
      albumId.hashCode ^
      albumPath.hashCode ^
      newCount.hashCode ^
      deletedCount.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AlbumUpdateInfo &&
          runtimeType == other.runtimeType &&
          albumId == other.albumId &&
          albumPath == other.albumPath &&
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
