import 'dart:io';

import 'package:flutter/material.dart';
import 'package:open_filex/open_filex.dart';
import 'package:picquery_app/src/managers/folder_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/widgets/folder_list_item.dart';
import 'package:watch_it/watch_it.dart';

class AlbumManagePage extends WatchingStatefulWidget {
  const AlbumManagePage({super.key});

  @override
  State<AlbumManagePage> createState() => _AlbumManagePageState();
}

class _AlbumManagePageState extends State<AlbumManagePage> {
  Future<void> _openFolderLocation(String folderPath) async {
    final strings = context.l10n;
    if (!isDesktop) {
      Toast.showMessage(strings.desktopOnlyOpenAlbum);
      return;
    }
    if (!await Directory(folderPath).exists()) {
      Toast.showMessage(strings.folderUnavailable);
      return;
    }
    try {
      final result = await OpenFilex.open(folderPath);
      if (result.type != ResultType.done) {
        Toast.showMessage(strings.openFolderFailed(result.message));
      }
    } catch (error) {
      Toast.showMessage(strings.openFolderFailed(error));
    }
  }

  Future<void> _deleteFolder(int folderId) async {
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
      await folderManager.deleteFolders({folderId});
    }
  }

  Widget _buildUpdateButton(IndexingManager indexing) {
    if (indexing.albumUpdateStatus == AlbumUpdateStatus.checking) {
      return IconButton.filledTonal(
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
    return IconButton.filledTonal(
      onPressed: indexing.isIndexing
          ? null
          : hasUpdate
          ? () => indexing.startUpdateIndexing(
              onDone: () => folderManager.reload(),
            )
          : () => indexing.checkForUpdates(context: context),
      icon: const Icon(Icons.refresh_rounded),
      tooltip: hasUpdate
          ? context.l10n.updatePhotos(indexing.pendingUpdateCount)
          : context.l10n.checkUpdates,
    );
  }

  @override
  Widget build(BuildContext context) {
    final indexing = indexingManager;
    watch(indexing);
    final folders = watchValue((FolderManager m) => m.folders);
    final isLoading = watchValue((FolderManager m) => m.isLoading);
    final totalPhotos = folders.fold<int>(
      0,
      (sum, item) => sum + item.imageCount,
    );

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: indexing.isIndexing
            ? null
            : () => indexing.pickAndIndexFolder(context),
        icon: const Icon(Icons.add_photo_alternate_outlined),
        label: Text(context.l10n.addAlbum),
      ),
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 16),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            context.l10n.albums,
                            style: Theme.of(context).textTheme.displaySmall
                                ?.copyWith(
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: -1,
                                ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            indexing.isIndexing
                                ? context.l10n.indexingAlbumsSummary(
                                    folders.length,
                                  )
                                : context.l10n.albumsPhotosSummary(
                                    folders.length,
                                    totalPhotos,
                                  ),
                            style: Theme.of(context).textTheme.titleMedium
                                ?.copyWith(
                                  color: context.colors.onSurfaceVariant,
                                ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 16),
                    _buildUpdateButton(indexing),
                  ],
                ),
              ),
            ),
            if (isLoading)
              const SliverFillRemaining(
                hasScrollBody: false,
                child: Center(child: CircularProgressIndicator()),
              )
            else if (folders.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: _EmptyAlbums(
                  onAdd: indexing.isIndexing
                      ? null
                      : () => indexing.pickAndIndexFolder(context),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 112),
                sliver: SliverLayoutBuilder(
                  builder: (context, constraints) {
                    const spacing = 16.0;
                    const maxCardWidth = 220.0;
                    final isPhoneLayout =
                        isMobile && constraints.crossAxisExtent < 600;
                    final responsiveColumns =
                        ((constraints.crossAxisExtent + spacing) /
                                (maxCardWidth + spacing))
                            .ceil();
                    // Phones use two columns. Wider layouts add columns as
                    // needed so album cards never grow beyond the target size.
                    final columns = isPhoneLayout
                        ? 2
                        : responsiveColumns < 1
                        ? 1
                        : responsiveColumns;
                    final cardWidth =
                        (constraints.crossAxisExtent -
                            spacing * (columns - 1)) /
                        columns;
                    final cardDetailsHeight = indexing.isIndexing
                        ? 116.0
                        : 68.0;
                    return SliverGrid(
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: columns,
                        mainAxisSpacing: spacing,
                        crossAxisSpacing: spacing,
                        mainAxisExtent: cardWidth + cardDetailsHeight,
                      ),
                      delegate: SliverChildBuilderDelegate((context, index) {
                        final folder = folders[index];
                        final isCurrent =
                            indexing.isIndexing &&
                            (indexing.currentFolder == folder.folderPath ||
                                indexing.folderName == folder.folderPath);
                        final progress = isCurrent && indexing.total > 0
                            ? (indexing.current / indexing.total).clamp(
                                0.0,
                                1.0,
                              )
                            : null;
                        final incomplete = !folder.isIndexComplete;
                        final updateAvailable = indexing
                            .updateAvailableFolderPaths
                            .contains(folder.folderPath);
                        return FolderListItem(
                          folder: folder,
                          onTap: () => _openFolderLocation(folder.folderPath),
                          progress: progress,
                          indexedCount: isCurrent ? indexing.current : null,
                          totalCount: isCurrent ? indexing.total : null,
                          imagesPerSecond: isCurrent
                              ? indexing.imagesPerSecond
                              : null,
                          onCancelIndexing: isCurrent
                              ? () => indexing.cancelIndexing(context)
                              : null,
                          onDelete: () => _deleteFolder(folder.id),
                          statusLabel: incomplete
                              ? context.l10n.incompletePhotoCount(
                                  folder.imageCount,
                                  folder.totalImageCount,
                                )
                              : updateAvailable
                              ? context.l10n.updatesPhotoCount(
                                  folder.imageCount,
                                )
                              : context.l10n.photoCount(folder.imageCount),
                          onResumeIndexing:
                              !indexing.isIndexing &&
                                  (incomplete || updateAvailable)
                              ? () => incomplete || isMobile
                                    ? indexing.continueIndexing(
                                        folder,
                                        context: context,
                                      )
                                    : indexing.startUpdateIndexing(
                                        onDone: () => folderManager.reload(),
                                      )
                              : null,
                          isGridCard: true,
                        );
                      }, childCount: folders.length),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _EmptyAlbums extends StatelessWidget {
  const _EmptyAlbums({required this.onAdd});

  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        constraints: const BoxConstraints(maxWidth: 440),
        margin: const EdgeInsets.all(28),
        padding: const EdgeInsets.all(36),
        decoration: BoxDecoration(
          color: context.colors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(28),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircleAvatar(
              radius: 38,
              backgroundColor: context.colors.primaryContainer,
              foregroundColor: context.colors.onPrimaryContainer,
              child: const Icon(Icons.photo_library_outlined, size: 36),
            ),
            const SizedBox(height: 22),
            Text(
              context.l10n.buildPhotoLibrary,
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
