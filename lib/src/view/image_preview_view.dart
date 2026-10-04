import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:share_plus/share_plus.dart';
import 'package:open_filex/open_filex.dart';
import 'package:url_launcher/url_launcher.dart';

import 'image_info_bottom_sheet.dart';

class ImagePreviewView extends StatefulWidget {
  final List<SearchResult> results;
  final int initialIndex;

  const ImagePreviewView({
    super.key,
    required this.results,
    required this.initialIndex,
  });

  @override
  State<ImagePreviewView> createState() => _ImagePreviewViewState();
}

class _ImagePreviewViewState extends State<ImagePreviewView> {
  late int _currentIndex = widget.initialIndex;
  late final _pageController = PageController(initialPage: widget.initialIndex);

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  SearchResult get _currentResult => widget.results[_currentIndex];

  bool get _canGoPrevious => _currentIndex > 0;

  bool get _canGoNext => _currentIndex < widget.results.length - 1;

  bool get _isSingleResult => widget.results.length <= 1;

  void _goPrevious() {
    if (!_canGoPrevious) return;
    _pageController.previousPage(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeInOut,
    );
  }

  void _goNext() {
    if (!_canGoNext) return;
    _pageController.nextPage(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeInOut,
    );
  }

  void _showImageInfoModal() {
    showModalBottomSheet(
      context: context,
      builder: (context) => ImageInfoBottomSheet(searchResult: _currentResult),
    );
  }

  Future<void> _shareImage() async {
    final strings = context.l10n;
    try {
      final file = File(_currentResult.filePath);
      if (await file.exists()) {
        await SharePlus.instance.share(
          ShareParams(files: [XFile(_currentResult.filePath)]),
        );
      } else {
        _showError(strings.fileNotFound);
      }
    } catch (e) {
      _showError(strings.shareFailed(e));
    }
  }

  Future<void> _openImage() async {
    final strings = context.l10n;
    try {
      final file = File(_currentResult.filePath);
      if (!await file.exists()) {
        if (mounted) {
          _showError(strings.fileNotFound);
        }
        return;
      }

      if (isDesktop) {
        await OpenFilex.open(_currentResult.filePath);
      } else {
        final uri = Uri.file(_currentResult.filePath);
        if (await canLaunchUrl(uri)) {
          await launchUrl(uri);
        } else {
          await OpenFilex.open(_currentResult.filePath);
        }
      }
    } catch (e) {
      if (mounted) {
        _showError(strings.openFailed(e));
      }
    }
  }

  void _showError(String message) {
    Toast.showMessage(message);
  }

  void _handleKeyEvent(KeyEvent event) {
    if (event is! KeyDownEvent) return;

    if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
      _goPrevious();
    } else if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
      _goNext();
    } else if (event.logicalKey == LogicalKeyboardKey.escape) {
      Navigator.of(context, rootNavigator: true).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      autofocus: true,
      onKeyEvent: (_, event) {
        _handleKeyEvent(event);
        if ([
          LogicalKeyboardKey.arrowLeft,
          LogicalKeyboardKey.arrowRight,
          LogicalKeyboardKey.escape,
        ].contains(event.logicalKey)) {
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: _buildBody(),
    );
  }

  Scaffold _buildBody() {
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
        flexibleSpace: SafeArea(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: 42),
            child: ListTile(
              title: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Expanded(
                    child: Text(
                      _currentResult.fileName,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      maxLines: 1,
                    ),
                  ),
                ],
              ),
              subtitle: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text('${_currentIndex + 1} / ${widget.results.length}'),
                ],
              ),
            ),
          ),
        ),
        actions: [
          if (context.isLargeScreen)
            IconButton(
              onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
              icon: const Icon(Icons.close),
            ),
          SizedBox.square(dimension: 8),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: PageView.builder(
              controller: _pageController,
              itemCount: widget.results.length,
              onPageChanged: (index) {
                setState(() {
                  _currentIndex = index;
                });
              },
              itemBuilder: (context, index) {
                final result = widget.results[index];
                return Center(
                  child: Image.file(
                    key: ValueKey(result.filePath),
                    File(result.filePath),
                    width: MediaQuery.of(context).size.width * 1.1,
                    cacheWidth: (MediaQuery.of(context).size.width * 1.1)
                        .toInt(),
                    fit: BoxFit.contain,
                    errorBuilder: (context, _, __) => Center(
                      child: Icon(
                        Icons.broken_image,
                        size: 64,
                        color: context.colors.onSurfaceVariant,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          _buildBottomToolbar(),
        ],
      ),
    );
  }

  Container _buildBottomToolbar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: context.colors.outlineVariant)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (isDesktop && !_isSingleResult) ...[
              _ToolbarButton(
                icon: Icons.arrow_back,
                label: context.l10n.previous,
                onPressed: _canGoPrevious ? _goPrevious : null,
              ),
              const SizedBox(width: 16),
              _ToolbarButton(
                icon: Icons.arrow_forward,
                label: context.l10n.next,
                onPressed: _canGoNext ? _goNext : null,
              ),
              const SizedBox(width: 24),
            ],
            _ToolbarButton(
              icon: Icons.info_outline,
              label: context.l10n.info,
              onPressed: _showImageInfoModal,
            ),
            const SizedBox(width: 24),
            _ToolbarButton(
              icon: Icons.share,
              label: context.l10n.share,
              onPressed: _shareImage,
            ),
            const SizedBox(width: 24),
            _ToolbarButton(
              icon: Icons.open_in_new,
              label: context.l10n.open,
              onPressed: _openImage,
            ),
          ],
        ),
      ),
    );
  }
}

class _ToolbarButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;

  const _ToolbarButton({
    required this.icon,
    required this.label,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          onPressed: onPressed,
          icon: Icon(icon),
          color: onPressed != null
              ? null
              : context.colors.onSurface.withValues(alpha: 0.38),
          iconSize: 24,
        ),
        Text(
          label,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
            color: onPressed != null
                ? null
                : context.colors.onSurface.withValues(alpha: 0.38),
          ),
        ),
      ],
    );
  }
}
