import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';

const albumGridCoverAspectRatio = 1.0;
const albumGridDetailsHeight = 60.0;
const albumGridCoverWidth = 120;

const _cardRadius = 16.0;
const _cardShadowBlur = 8.0;
const _cardShadowOffset = Offset(0, 3);
const _coverProgressSize = 52.0;
const _coverActionInset = 8.0;
const _coverActionSize = 32.0;
const _detailsGap = 4.0;
const _statusIconSize = 14.0;
const _menuButtonSize = 28.0;

class AlbumGridCard extends StatelessWidget {
  const AlbumGridCard({
    super.key,
    required this.album,
    this.onTap,
    this.progress,
    this.indexedCount,
    this.totalCount,
    this.imagesPerSecond,
    this.onDelete,
    this.onResumeIndexing,
    this.statusLabel,
    this.isUpdateAvailable = false,
  });

  final Album album;
  final VoidCallback? onTap;
  final double? progress;
  final int? indexedCount;
  final int? totalCount;
  final double? imagesPerSecond;
  final VoidCallback? onDelete;
  final VoidCallback? onResumeIndexing;
  final String? statusLabel;
  final bool isUpdateAvailable;

  String get _displayName {
    if (album.displayName case final displayName? when displayName.isNotEmpty) {
      return displayName;
    }
    final name = path.basename(album.albumPath);
    return name.isEmpty || name == '.' ? album.albumPath : name;
  }

  bool get _isIndexing => progress != null;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _AlbumGridCover(
          coverPath: album.coverPath,
          progress: progress,
          onTap: onTap,
          onResumeIndexing: onResumeIndexing,
          isUpdateAvailable: isUpdateAvailable,
        ),
        const SizedBox(height: _detailsGap),
        Expanded(
          child: _AlbumGridDetails(
            displayName: _displayName,
            statusLabel:
                statusLabel ?? context.l10n.photoCount(album.imageCount),
            isIndexComplete: album.isIndexComplete,
            isIndexing: _isIndexing,
            indexingSummary: _indexingSummary,
            isUpdateAvailable: isUpdateAvailable,
            onTap: onTap,
            onDelete: onDelete,
          ),
        ),
      ],
    );
  }

  String get _indexingSummary {
    final current = indexedCount ?? 0;
    final total = totalCount ?? 0;
    final speed = imagesPerSecond ?? 0;
    final speedText = speed >= 10
        ? speed.round().toString()
        : speed.toStringAsFixed(1);
    return '$current/$total ($speedText P/s)';
  }
}

class _AlbumGridCover extends StatelessWidget {
  const _AlbumGridCover({
    required this.coverPath,
    required this.progress,
    required this.onTap,
    required this.onResumeIndexing,
    required this.isUpdateAvailable,
  });

