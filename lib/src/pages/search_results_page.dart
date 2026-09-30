import 'package:flutter/material.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/search_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:watch_it/watch_it.dart';
import 'package:picquery_app/src/models/search_state.dart';
import 'package:picquery_app/src/utils/image_search_picker.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/widgets/search_input_card.dart';
import 'package:picquery_app/src/widgets/search_result_grid.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

final _log = Logger('search_results_page');

class SearchResultsPage extends WatchingStatefulWidget {
  final List<SearchResult> results;
  final String? query;
  final String? realQuery; // 实际的查询字符串(可能是翻译为英文的)
  final SearchMode searchMode;

  const SearchResultsPage({
    super.key,
    required this.results,
    this.query,
    this.realQuery,
    required this.searchMode,
  });

  @override
  State<SearchResultsPage> createState() => _SearchResultsPageState();
}

class _SearchResultsPageState extends State<SearchResultsPage> {
  late final TextEditingController _queryController;
  late List<SearchResult> _results;
  // late String? _realQuery;
  bool _searching = false;

  @override
  void initState() {
    super.initState();
    _queryController = TextEditingController(text: widget.query ?? '');
    _results = widget.results;
    // _realQuery = widget.realQuery;
  }

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  bool _containsChinese(String text) => RegExp(r'[一-鿿]').hasMatch(text);

  Future<void> _searchByText() async {
    final query = _queryController.text.trim();
    if (query.isEmpty || _searching) return;

    setState(() => _searching = true);
    try {
      var searchQuery = query;
      if (_containsChinese(query)) {
        try {
          final startTime = DateTime.now();
          searchQuery = await translateZhToEn(sentence: query);
          final costTime = DateTime.now().difference(startTime).inMilliseconds;
          _log.info('Translation completed in $costTime ms.');
        } catch (_) {
          _log.severe('Translation failed; using the original query.');
          // Fallback to original query on translation failure
        }
      }

      final folderIds = searchManager.selectedFolderIds.value.toList();
      final results = await searchByTextWithFilters(
        query: searchQuery,
        limit: searchManager.resultLimit.value,
        folderIds: folderIds,
        modifiedAfter: searchManager.timeRange.value.modifiedAfterSeconds(
          DateTime.now(),
        ),
      );
      searchManager.addRecentSearch(query);
      if (mounted) {
        setState(() {
          _results = results;
          // _realQuery = searchQuery;
        });
      }
    } catch (e) {
      _log.severe('Text search failed.');
      if (mounted) {
        Toast.showMessage(context.l10n.searchFailed(e));
      }
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _searchByImage() async {
    if (_searching) return;
    setState(() => _searching = true);
    try {
      final folderIds = searchManager.selectedFolderIds.value.toList();
      final results = await pickImageAndSearch(
        context,
        folderIds,
        limit: searchManager.resultLimit.value,
        modifiedAfter: searchManager.timeRange.value.modifiedAfterSeconds(
          DateTime.now(),
        ),
      );
      if (results != null && mounted) {
        setState(() {
          _results = results;
          // _realQuery = null;
          _queryController.clear();
        });
      }
    } catch (e) {
      _log.severe('Image search failed.');
      if (mounted) {
        Toast.showMessage(context.l10n.imageSearchFailed(e));
      }
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final topInset = MediaQuery.paddingOf(context).top + 84.0;
    final indexing = indexingManager;
    watch(indexing);

    return Scaffold(
      body: Stack(
        children: [
          // Result content, pushed down far enough that the first rows are not
          // covered by the floating search bar.
          if (_results.isEmpty)
            Padding(
              padding: EdgeInsets.only(top: topInset),
              child: _buildEmptyState(context),
            )
          else
            SearchResultGrid(
              results: _results,
              topPadding: topInset,
              onResultOpened: searchManager.addRecentViewedPhoto,
            ),
          // Floating search bar over the results.
          Positioned(top: 0, left: 0, right: 0, child: _buildSearchInput()),
          if (indexing.isIndexing)
            Positioned(
              top: topInset,
              left: 0,
              right: 0,
              child: Center(
                child: Text(
                  context.l10n.indexingResultsWarning,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: context.colors.onSurfaceVariant,
                  ),
                ),
              ),
            ),
          if (_searching)
            const Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: LinearProgressIndicator(),
            ),
        ],
      ),
    );
  }

  Widget _buildSearchInput() {
    return SafeArea(
      child: Container(
        color: context.colors.surface.withValues(alpha: 0.88),
        padding: const EdgeInsets.only(left: 8, right: 10, top: 12, bottom: 12),
        child: Center(
          child: ConstrainedBox(
            // 48px for the back button + the 568px search input used on the
            // search page. Centering this group also centers the button.
            constraints: const BoxConstraints(
              maxWidth: kSearchInputMaxWidth + 48,
            ),
            child: Row(
              children: [
                BackButton(onPressed: () => Navigator.of(context).maybePop()),
                Expanded(
                  child: Hero(
                    tag: kSearchInputHeroTag,
                    createRectTween: (begin, end) =>
                        RectTween(begin: begin, end: end),
                    child: SearchInputCard(
                      queryController: _queryController,
                      onSearch: _searchByText,
                      onImageUpload: _searchByImage,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.search_off, size: 64, color: context.colors.outline),
          const SizedBox(height: 16),
          Text(
            context.l10n.noMatchesFound,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: context.colors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
