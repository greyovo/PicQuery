import 'package:flutter/material.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/folder_manager.dart';
import 'package:picquery_app/src/managers/search_manager.dart';
import 'package:picquery_app/src/models/search_state.dart';
import 'package:picquery_app/src/pages/search_results_page.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/image_search_picker.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/view/folder_selector_view.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';
import 'package:picquery_app/src/widgets/search_input_card.dart';
import 'package:picquery_app/src/widgets/search_filter_button.dart';
import 'package:watch_it/watch_it.dart';

final _log = Logger('search_page');

class SearchPage extends WatchingStatefulWidget {
  const SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final _queryController = TextEditingController();

  Route<void> _searchResultsRoute(SearchResultsPage page) =>
      PageRouteBuilder<void>(
        pageBuilder: (_, _, _) => page,
        transitionDuration: const Duration(milliseconds: 280),
        reverseTransitionDuration: const Duration(milliseconds: 220),
        transitionsBuilder: (_, animation, _, child) => FadeTransition(
          opacity: CurvedAnimation(parent: animation, curve: Curves.easeOut),
          child: child,
        ),
      );

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  bool _containsChinese(String text) => RegExp(r'[一-鿿]').hasMatch(text);

  Future<void> _searchByText() async {
    final query = _queryController.text.trim();
    if (query.isEmpty) return;
    try {
      var searchQuery = query;
      if (_containsChinese(query)) {
        try {
          searchQuery = await translateZhToEn(sentence: query);
        } catch (_) {
          _log.severe('Translation failed; using the original query.');
        }
      }
      final results = await searchByTextWithFilters(
        query: searchQuery,
        limit: searchManager.resultLimit.value,
        folderIds: searchManager.selectedFolderIds.value.toList(),
        modifiedAfter: searchManager.timeRange.value.modifiedAfterSeconds(
          DateTime.now(),
        ),
      );
      searchManager.addRecentSearch(query);
      if (mounted) {
        Navigator.push<void>(
          context,
          _searchResultsRoute(
            SearchResultsPage(
              results: results,
              query: query,
              realQuery: searchQuery,
              searchMode: SearchMode.text,
            ),
          ),
        );
      }
    } catch (error) {
      _log.severe('Text search failed.');
      if (mounted) Toast.showMessage(context.l10n.searchFailed(error));
    }
  }

  Future<void> _searchByImage() async {
    try {
      final results = await pickImageAndSearch(
        context,
        searchManager.selectedFolderIds.value.toList(),
        limit: searchManager.resultLimit.value,
        modifiedAfter: searchManager.timeRange.value.modifiedAfterSeconds(
          DateTime.now(),
        ),
      );
      if (results != null && mounted) {
        Navigator.push<void>(
          context,
          _searchResultsRoute(
            SearchResultsPage(results: results, searchMode: SearchMode.image),
          ),
        );
      }
    } catch (error) {
      _log.severe('Image search failed.');
      if (mounted) Toast.showMessage(context.l10n.imageSearchFailed(error));
    }
  }

  Future<void> _showFolderSelector() async {
    await showContentInDialogOrPage<Set<int>>(
      context: context,
      builder: (context) => FolderSelectorView(
        initialSelection: searchManager.selectedFolderIds.value,
      ),
    );
  }

  void _onRecentSearchSelected(String query) {
    _queryController.value = TextEditingValue(
      text: query,
      selection: TextSelection.collapsed(offset: query.length),
    );
    _searchByText();
  }

  String _scopeLabel(BuildContext context, Set<int> selectedIds) {
    if (selectedIds.isEmpty) return context.l10n.allFolders;
    return context.l10n.selectedAlbumsCount(selectedIds.length);
  }

