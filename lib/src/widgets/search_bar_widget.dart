import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:picquery_app/src/widgets/add_album_entry.dart';
import 'package:picquery_app/src/widgets/search_input_card.dart';
import 'package:picquery_app/src/widgets/search_scope_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';

class QuerySearchBar extends StatelessWidget {
  const QuerySearchBar({
    super.key,
    required this.queryController,
    required this.onSearch,
    required this.onImageUpload,
    required this.isCustomScope,
    required this.scopeSubtitle,
    required this.onScopeSelected,
    this.recentSearches = const [],
    this.onRecentSearchSelected,
    this.onClear,
  });

  final TextEditingController queryController;
  final VoidCallback onSearch;
  final VoidCallback onImageUpload;
  final bool isCustomScope;
  final String? scopeSubtitle;
  final ValueChanged<String> onScopeSelected;
  final List<String> recentSearches;
  final ValueChanged<String>? onRecentSearchSelected;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final width = math.min(MediaQuery.sizeOf(context).width, 600.0);
    return Container(
      width: width,
      padding: const EdgeInsets.symmetric(horizontal: 16.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // LOGO row
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Text(
              "PicQuery",
              style: Theme.of(context).textTheme.headlineLarge!.copyWith(
                color: context.colors.primary,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.5,
              ),
            ),
          ),
          // Search input row
          Hero(
            tag: kSearchInputHeroTag,
            createRectTween: (begin, end) => RectTween(begin: begin, end: end),
            child: SearchInputCard(
              queryController: queryController,
              onSearch: onSearch,
              onImageUpload: onImageUpload,
            ),
          ),
          // Recent searches
          if (recentSearches.isNotEmpty && onRecentSearchSelected != null)
            Padding(
              padding: const EdgeInsets.only(top: 16, left: 16, right: 16),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final query in recentSearches)
                      ActionChip(
                        labelStyle: TextStyle(fontSize: 12),
                        labelPadding: EdgeInsets.symmetric(
                          horizontal: 2,
                          vertical: 0,
                        ),
                        label: Text(
                          query,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onPressed: () => onRecentSearchSelected!(query),
                      ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 12),
          // Divider(indent: 16, endIndent: 16),
          SearchScopeDisplay(
            isCustom: isCustomScope,
            scopeSubtitle: scopeSubtitle,
            onScopeSelected: onScopeSelected,
          ),
          // const SizedBox(height: 8),
          const AddAlbumEntry(),
          const SizedBox(height: 6),
        ],
      ),
    );
  }
}
