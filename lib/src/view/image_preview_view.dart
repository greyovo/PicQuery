import 'dart:io';

import 'package:path/path.dart' as p;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:share_plus/share_plus.dart';
import 'package:picquery_app/src/utils/image_opener.dart';

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
  bool _isScrolling = false;
  final Map<String, String> _albumNames = {};

  @override
  void initState() {
    super.initState();
    _loadAlbumName();
  }

  Future<void> _loadAlbumName() async {
    final path = _currentResult.filePath;
    if (_albumNames.containsKey(path)) return;
    final album = await getImageAlbum(filePath: path);
    if (!mounted) return;
    setState(() {
      _albumNames[path] = album?.$2?.trim().isNotEmpty == true
          ? album!.$2!
          : album != null
          ? p.basename(album.$1)
          : isMobile
          ? context.l10n.unknown
          : p.basename(p.dirname(path));
    });
  }

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
      if (!Platform.isIOS && !await file.exists()) {
        if (mounted) {
          _showError(strings.fileNotFound);
        }
        return;
      }

      await openImageExternally(file.path);
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
    final mediaQuery = MediaQuery.of(context);
    final imageWidth =
        mediaQuery.size.width * mediaQuery.devicePixelRatio * 1.1;
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
        centerTitle: true,
        title: Text(
          _albumNames[_currentResult.filePath] ??
              (isMobile ? '' : p.basename(p.dirname(_currentResult.filePath))),
          overflow: TextOverflow.ellipsis,
          maxLines: 1,
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
            child: Stack(
              alignment: Alignment.bottomCenter,
              children: [
                NotificationListener<ScrollNotification>(
                  onNotification: (notification) {
                    if (notification.depth != 0) return false;
                    if (notification is ScrollStartNotification) {
                      setState(() => _isScrolling = true);
                    } else if (notification is ScrollEndNotification) {
                      setState(() => _isScrolling = false);
                    }
                    return false;
                  },
                  child: PageView.builder(
                    controller: _pageController,
                    itemCount: widget.results.length,
                    onPageChanged: (index) {
                      setState(() => _currentIndex = index);
                      _loadAlbumName();
                    },
                    itemBuilder: (context, index) {
                      final result = widget.results[index];
                      return Center(
                        child: Image.file(
                          key: ValueKey(result.filePath),
                          File(result.filePath),
                          width: imageWidth,
                          cacheWidth: imageWidth.toInt(),
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
                Positioned(
                  bottom: 20,
                  child: IgnorePointer(
                    child: AnimatedOpacity(
                      opacity: _isScrolling ? 1 : 0,
                      duration: const Duration(milliseconds: 200),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.55),
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Text(
                          '${_currentIndex + 1} / ${widget.results.length}',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
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
