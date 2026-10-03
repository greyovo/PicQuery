import 'dart:io';

import 'package:flutter/material.dart';
import 'package:open_filex/open_filex.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/album_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/widgets/album_grid_card.dart';
import 'package:picquery_app/src/widgets/empty_albums_guide.dart';
import 'package:watch_it/watch_it.dart';

const _albumGridPadding = EdgeInsets.fromLTRB(12, 8, 12, 112);
const _albumGridSpacing = 16.0;
const _albumGridMaxCardWidth = 220.0;
const _phoneGridBreakpoint = 600.0;
const _phoneGridColumnCount = 2;

class AlbumManagePage extends WatchingStatefulWidget {
  const AlbumManagePage({super.key});

  @override
  State<AlbumManagePage> createState() => _AlbumManagePageState();
}

class _AlbumManagePageState extends State<AlbumManagePage> {
  Future<void> _openAlbumLocation(String albumPath) async {
    final strings = context.l10n;
    if (!isDesktop) {
      Toast.showMessage(strings.desktopOnlyOpenAlbum);
      return;
    }
    if (!await Directory(albumPath).exists()) {
      Toast.showMessage(strings.albumUnavailable);
      return;
    }
    try {
      final result = await OpenFilex.open(albumPath);
      if (result.type != ResultType.done) {
        Toast.showMessage(strings.openAlbumFailed(result.message));
      }
    } catch (error) {
      Toast.showMessage(strings.openAlbumFailed(error));
    }
  }

