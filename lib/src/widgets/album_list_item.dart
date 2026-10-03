import 'dart:io';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';
import 'package:path/path.dart' as path;
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

const _rowMinHeight = 84.0;
const _coverSize = 60.0;
const _coverRadius = 9.0;
const _progressSize = 40.0;
const _actionGap = 8.0;

/// Shared album row used by album management and search-scope selection.
class AlbumListItem extends StatelessWidget {
  const AlbumListItem({
    super.key,
    required this.album,
    this.selected = false,
    this.onSelected,
    this.onTap,
    this.progress,
    this.indexedCount,
    this.totalCount,
    this.imagesPerSecond,
    this.onCancelIndexing,
    this.onDelete,
    this.onResumeIndexing,
    this.statusLabel,
  });

  final Album album;
  final bool selected;
  final ValueChanged<bool>? onSelected;
  final VoidCallback? onTap;
  final double? progress;
  final int? indexedCount;
  final int? totalCount;
  final double? imagesPerSecond;
  final VoidCallback? onCancelIndexing;
  final VoidCallback? onDelete;
  final VoidCallback? onResumeIndexing;
  final String? statusLabel;

  String get _displayName {
    final name = path.basename(album.albumPath);
    return name.isEmpty || name == '.' ? album.albumPath : name;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = context.colors;
    final isIndexing = progress != null;

    return InkWell(
      onTap:
          onTap ?? (onSelected != null ? () => onSelected!(!selected) : null),
      child: Container(
        constraints: const BoxConstraints(minHeight: _rowMinHeight),
        padding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 8,
        ),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: colorScheme.outlineVariant.withValues(alpha: 0.42),
            ),
          ),
        ),
        child: Row(
          children: [
            _AlbumCover(path: album.coverPath),
            const SizedBox(width: 12.0),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    _displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    isIndexing
                        ? _indexingSummary(context)
                        : statusLabel ??
                              context.l10n.photoCount(album.imageCount),
                    style: Theme.of(context).textTheme.bodyMedium
                        ?.copyWith(color: colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            const SizedBox(width: _actionGap),
            if (isIndexing)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _ProgressCircle(progress: progress!),
                  const SizedBox(width: _actionGap),
                  IconButton(
                    onPressed: onCancelIndexing,
                    tooltip: context.l10n.cancelIndexing,
                    icon: const Icon(Icons.close),
                    style: IconButton.styleFrom(
                      shape: const CircleBorder(),
                      side: BorderSide(color: colorScheme.outlineVariant),
                    ),
                  ),
                ],
              )
            else if (onSelected != null)
              Checkbox(
                value: selected,
                onChanged: (value) => onSelected!(value ?? false),
              )
            else ...[
              if (onResumeIndexing != null)
                IconButton.outlined(
                  onPressed: onResumeIndexing,
                  icon: const Icon(Icons.play_arrow),
                  tooltip: context.l10n.continueIndexing,
                ),
              if (onDelete != null)
                AppMenuButton<_AlbumAction>(
                  onSelected: (action) {
                    if (action == _AlbumAction.delete) onDelete?.call();
                  },
                  items: [
                    AppMenuItem(
                      value: _AlbumAction.delete,
                      child: Text(context.l10n.removeAlbumIndex),
                    ),
                  ],
                  builder: (context, controller, child) => IconButton(
                    tooltip: context.l10n.albumActions,
                    icon: const Icon(Icons.more_vert),
                    onPressed: () => controller.isOpen
                        ? controller.close()
                        : controller.open(),
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }

  String _indexingSummary(BuildContext context) {
    final current = indexedCount ?? 0;
    final total = totalCount ?? 0;
    final speed = imagesPerSecond ?? 0;
    final speedText = speed >= 10
        ? speed.round().toString()
        : speed.toStringAsFixed(1);
    return context.l10n.indexingProgress(current, total, speedText);
  }
}

enum _AlbumAction { delete }

class _ProgressCircle extends StatelessWidget {
  const _ProgressCircle({required this.progress});

  final double progress;

  @override
  Widget build(BuildContext context) {
    final colorScheme = context.colors;
    return SizedBox(
      width: _progressSize,
      height: _progressSize,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CircularProgressIndicator(
            value: progress,
            strokeWidth: 3,
            backgroundColor: colorScheme.surfaceContainerHighest,
          ),
          Text(
            '${(progress * 100).round()}%',
            style: Theme.of(context).textTheme.labelSmall
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}

class _AlbumCover extends StatelessWidget {
  const _AlbumCover({required this.path});

  final String? path;

  @override
  Widget build(BuildContext context) {
    final colorScheme = context.colors;
    return ClipRRect(
      borderRadius: BorderRadius.circular(_coverRadius),
      child: SizedBox(
        width: _coverSize,
        height: _coverSize,
        child: path == null
            ? ColoredBox(
                color: colorScheme.surfaceContainerHighest,
                child: Icon(
                  Icons.photo_library_outlined,
                  color: colorScheme.onSurfaceVariant,
                ),
              )
            : Image.file(
                File(path!),
                fit: BoxFit.cover,
                filterQuality: FilterQuality.medium,
                errorBuilder: (context, error, stackTrace) => ColoredBox(
                  color: colorScheme.surfaceContainerHighest,
                  child: Icon(
                    Icons.broken_image_outlined,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
      ),
    );
  }
}
