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
import 'package:picquery_app/src/widgets/report_problem_button.dart';

final _log = Logger('search_results_page');

class SearchResultsPage extends WatchingStatefulWidget {
  final List<SearchResult> results;
  final String? query;
  final String? realQuery; // 实际的查询字符串(可能是翻译为英文的)
  final SearchMode searchMode;
  final Object? error;

  const SearchResultsPage({
    super.key,
    required this.results,
    this.query,
    this.realQuery,
    required this.searchMode,
    this.error,
  });

  @override
  State<SearchResultsPage> createState() => _SearchResultsPageState();
}

class _SearchResultsPageState extends State<SearchResultsPage> {
  late final TextEditingController _queryController;
  late List<SearchResult> _results;
  // late String? _realQuery;
  bool _searching = false;
  int _searchRevision = 0;
  Object? _searchError;
  late SearchMode _searchMode;

  @override
  void initState() {
    super.initState();
    _queryController = TextEditingController(text: widget.query ?? '');
    _results = widget.results;
    _searchMode = widget.searchMode;
    _searchError = widget.error;
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
        } catch (error, stackTrace) {
          _log.severe(
            'Translation failed; using the original query.',
            error,
            stackTrace,
          );
          // Fallback to original query on translation failure
        }
      }

      final albumIds = searchManager.selectedAlbumIds.value.toList();
      final results = await searchByTextWithFilters(
        query: searchQuery,
        limit: searchManager.resultLimit.value,
        albumIds: albumIds,
        modifiedAfter: searchManager.timeRange.value.modifiedAfterSeconds(
          DateTime.now(),
        ),
      );
      searchManager.addRecentSearch(query);
      if (mounted) {
        setState(() {
          _results = results;
          _searchMode = SearchMode.text;
          _searchError = null;
          _searchRevision++;
          // _realQuery = searchQuery;
        });
      }
    } catch (e, stackTrace) {
      _log.severe('Text search failed.', e, stackTrace);
      if (mounted) {
        setState(() {
          _results = [];
          _searchError = e;
          _searchMode = SearchMode.text;
          _searchRevision++;
        });
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
      final albumIds = searchManager.selectedAlbumIds.value.toList();
      final results = await pickImageAndSearch(
        context,
        albumIds,
        limit: searchManager.resultLimit.value,
        modifiedAfter: searchManager.timeRange.value.modifiedAfterSeconds(
          DateTime.now(),
        ),
      );
      if (results != null && mounted) {
        setState(() {
          _results = results;
          _searchMode = SearchMode.image;
          _searchError = null;
          _searchRevision++;
          // _realQuery = null;
          _queryController.clear();
        });
      }
    } catch (e, stackTrace) {
      _log.severe('Image search failed.', e, stackTrace);
      if (mounted) {
        setState(() {
          _results = [];
          _searchError = e;
          _searchMode = SearchMode.image;
          _searchRevision++;
        });
        Toast.showMessage(context.l10n.imageSearchFailed(e));
      }
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final indexing = indexingManager;
    watch(indexing);

    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        toolbarHeight: 84,
        titleSpacing: 0,
        title: _buildSearchInput(),
        backgroundColor: context.colors.surface,
        surfaceTintColor: Colors.transparent,
        bottom: _searching
            ? const PreferredSize(
                preferredSize: Size.fromHeight(4),
                child: LinearProgressIndicator(),
              )
            : null,
      ),
      body: Column(
        children: [
          if (indexing.isIndexing)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Text(
                context.l10n.indexingResultsWarning,
                style: Theme.of(context).textTheme.bodySmall
                    ?.copyWith(color: context.colors.onSurfaceVariant),
              ),
            ),
          Expanded(
            child: _results.isEmpty
                ? _buildEmptyState(context)
                : SearchResultGrid(
                    results: _results,
                    onResultOpened: searchManager.addRecentViewedPhoto,
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchInput() {
    return Padding(
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
            style: Theme.of(context).textTheme.titleMedium
                ?.copyWith(color: context.colors.onSurfaceVariant),
          ),
          if (!_searching)
            ReportProblemButton(
              key: ValueKey(_searchRevision),
              source: _searchError == null ? 'empty_search' : 'search_error',
              error: _searchError,
              diagnostics: {
                'search_mode': _searchMode.name,
                'result_count': _results.length,
                'album_filter_count':
                    searchManager.selectedAlbumIds.value.length,
                'result_limit': searchManager.resultLimit.value,
                'indexing_in_progress': indexingManager.isIndexing,
              },
            ),
        ],
      ),
    );
  }
}
