import 'dart:io';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';
import 'package:path/path.dart' as path;
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

const albumGridCoverAspectRatio = 1.15;

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
    this.isUpdateAvailable = false,
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
  final bool isUpdateAvailable;
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
                AppMenuButton<_AlbumAction>(
                  onSelected: (action) {
                    if (action == _AlbumAction.delete) onDelete!();
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

  Widget _buildGridCard(
    BuildContext context,
    ColorScheme colorScheme,
    bool isIndexing,
  ) {
    final coverBorderColor = isIndexing
        ? colorScheme.primary.withValues(alpha: .45)
        : colorScheme.outlineVariant.withValues(alpha: .7);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: colorScheme.shadow.withValues(alpha: .16),
                blurRadius: 8,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          foregroundDecoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: coverBorderColor),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: GestureDetector(
              onTap: onTap,
              child: AspectRatio(
                aspectRatio: albumGridCoverAspectRatio,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    _AlbumHero(path: folder.coverPath),
                    if (isIndexing) ...[
                      ColoredBox(color: Colors.black.withValues(alpha: .3)),
                      Center(
                        child: SizedBox.square(
                          dimension: 52,
                          child: CircularProgressIndicator(
                            value: progress,
                            strokeWidth: 4,
                            color: Colors.white,
                            backgroundColor: Colors.white.withValues(alpha: .3),
                          ),
                        ),
                      ),
                    ] else if (onResumeIndexing != null)
                      Positioned(
                        right: 8,
                        bottom: 8,
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
                            minimumSize: const Size.square(32),
                            maximumSize: const Size.square(32),
                            padding: EdgeInsets.zero,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: onTap,
                  child: Padding(
                    padding: const EdgeInsets.only(left: 4, top: 1),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleSmall
                              ?.copyWith(fontWeight: FontWeight.normal),
                        ),
                        const SizedBox(height: 2),
                        if (isIndexing)
                          Text(
                            _gridIndexingSummary(),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: colorScheme.onSurfaceVariant),
                          )
                        else
                          Row(
                            children: [
                              if (!folder.isIndexComplete ||
                                  isUpdateAvailable) ...[
                                Icon(
                                  folder.isIndexComplete
                                      ? Icons.arrow_upward_rounded
                                      : Icons.pause_rounded,
                                  size: 14,
                                  color: colorScheme.primary,
                                ),
                                const SizedBox(width: 5),
                              ],
                              Expanded(
                                child: Text(
                                  statusLabel ??
                                      context.l10n.photoCount(
                                        folder.imageCount,
                                      ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(
                                        color: colorScheme.onSurfaceVariant,
                                      ),
                                ),
                              ),
                            ],
                          ),
                      ],
                    ),
                  ),
                ),
              ),
              AppMenuButton<_AlbumAction>(
                onSelected: (action) {
                  if (action == _AlbumAction.delete) onDelete?.call();
                },
                builder: (context, controller, child) => IconButton(
                  onPressed: () => controller.isOpen
                      ? controller.close()
                      : controller.open(),
                  tooltip: context.l10n.albumActions,
                  icon: const Icon(Icons.more_vert, size: 16),
                  style: IconButton.styleFrom(
                    minimumSize: const Size.square(28),
                    maximumSize: const Size.square(28),
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
              ),
            ],
          ),
        ),
      ],
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

  String _gridIndexingSummary() {
    final current = indexedCount ?? 0;
    final total = totalCount ?? 0;
    final speed = imagesPerSecond ?? 0;
    final speedText = speed >= 10
        ? speed.round().toString()
        : speed.toStringAsFixed(1);
    return '$current/$total ($speedText P/s)';
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
      return ColoredBox(color: colors.surfaceContainerHighest);
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