  List<String> _selectedFolderNames(
    List<Folder> folders,
    Set<int> selectedIds,
  ) {
    return folders
        .where((folder) => selectedIds.contains(folder.id))
        .map((folder) => folder.folderPath.split('/').last)
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final folders = watchValue((FolderManager manager) => manager.folders);
    final selectedIds = watchValue(
      (SearchManager manager) => manager.selectedFolderIds,
    );
    final timeRange = watchValue((SearchManager manager) => manager.timeRange);
    final recentSearches = watchValue(
      (SearchManager manager) => manager.recentSearches,
    );
    final selectedFolderNames = _selectedFolderNames(folders, selectedIds);

    return Scaffold(
      body: LayoutBuilder(
        builder: (context, constraints) {
          final isDesktopLayout = constraints.maxWidth >= 760;
          return SingleChildScrollView(
            padding: EdgeInsets.symmetric(
              horizontal: isDesktopLayout ? 40 : 20,
              vertical: 24,
            ),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: constraints.maxHeight - 48,
              ),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: kSearchInputMaxWidth,
                  ),
                  child: _HeroSection(
                    controller: _queryController,
                    isDesktop: isDesktopLayout,
                    onSearch: _searchByText,
                    onImageSearch: _searchByImage,
                    scopeLabel: _scopeLabel(context, selectedIds),
                    selectedFolderNames: selectedFolderNames,
                    hasFolderFilter: selectedIds.isNotEmpty,
                    onSelectScope: _showFolderSelector,
                    timeRange: timeRange,
                    onTimeRangeChanged: (value) =>
                        searchManager.timeRange.value = value,
                    onClearFilters: searchManager.clearFilters,
                    recentSearches: recentSearches.take(10).toList(),
                    onRecentSearchSelected: _onRecentSearchSelected,
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _HeroSection extends StatelessWidget {
  const _HeroSection({
    required this.controller,
    required this.isDesktop,
    required this.onSearch,
    required this.onImageSearch,
    required this.scopeLabel,
    required this.selectedFolderNames,
    required this.hasFolderFilter,
    required this.onSelectScope,
    required this.timeRange,
    required this.onTimeRangeChanged,
    required this.onClearFilters,
    required this.recentSearches,
    required this.onRecentSearchSelected,
  });

  final TextEditingController controller;
  final bool isDesktop;
  final VoidCallback onSearch;
  final VoidCallback onImageSearch;
  final String scopeLabel;
  final List<String> selectedFolderNames;
  final bool hasFolderFilter;
  final VoidCallback onSelectScope;
  final SearchTimeRange timeRange;
  final ValueChanged<SearchTimeRange> onTimeRangeChanged;
  final VoidCallback onClearFilters;
  final List<String> recentSearches;
  final ValueChanged<String> onRecentSearchSelected;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          isDesktop
              ? context.l10n.searchPhotosTitle
              : context.l10n.searchPhotosTitleMobile,
          style:
              (isDesktop
                      ? Theme.of(context).textTheme.headlineLarge
                      : Theme.of(context).textTheme.headlineMedium)
                  ?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -1),
        ),
        const SizedBox(height: 8),
        Text(
          isDesktop
              ? context.l10n.searchPhotosDescription
              : context.l10n.searchPhotosDescriptionMobile,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: context.colors.onSurfaceVariant,
            fontWeight: FontWeight.w400,
          ),
        ),
        SizedBox(height: isDesktop ? 34 : 24),
        Hero(
          tag: kSearchInputHeroTag,
          child: SearchInputCard(
            queryController: controller,
            onSearch: onSearch,
            onImageUpload: onImageSearch,
          ),
        ),
        const SizedBox(height: 20),
        _SearchFilters(
          scopeLabel: scopeLabel,
          hasFolderFilter: hasFolderFilter,
          onSelectScope: onSelectScope,
          timeRange: timeRange,
          onTimeRangeChanged: onTimeRangeChanged,
          onClear: onClearFilters,
        ),
        if (selectedFolderNames.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 6, left: 4, right: 4),
            child: Text(
              [
                ...selectedFolderNames.take(5),
                if (selectedFolderNames.length > 5) '…',
              ].join(', '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: context.colors.primary),
            ),
          ),
        if (recentSearches.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  context.l10n.recentSearches,
                  style: Theme.of(context).textTheme.titleLarge
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 14),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    for (final query in recentSearches)
                      ActionChip(
                        label: Text(
                          query,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onPressed: () => onRecentSearchSelected(query),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        visualDensity: VisualDensity.compact,
                        labelStyle: Theme.of(context).textTheme.labelMedium,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(
                            kSearchFilterButtonBorderRadius,
                          ),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _SearchFilters extends StatelessWidget {
  const _SearchFilters({
    required this.scopeLabel,
    required this.hasFolderFilter,
    required this.onSelectScope,
    required this.timeRange,
    required this.onTimeRangeChanged,
    required this.onClear,
  });

  final String scopeLabel;
  final bool hasFolderFilter;
  final VoidCallback onSelectScope;
  final SearchTimeRange timeRange;
  final ValueChanged<SearchTimeRange> onTimeRangeChanged;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final hasFilters = hasFolderFilter || timeRange != SearchTimeRange.anyTime;
    final controls = <Widget>[
      SearchFilterButton<void>.action(
        onPressed: onSelectScope,
        icon: const Icon(Icons.folder_outlined, size: 18),
        label: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 220),
          child: Text(scopeLabel, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
      ),
      SearchFilterButton<SearchTimeRange>.menu(
        initialValue: timeRange,
        onSelected: onTimeRangeChanged,
        icon: const Icon(Icons.calendar_today_outlined, size: 17),
        label: Text(_timeRangeLabel(context, timeRange)),
        items: [
          for (final option in SearchTimeRange.values)
            AppMenuItem(
              value: option,
              child: Text(_timeRangeLabel(context, option)),
            ),
        ],
      ),
    ];
    final clearButton = IconButton(
      onPressed: onClear,
      tooltip: context.l10n.clearFilters,
      icon: const Icon(Icons.close_rounded, size: 18),
      visualDensity: VisualDensity.compact,
    );

    return Wrap(
      spacing: 8,
      runSpacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [...controls, if (hasFilters) clearButton],
    );
  }

  String _timeRangeLabel(BuildContext context, SearchTimeRange value) =>
      switch (value) {
        SearchTimeRange.anyTime => context.l10n.timeAny,
        SearchTimeRange.pastWeek => context.l10n.timeWeek,
        SearchTimeRange.pastMonth => context.l10n.timeMonth,
        SearchTimeRange.pastYear => context.l10n.timeYear,
      };
}
