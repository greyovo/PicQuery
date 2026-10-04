import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

final _coverSize = 80.0;

/// A selectable album row used by the album selector.
class AlbumListItem extends StatelessWidget {
  const AlbumListItem({
    super.key,
    required this.album,
    required this.selected,
    required this.onSelected,
  });

  final Album album;
  final bool selected;
  final ValueChanged<bool> onSelected;

  String get _displayName {
    if (album.displayName case final displayName? when displayName.isNotEmpty) {
      return displayName;
    }
    final name = path.basename(album.albumPath);
    return name.isEmpty || name == '.' ? album.albumPath : name;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = context.colors;

    return InkWell(
      onTap: () => onSelected(!selected),
      child: Container(
        constraints: const BoxConstraints(minHeight: 84),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
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
                    context.l10n.photoCount(album.imageCount),
                    style: Theme.of(context).textTheme.bodyMedium
                        ?.copyWith(color: colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Checkbox(
              value: selected,
              onChanged: (value) => onSelected(value ?? false),
            ),
          ],
        ),
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
    final coverImageSize = _coverSize * MediaQuery.devicePixelRatioOf(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(9),
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
                cacheHeight: coverImageSize.toInt(),
                cacheWidth: coverImageSize.toInt(),
                filterQuality: FilterQuality.low,
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
