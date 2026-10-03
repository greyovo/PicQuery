import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

class PathSelectionResult {
  final String path;
  final String displayName;
  final List<String>? imagePaths;

  const PathSelectionResult({
    required this.path,
    required this.displayName,
    this.imagePaths,
  });
}

Future<PathSelectionResult?> pickIndexPathToIndex(BuildContext context) async {
  if (isMobile) {
    return _pickAlbumOnMobile(context);
  } else {
    return _pickAlbumOnDesktop(context);
  }
}

Future<PathSelectionResult?> _pickAlbumOnMobile(BuildContext context) async {
  final PermissionState permission =
      await PhotoManager.requestPermissionExtend();

  if (!permission.isAuth) {
    if (context.mounted) {
      _showPermissionDenied(context);
    }
    return null;
  }

  final List<AssetPathEntity> albums = await PhotoManager.getAssetPathList(
    type: RequestType.image,
    hasAll: true,
  );

  if (albums.isEmpty) {
    if (context.mounted) {
      _showNoAlbums(context);
    }
    return null;
  }

  if (!context.mounted) return null;

  final selectedAlbum = await showModalBottomSheet<AssetPathEntity>(
    context: context,
    isScrollControlled: true,
    builder: (dialogContext) => _AlbumGridBottomSheet(albums: albums),
  );

  if (selectedAlbum == null || !context.mounted) {
    return null;
  }

  final int count = await selectedAlbum.assetCountAsync;
  final List<AssetEntity> imageAssets = await selectedAlbum.getAssetListRange(
    start: 0,
    end: count,
  );

  final List<String> imagePaths = [];
  for (final asset in imageAssets) {
    final File? file = await asset.originFile;
    if (file != null && await file.exists()) {
      imagePaths.add(file.path);
    }
  }

  if (imagePaths.isEmpty) {
    if (context.mounted) {
      _showNoImages(context);
    }
    return null;
  }

  return PathSelectionResult(
    path: selectedAlbum.id,
    displayName: selectedAlbum.name,
    imagePaths: imagePaths,
  );
}

/// Loads an album again by its stable PhotoManager id, so an interrupted
/// mobile index can continue without asking the user to select it again.
Future<PathSelectionResult?> loadMobileAlbum(String albumId) async {
  final permission = await PhotoManager.requestPermissionExtend();
  if (!permission.isAuth) return null;
  final albums = await PhotoManager.getAssetPathList(
    type: RequestType.image,
    hasAll: true,
  );
  final album = albums.where((item) => item.id == albumId).firstOrNull;
  if (album == null) return null;
  return _albumToSelection(album);
}

/// Finds the Android DCIM album (usually named DCIM or Camera) and loads it.
Future<PathSelectionResult?> loadRecommendedAndroidAlbum() async {
  final permission = await PhotoManager.requestPermissionExtend();
  if (!permission.isAuth) return null;
  final albums = await PhotoManager.getAssetPathList(
    type: RequestType.image,
    hasAll: true,
  );
  final album = albums.where((item) {
    final name = item.name.toLowerCase();
    return name == 'dcim' || name.contains('dcim') || name == 'camera';
  }).firstOrNull;
  return album == null ? null : _albumToSelection(album);
}

Future<PathSelectionResult?> _albumToSelection(
  AssetPathEntity selectedAlbum,
) async {
  final count = await selectedAlbum.assetCountAsync;
  final imageAssets = await selectedAlbum.getAssetListRange(
    start: 0,
    end: count,
  );
  final imagePaths = <String>[];
  for (final asset in imageAssets) {
    final file = await asset.originFile;
    if (file != null && await file.exists()) imagePaths.add(file.path);
  }
  if (imagePaths.isEmpty) return null;
  return PathSelectionResult(
    path: selectedAlbum.id,
    displayName: selectedAlbum.name,
    imagePaths: imagePaths,
  );
}

Future<PathSelectionResult?> _pickAlbumOnDesktop(BuildContext context) async {
  final result = await FilePicker.platform.getDirectoryPath(
    dialogTitle: context.l10n.selectAlbumToIndex,
  );

  if (result == null) {
    return null;
  }

  return PathSelectionResult(
    path: result,
    displayName: result.split(Platform.pathSeparator).last,
  );
}

void _showPermissionDenied(BuildContext context) {
  showDialog(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(context.l10n.permissionDenied),
      content: Text(context.l10n.photoPermissionMessage),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(context.l10n.ok),
        ),
      ],
    ),
  );
}

void _showNoAlbums(BuildContext context) {
  showDialog(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(context.l10n.noAlbumsFound),
      content: Text(context.l10n.noAlbumsMessage),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(context.l10n.ok),
        ),
      ],
    ),
  );
}

void _showNoImages(BuildContext context) {
  showDialog(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(context.l10n.noImagesFound),
      content: Text(context.l10n.noImagesMessage),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(context.l10n.ok),
        ),
      ],
    ),
  );
}

class _AlbumGridBottomSheet extends StatelessWidget {
  final List<AssetPathEntity> albums;

  const _AlbumGridBottomSheet({required this.albums});

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final maxCrossAxisExtent = getAdaptiveMaxCrossAxisExtent(screenWidth);

    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      minChildSize: 0.3,
      maxChildSize: 0.85,
      expand: false,
      builder: (context, scrollController) {
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    context.l10n.selectAlbum,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: GridView.builder(
                controller: scrollController,
                padding: const EdgeInsets.all(8),
                gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: maxCrossAxisExtent,
                  childAspectRatio: 1.0,
                  crossAxisSpacing: 8,
                  mainAxisSpacing: 8,
                ),
                itemCount: albums.length,
                itemBuilder: (context, index) {
                  final album = albums[index];
                  return _AlbumGridCell(
                    album: album,
                    onTap: () => Navigator.pop(context, album),
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }
}

class _AlbumGridCell extends StatefulWidget {
  final AssetPathEntity album;
  final VoidCallback onTap;

  const _AlbumGridCell({required this.album, required this.onTap});

  @override
  State<_AlbumGridCell> createState() => _AlbumGridCellState();
}

class _AlbumGridCellState extends State<_AlbumGridCell> {
  late final Future<Uint8List?> _cover = _loadCover();

  Future<Uint8List?> _loadCover() async {
    final assets = await widget.album.getAssetListRange(start: 0, end: 1);
    if (assets.isEmpty) return null;
    return assets.first.thumbnailDataWithSize(const ThumbnailSize.square(300));
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.hardEdge,
      child: InkWell(
        onTap: widget.onTap,
        child: Stack(
          fit: StackFit.expand,
          children: [
            FutureBuilder<Uint8List?>(
              future: _cover,
              builder: (context, snapshot) {
                final bytes = snapshot.data;
                if (bytes != null) {
                  return Image.memory(
                    bytes,
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                  );
                }
                return ColoredBox(
                  color: context.colors.surfaceContainerHighest,
                  child: snapshot.connectionState == ConnectionState.waiting
                      ? const Center(child: CircularProgressIndicator())
                      : Icon(
                          Icons.photo_album_outlined,
                          size: 48,
                          color: context.colors.primary,
                        ),
                );
              },
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                padding: const EdgeInsets.fromLTRB(8, 18, 8, 8),
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Colors.black87],
                  ),
                ),
                child: Text(
                  widget.album.name,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
