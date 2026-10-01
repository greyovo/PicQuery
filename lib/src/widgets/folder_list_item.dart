import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

/// Shared album row used by album management and search-scope selection.
class FolderListItem extends StatelessWidget {
  const FolderListItem({
    super.key,
    required this.folder,
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
    this.isGridCard = false,
  });

  final Folder folder;
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
  final bool isGridCard;

  String get _displayName {
    final name = path.basename(folder.folderPath);
    return name.isEmpty || name == '.' ? folder.folderPath : name;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = context.colors;
    final isIndexing = progress != null;

    if (isGridCard) {
      return _buildGridCard(context, colorScheme, isIndexing);
    }

    return InkWell(
      onTap:
          onTap ?? (onSelected != null ? () => onSelected!(!selected) : null),
      borderRadius: isGridCard ? BorderRadius.circular(12) : null,
      child: Container(
        constraints: const BoxConstraints(minHeight: 84),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isGridCard ? colorScheme.surfaceContainerLow : null,
          borderRadius: isGridCard ? BorderRadius.circular(12) : null,
          border: isGridCard
              ? Border.all(
                  color: colorScheme.outlineVariant.withValues(alpha: 0.42),
                )
              : Border(
                  bottom: BorderSide(
                    color: colorScheme.outlineVariant.withValues(alpha: 0.42),
                  ),
                ),
        ),
        child: Row(
          children: [
            _AlbumCover(path: folder.coverPath),
            const SizedBox(width: 12),
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
                              context.l10n.photoCount(folder.imageCount),
                    style: Theme.of(context).textTheme.bodyMedium
                        ?.copyWith(color: colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            if (isIndexing)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _ProgressCircle(progress: progress!),
                  const SizedBox(width: 8),
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
                PopupMenuButton<_AlbumAction>(
                  tooltip: context.l10n.albumActions,
                  icon: const Icon(Icons.more_vert),
                  onSelected: (action) {
                    if (action == _AlbumAction.delete) onDelete!();
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: _AlbumAction.delete,
                      child: Text(context.l10n.removeAlbumIndex),
                    ),
                  ],
                ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildGridCard(
    BuildContext context,
    ColorScheme colorScheme,
    bool isIndexing,
  ) {
    final percentage = ((progress ?? 0) * 100).round();
    return Card(
      margin: EdgeInsets.zero,
      elevation: 0,
      color: colorScheme.surfaceContainerLow,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: BorderSide(
          color: isIndexing
              ? colorScheme.primary.withValues(alpha: .28)
              : colorScheme.outlineVariant.withValues(alpha: .55),
        ),
      ),
      child: Stack(
        children: [
          InkWell(
            onTap: onTap,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                AspectRatio(
                  aspectRatio: 1,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      _AlbumHero(path: folder.coverPath),
                      if (isIndexing)
                        Positioned(
                          left: 10,
                          bottom: 10,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 5,
                            ),
                            decoration: BoxDecoration(
                              color: colorScheme.primaryContainer,
                              borderRadius: BorderRadius.circular(99),
                            ),
                            child: Text(
                              context.l10n.indexingPercent(percentage),
                              style: Theme.of(context).textTheme.labelLarge
                                  ?.copyWith(
                                    color: colorScheme.onPrimaryContainer,
                                    fontWeight: FontWeight.w700,
                                  ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 8, 8, 4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.w800),
                      ),
                      const SizedBox(height: 3),
                      if (isIndexing) ...[
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                _indexingSummary(context),
                                style: TextStyle(
                                  color: colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ),
                            Text(
                              '$percentage%',
                              style: Theme.of(context).textTheme.labelLarge
                                  ?.copyWith(
                                    color: colorScheme.primary,
                                    fontWeight: FontWeight.w800,
                                  ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 7),
                        LinearProgressIndicator(
                          value: progress,
                          minHeight: 7,
                          borderRadius: BorderRadius.circular(99),
                        ),
                        const SizedBox(height: 6),
                        Align(
                          alignment: Alignment.centerRight,
                          child: IconButton.filledTonal(
                            onPressed: onCancelIndexing,
                            tooltip: context.l10n.stop,
                            icon: const Icon(Icons.stop_rounded, size: 18),
                            style: IconButton.styleFrom(
                              minimumSize: const Size.square(36),
                              maximumSize: const Size.square(36),
                              padding: EdgeInsets.zero,
                            ),
                          ),
                        ),
                      ] else
                        Row(
                          children: [
                            Icon(
                              folder.isIndexComplete
                                  ? Icons.check_circle_rounded
                                  : Icons.pending_outlined,
                              size: 18,
                              color: folder.isIndexComplete
                                  ? colorScheme.tertiary
                                  : colorScheme.primary,
                            ),
                            const SizedBox(width: 7),
                            Expanded(
                              child: Text(
                                statusLabel ??
                                    context.l10n.photoCount(folder.imageCount),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ),
                            if (onResumeIndexing != null)
                              IconButton.filledTonal(
                                onPressed: onResumeIndexing,
                                tooltip: folder.isIndexComplete
                                    ? context.l10n.update
                                    : context.l10n.continueAction,
                                icon: const Icon(
                                  Icons.refresh_rounded,
                                  size: 18,
                                ),
                                style: IconButton.styleFrom(
                                  minimumSize: const Size.square(32),
                                  maximumSize: const Size.square(32),
                                  padding: EdgeInsets.zero,
                                ),
                              ),
                          ],
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Positioned(
            top: 6,
            right: 6,
            child: MenuAnchor(
              builder: (context, controller, child) => IconButton.filledTonal(
                onPressed: () =>
                    controller.isOpen ? controller.close() : controller.open(),
                tooltip: context.l10n.albumActions,
                icon: const Icon(Icons.more_vert, size: 18),
                style: IconButton.styleFrom(
                  minimumSize: const Size.square(34),
                  maximumSize: const Size.square(34),
                  padding: EdgeInsets.zero,
                ),
              ),
              menuChildren: [
                if (onDelete != null)
                  MenuItemButton(
                    onPressed: onDelete,
                    leadingIcon: const Icon(Icons.delete_outline),
                    child: Text(context.l10n.removeAlbumIndex),
                  ),
              ],
            ),
          ),
        ],
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
      width: 40,
      height: 40,
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
      borderRadius: BorderRadius.circular(9),
      child: SizedBox(
        width: 60,
        height: 60,
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

class _AlbumHero extends StatelessWidget {
  const _AlbumHero({required this.path});

  final String? path;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    if (path == null) {
      return ColoredBox(
        color: colors.surfaceContainerHighest,
        child: Icon(
          Icons.photo_library_outlined,
          size: 52,
          color: colors.onSurfaceVariant,
        ),
      );
    }
    return Image.file(
      File(path!),
      fit: BoxFit.cover,
      filterQuality: FilterQuality.medium,
      errorBuilder: (context, error, stackTrace) => ColoredBox(
        color: colors.surfaceContainerHighest,
        child: Icon(
          Icons.broken_image_outlined,
          size: 52,
          color: colors.onSurfaceVariant,
        ),
      ),
    );
  }
}