  final String? coverPath;
  final double? progress;
  final VoidCallback? onTap;
  final VoidCallback? onResumeIndexing;
  final bool isUpdateAvailable;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final radius = BorderRadius.circular(_cardRadius);
    return Container(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: .16),
            blurRadius: _cardShadowBlur,
            offset: _cardShadowOffset,
          ),
        ],
      ),
      foregroundDecoration: BoxDecoration(borderRadius: radius),
      child: ClipRRect(
        borderRadius: radius,
        child: GestureDetector(
          onTap: onTap,
          child: AspectRatio(
            aspectRatio: albumGridCoverAspectRatio,
            child: Stack(
              fit: StackFit.expand,
              children: [
                _AlbumHero(path: coverPath),
                if (progress != null)
                  Center(
                    child: SizedBox.square(
                      dimension: _coverProgressSize,
                      child: CircularProgressIndicator(
                        value: progress,
                        strokeWidth: 4,
                        color: colors.primary,
                      ),
                    ),
                  )
                else if (onResumeIndexing != null)
                  Positioned(
                    right: _coverActionInset,
                    bottom: _coverActionInset,
                    child: IconButton.filledTonal(
                      onPressed: onResumeIndexing,
                      tooltip: isUpdateAvailable
                          ? context.l10n.update
                          : context.l10n.continueAction,
                      icon: Icon(
                        isUpdateAvailable
                            ? Icons.arrow_upward_rounded
                            : Icons.play_arrow_rounded,
                        size: 18,
                      ),
                      style: IconButton.styleFrom(
                        minimumSize: const Size.square(_coverActionSize),
                        maximumSize: const Size.square(_coverActionSize),
                        padding: EdgeInsets.zero,
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
}

class _AlbumGridDetails extends StatelessWidget {
  const _AlbumGridDetails({
    required this.displayName,
    required this.statusLabel,
    required this.isIndexComplete,
    required this.isIndexing,
    required this.indexingSummary,
    required this.isUpdateAvailable,
    required this.onTap,
    required this.onDelete,
  });

  final String displayName;
  final String statusLabel;
  final bool isIndexComplete;
  final bool isIndexing;
  final String indexingSummary;
  final bool isUpdateAvailable;
  final VoidCallback? onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, top: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: onTap,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 2),
                  if (isIndexing)
                    _StatusText(
                      text: indexingSummary,
                      style: Theme.of(context).textTheme.bodyMedium,
                    )
                  else
                    _AlbumStatus(
                      label: statusLabel,
                      showIcon: !isIndexComplete || isUpdateAvailable,
                      isUpdateAvailable: isUpdateAvailable,
                    ),
                ],
              ),
            ),
          ),
          _AlbumActionsMenu(onDelete: onDelete),
        ],
      ),
    );
  }
}

class _AlbumStatus extends StatelessWidget {
  const _AlbumStatus({
    required this.label,
    required this.showIcon,
    required this.isUpdateAvailable,
  });

  final String label;
  final bool showIcon;
  final bool isUpdateAvailable;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        if (showIcon) ...[
          Icon(
            isUpdateAvailable
                ? Icons.arrow_upward_rounded
                : Icons.pause_rounded,
            size: _statusIconSize,
            color: context.colors.primary,
          ),
          const SizedBox(width: 5),
        ],
        Expanded(
          child: _StatusText(
            text: label,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
      ],
    );
  }
}

class _StatusText extends StatelessWidget {
  const _StatusText({required this.text, required this.style});

  final String text;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: style?.copyWith(color: context.colors.onSurfaceVariant),
    );
  }
}

class _AlbumActionsMenu extends StatelessWidget {
  const _AlbumActionsMenu({required this.onDelete});

  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    return AppMenuButton<_AlbumAction>(
      onSelected: (_) => onDelete?.call(),
      builder: (context, controller, child) => IconButton(
        onPressed: () =>
            controller.isOpen ? controller.close() : controller.open(),
        tooltip: context.l10n.albumActions,
        icon: const Icon(Icons.more_vert, size: 16),
        style: IconButton.styleFrom(
          minimumSize: const Size.square(_menuButtonSize),
          maximumSize: const Size.square(_menuButtonSize),
          padding: EdgeInsets.zero,
        ),
      ),
      items: [
        if (onDelete != null)
          AppMenuItem(
            value: _AlbumAction.delete,
            leadingIcon: const Icon(Icons.delete_outline),
            child: Text(context.l10n.removeAlbumIndex),
          ),
      ],
    );
  }
}

enum _AlbumAction { delete }

class _AlbumHero extends StatelessWidget {
  const _AlbumHero({required this.path});

  final String? path;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    if (path == null) return ColoredBox(color: colors.primaryContainer);
    return Image.file(
      File(path!),
      cacheHeight: albumGridCoverWidth,
      cacheWidth: albumGridCoverWidth,
      fit: BoxFit.cover,
      filterQuality: FilterQuality.medium,
      errorBuilder: (context, error, stackTrace) => ColoredBox(
        color: colors.surfaceContainerHighest,
        child: Icon(
          Icons.broken_image_outlined,
          size: _coverProgressSize,
          color: colors.onSurfaceVariant,
        ),
      ),
    );
  }
}