  Future<void> _deleteAlbum(int albumId) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.delete_outline),
        title: Text(context.l10n.removeAlbumIndex),
        content: Text(context.l10n.removeAlbumIndexMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(context.l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(
              backgroundColor: context.colors.error,
              foregroundColor: context.colors.onError,
            ),
            child: Text(context.l10n.remove),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await albumManager.deleteAlbums({albumId});
    }
  }

  Widget _buildUpdateButton(IndexingManager indexing) {
    if (indexing.albumUpdateStatus == AlbumUpdateStatus.checking) {
      return IconButton(
        onPressed: null,
        icon: const SizedBox.square(
          dimension: 16,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        tooltip: context.l10n.checking,
      );
    }
    final hasUpdate =
        indexing.albumUpdateStatus == AlbumUpdateStatus.updateAvailable;
    final isUpToDate = indexing.albumUpdateStatus == AlbumUpdateStatus.upToDate;
    return IconButton(
      onPressed: indexing.isIndexing
          ? null
          : hasUpdate
          ? () => indexing.startUpdateIndexing(
              onDone: () => albumManager.reload(),
            )
          : () => indexing.checkForUpdates(context: context),
      icon: Icon(
        isUpToDate ? Icons.check_circle_rounded : Icons.refresh_rounded,
        color: isUpToDate ? context.colors.primary : null,
      ),
      tooltip: isUpToDate
          ? context.l10n.indexUpToDate
          : hasUpdate
          ? context.l10n.updatePhotos(indexing.pendingUpdateCount)
          : context.l10n.checkUpdates,
    );
  }

  @override
  Widget build(BuildContext context) {
    final indexing = indexingManager;
    watch(indexing);
    final albums = watchValue((AlbumManager m) => m.albums);
    final isLoading = watchValue((AlbumManager m) => m.isLoading);
    final totalPhotos = albums.fold<int>(
      0,
      (sum, item) => sum + item.imageCount,
    );

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 104,
        titleSpacing: 20,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        title: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              context.l10n.albums,
              style: Theme.of(context).textTheme.headlineLarge
                  ?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -1),
            ),
            const SizedBox(height: 6),
            Text(
              indexing.isIndexing
                  ? context.l10n.indexingAlbumsSummary(albums.length)
                  : context.l10n.albumsPhotosSummary(
                      albums.length,
                      totalPhotos,
                    ),
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(color: context.colors.onSurfaceVariant),
            ),
          ],
        ),
        actions: [_buildUpdateButton(indexing), const SizedBox(width: 12)],
      ),
      floatingActionButton: isLoading || albums.isEmpty
          ? null
          : FloatingActionButton.extended(
              onPressed: indexing.isIndexing
                  ? null
                  : () => indexing.pickAndIndexAlbum(context),
              icon: const Icon(Icons.add_photo_alternate_outlined),
              label: Text(context.l10n.addAlbum),
            ),
      body: CustomScrollView(
        slivers: [
          if (isLoading)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: Center(child: CircularProgressIndicator()),
            )
          else if (albums.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: EmptyAlbumsGuide(
                onAdd: indexing.isIndexing
                    ? null
                    : () => indexing.pickAndIndexAlbum(context),
              ),
            )
          else
            SliverPadding(
              padding: _albumGridPadding,
              sliver: SliverLayoutBuilder(
                builder: (context, constraints) {
                  final isPhoneLayout =
                      isMobile &&
                      constraints.crossAxisExtent < _phoneGridBreakpoint;
                  final responsiveColumns =
                      ((constraints.crossAxisExtent + _albumGridSpacing) /
                              (_albumGridMaxCardWidth + _albumGridSpacing))
                          .ceil();
                  // Phones use two columns. Wider layouts add columns as
                  // needed so album cards never grow beyond the target size.
                  final columns = isPhoneLayout
                      ? _phoneGridColumnCount
                      : responsiveColumns < 1
                      ? 1
                      : responsiveColumns;
                  final cardWidth =
                      (constraints.crossAxisExtent -
                          _albumGridSpacing * (columns - 1)) /
                      columns;
                  final cardCoverHeight = cardWidth / albumGridCoverAspectRatio;
                  return SliverGrid(
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: columns,
                      mainAxisSpacing: _albumGridSpacing,
                      crossAxisSpacing: _albumGridSpacing,
                      mainAxisExtent: cardCoverHeight + albumGridDetailsHeight,
                    ),
                    delegate: SliverChildBuilderDelegate((context, index) {
                      final album = albums[index];
                      return _AlbumCard(
                        album: album,
                        indexing: indexing,
                        onOpen: () => _openAlbumLocation(album.albumPath),
                        onDelete: () => _deleteAlbum(album.id),
                      );
                    }, childCount: albums.length),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _AlbumCard extends StatelessWidget {
  const _AlbumCard({
    required this.album,
    required this.indexing,
    required this.onOpen,
    required this.onDelete,
  });

  final Album album;
  final IndexingManager indexing;
  final VoidCallback onOpen;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final isCurrent =
        indexing.isIndexing &&
        (indexing.currentAlbum == album.albumPath ||
            indexing.albumName == album.albumPath);
    final incomplete = !album.isIndexComplete;
    final updateAvailable = indexing.updateAvailableAlbumPaths.contains(
      album.albumPath,
    );
    final canResume = !indexing.isIndexing && (incomplete || updateAvailable);

    return AlbumGridCard(
      album: album,
      onTap: onOpen,
      progress: isCurrent && indexing.total > 0
          ? (indexing.current / indexing.total).clamp(0.0, 1.0)
          : null,
      indexedCount: isCurrent ? indexing.current : null,
      totalCount: isCurrent ? indexing.total : null,
      imagesPerSecond: isCurrent ? indexing.imagesPerSecond : null,
      onDelete: onDelete,
      statusLabel: incomplete
          ? context.l10n.incompletePhotoCount(
              album.imageCount,
              album.totalImageCount,
            )
          : updateAvailable
          ? context.l10n.updatesPhotoCount(album.imageCount)
          : context.l10n.photoCount(album.imageCount),
      isUpdateAvailable: updateAvailable,
      onResumeIndexing: canResume
          ? () => incomplete || isMobile
                ? indexing.continueIndexing(album, context: context)
                : indexing.startUpdateIndexing(
                    onDone: () => albumManager.reload(),
                  )
          : null,
    );
  }
}
