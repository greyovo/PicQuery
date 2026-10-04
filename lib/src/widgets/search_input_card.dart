import 'package:flutter/material.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

const String kSearchInputHeroTag = 'search_query_input';
const double kSearchInputMaxWidth = 768;
const BorderRadius _largeBorderRadius = BorderRadius.all(Radius.circular(16));

/// The search input card used on both the search tab and the search results
/// page. Wrapped in a [Hero] with [kSearchInputHeroTag] in both places.
class SearchInputCard extends StatelessWidget {
  const SearchInputCard({
    super.key,
    required this.queryController,
    required this.onSearch,
    required this.onImageUpload,
  });

  final TextEditingController queryController;
  final VoidCallback onSearch;
  final VoidCallback onImageUpload;

  @override
  Widget build(BuildContext context) {
    return TextField(
      autofocus: false,
      controller: queryController,
      maxLength: 77,
      maxLines: 1,
      decoration: InputDecoration(
        hintText: context.l10n.searchPhotosHint,
        counter: const SizedBox.shrink(),
        hintStyle: TextStyle(
          color: context.colors.onSurfaceVariant.withValues(alpha: 0.6),
        ),
        suffixIcon: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              onPressed: onImageUpload,
              icon: const Icon(Icons.image_outlined),
              tooltip: context.l10n.searchByImage,
              style: IconButton.styleFrom(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
            const SizedBox(height: 24, child: VerticalDivider()),
            Padding(
              padding: const EdgeInsets.only(right: 4),
              child: context.isLargeScreen
                  ? FilledButton.icon(
                      onPressed: onSearch,
                      icon: const Icon(Icons.search_rounded, size: 24),
                      label: Text(context.l10n.search),
                      style: FilledButton.styleFrom(
                        maximumSize: const Size(124, 48),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                    )
                  : Tooltip(
                      message: context.l10n.search,
                      child: FilledButton(
                        onPressed: onSearch,
                        style: FilledButton.styleFrom(
                          minimumSize: const Size.square(42),
                          padding: EdgeInsets.zero,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                        child: const Icon(Icons.search_rounded, size: 24),
                      ),
                    ),
            ),
          ],
        ),
        filled: true,
        fillColor: context.colors.surface.withValues(alpha: 0.72),
        border: OutlineInputBorder(
          borderRadius: _largeBorderRadius,
          borderSide: BorderSide(color: context.colors.outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: _largeBorderRadius,
          borderSide: BorderSide(color: context.colors.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: _largeBorderRadius,
          borderSide: BorderSide(color: context.colors.primary, width: 1.5),
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
      ),
      onSubmitted: (_) => onSearch(),
    );
  }
}
