import 'package:flutter/material.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

const _maxWidth = 440.0;
const _margin = 28.0;
const _padding = 36.0;
const _iconRadius = 38.0;

/// Guides users to add their first album when no searchable index exists.
class EmptyAlbumsGuide extends StatelessWidget {
  const EmptyAlbumsGuide({super.key, required this.onAdd});

  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        constraints: const BoxConstraints(maxWidth: _maxWidth),
        margin: const EdgeInsets.all(_margin),
        padding: const EdgeInsets.all(_padding),
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(28)),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircleAvatar(
              radius: _iconRadius,
              backgroundColor: context.colors.primaryContainer,
              foregroundColor: context.colors.onPrimaryContainer,
              child: const Icon(Icons.photo_library_outlined, size: 36),
            ),
            const SizedBox(height: 22),
            Text(
              isDesktop
                  ? context.l10n.selectOrDropFolder
                  : context.l10n.buildPhotoLibrary,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            Text(
              context.l10n.buildPhotoLibraryDescription,
              textAlign: TextAlign.center,
              style: TextStyle(color: context.colors.onSurfaceVariant),
            ),
            const SizedBox(height: 22),
            FilledButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: Text(context.l10n.addFirstAlbum),
            ),
          ],
        ),
      ),
    );
  }
}
