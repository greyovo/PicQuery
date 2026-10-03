import 'dart:io';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/view/image_preview_view.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';

/// Grid display for search results with thumbnails and similarity scores.
class SearchResultGrid extends StatelessWidget {
  final List<SearchResult> results;

  /// Extra top padding so the first rows are not covered by the floating
  /// search bar on the results page.
  final double topPadding;
  final ValueChanged<SearchResult>? onResultOpened;

  const SearchResultGrid({
    super.key,
    required this.results,
    this.topPadding = 8,
    this.onResultOpened,
  });

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final maxCrossAxisExtent = getAdaptiveMaxCrossAxisExtent(screenWidth);

    return GridView.builder(
      padding: EdgeInsets.fromLTRB(8, topPadding, 8, 8),
      gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: maxCrossAxisExtent,
        childAspectRatio: 0.85,
        crossAxisSpacing: 0,
        mainAxisSpacing: 0,
      ),
      itemCount: results.length,
      itemBuilder: (context, index) {
        final result = results[index];
        return _ResultCard(
          result: result,
          results: results,
          index: index,
          onResultOpened: onResultOpened,
        );
      },
    );
  }
}

class _ResultCard extends StatelessWidget {
  final SearchResult result;
  final List<SearchResult> results;
  final int index;
  final ValueChanged<SearchResult>? onResultOpened;
  late final focusNode = FocusNode();
  _ResultCard({
    required this.result,
    required this.results,
    required this.index,
    this.onResultOpened,
  });

  void _openPreview(BuildContext context) {
    focusNode.requestFocus();
    onResultOpened?.call(result);
    showContentInDialogOrPage<void>(
      context: context,
      barrierDismissible: true,
      builder: (context) =>
          ImagePreviewView(results: results, initialIndex: index),
    );
  }

  double getImageHeight(BuildContext context) {
    final height = MediaQuery.of(context).size.width / 3;
    return height;
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: focusNode,
      autofocus: true,
      child: Card(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
        // elevation: 0,
        clipBehavior: Clip.hardEdge,
        margin: EdgeInsets.all(2.5),
        child: InkWell(
          onTap: () => _openPreview(context),
          child: Stack(
            children: [
              Positioned.fill(
                child: Image.file(
                  File(result.filePath),
                  fit: BoxFit.cover,
                  height: getImageHeight(context),
                  cacheHeight: getImageHeight(context).toInt(),
                  errorBuilder: (context, _, __) => Center(
                    child: Icon(
                      Icons.broken_image,
                      size: 48,
                      color: context.colors.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
