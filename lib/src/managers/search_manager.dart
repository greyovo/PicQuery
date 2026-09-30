import 'package:flutter/foundation.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/models/search_state.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:watch_it/watch_it.dart';

SearchManager get searchManager => di<SearchManager>();

class SearchManager {
  static const int maxRecentSearches = 10;
  static const int maxRecentViewedPhotos = 10;
  static const int defaultResultLimit = 50;
  static const List<int> resultLimitOptions = [25, 50, 100];

  final selectedFolderIds = ValueNotifier<Set<int>>({});
  final resultLimit = ValueNotifier<int>(defaultResultLimit);
  final timeRange = ValueNotifier<SearchTimeRange>(SearchTimeRange.anyTime);
  final isCustomScope = ValueNotifier<bool>(false);
  final recentSearches = ValueNotifier<List<String>>(
    SettingsStore.getRecentSearches(),
  );
  final recentViewedPhotos = ValueNotifier<List<SearchResult>>(
    SettingsStore.getRecentViewedPhotos()
        .map(
          (path) => SearchResult(
            filePath: path,
            fileName: Uri.file(path).pathSegments.last,
            similarity: 0,
          ),
        )
        .toList(),
  );

  void setSelectedFolderIds(Set<int> ids) {
    selectedFolderIds.value = ids;
    isCustomScope.value = ids.isNotEmpty;
  }

  void clearScope() {
    selectedFolderIds.value = {};
    isCustomScope.value = false;
  }

  void clearFilters() {
    clearScope();
    resultLimit.value = defaultResultLimit;
    timeRange.value = SearchTimeRange.anyTime;
  }

  /// Records a search query: moves it to the front (deduped) and caps the
  /// list at [maxRecentSearches], persisting to [SettingsStore].
  void addRecentSearch(String query) {
    if (query.trim().isEmpty) return;
    final updated = List<String>.from(recentSearches.value)..remove(query);
    updated.insert(0, query);
    if (updated.length > maxRecentSearches) {
      updated.removeRange(maxRecentSearches, updated.length);
    }
    recentSearches.value = updated;
    SettingsStore.setRecentSearches(updated);
  }

  /// Removes all persisted recent text searches.
  Future<void> clearRecentSearches() async {
    recentSearches.value = const [];
    await SettingsStore.setRecentSearches(const []);
  }

  /// Records a photo opened from search results, newest first and deduplicated.
  void addRecentViewedPhoto(SearchResult result) {
    final updated = List<SearchResult>.from(recentViewedPhotos.value)
      ..removeWhere((item) => item.filePath == result.filePath)
      ..insert(0, result);
    if (updated.length > maxRecentViewedPhotos) {
      updated.removeRange(maxRecentViewedPhotos, updated.length);
    }
    recentViewedPhotos.value = updated;
    SettingsStore.setRecentViewedPhotos(
      updated.map((item) => item.filePath).toList(),
    );
  }

  /// Removes all persisted recently viewed photos.
  Future<void> clearRecentViewedPhotos() async {
    recentViewedPhotos.value = const [];
    await SettingsStore.setRecentViewedPhotos(const []);
  }
}
