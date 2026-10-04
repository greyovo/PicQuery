import 'dart:async';
import 'dart:io';

import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:picquery_app/src/engine/api.dart' as api;
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/utils/path_selector.dart';

/// Accepts one desktop folder only while the main tab route is visible.
class DesktopAlbumDropTarget extends StatefulWidget {
  const DesktopAlbumDropTarget({
    super.key,
    required this.onFolderDropped,
    required this.child,
  });

  final Future<void> Function(PathSelectionResult) onFolderDropped;
  final Widget child;

  @override
  State<DesktopAlbumDropTarget> createState() => _DesktopAlbumDropTargetState();
}

class _DesktopAlbumDropTargetState extends State<DesktopAlbumDropTarget> {
  bool _dragging = false;
  bool _handlingDrop = false;

  bool get _routeIsCurrent => ModalRoute.of(context)?.isCurrent ?? false;

  Future<void> _onDrop(DropDoneDetails details) async {
    setState(() => _dragging = false);
    if (!_routeIsCurrent || _handlingDrop || details.files.length != 1) return;
    _handlingDrop = true;
    try {
      final path = p.normalize(details.files.single.path);
      if (!await Directory(path).exists()) {
        if (mounted && _routeIsCurrent) {
          await _showDropMessage(
            context.l10n.dropFolderRequired,
            context.l10n.dropFolderRequiredMessage,
          );
        }
        return;
      }
      final hasImages = await api.hasIndexableImages(albumPath: path);
      if (!mounted || !_routeIsCurrent) return;
      if (!hasImages) {
        await _showDropMessage(
          context.l10n.noImagesFound,
          context.l10n.dropFolderNoImagesMessage,
        );
        return;
      }
      if (!mounted || !_routeIsCurrent) return;
      await widget.onFolderDropped(
        PathSelectionResult(path: path, displayName: p.basename(path)),
      );
    } on FileSystemException {
      if (mounted && _routeIsCurrent) {
        await _showDropMessage(
          context.l10n.albumUnavailable,
          context.l10n.albumUnavailableReAdd,
        );
      }
    } catch (error) {
      if (mounted && _routeIsCurrent) {
        await _showDropMessage(
          context.l10n.albumUnavailable,
          context.l10n.indexingError(error),
        );
      }
    } finally {
      _handlingDrop = false;
    }
  }

  Future<void> _showDropMessage(String title, String message) =>
      showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(context.l10n.ok),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final enabled = _routeIsCurrent;
    return DropTarget(
      enable: enabled,
      onDragDone: (details) => unawaited(_onDrop(details)),
      onDragEntered: (_) => setState(() => _dragging = true),
      onDragExited: (_) => setState(() => _dragging = false),
      child: Stack(
        fit: StackFit.expand,
        children: [
          widget.child,
          if (enabled && _dragging)
            Positioned.fill(
              child: IgnorePointer(
                child: ColoredBox(
                  color: Theme.of(context).colorScheme.primary
                      .withValues(alpha: 0.12),
                  child: Center(
                    child: Card(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.create_new_folder_outlined,
                              size: 48,
                            ),
                            const SizedBox(height: 12),
                            Text(context.l10n.addAlbum),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
