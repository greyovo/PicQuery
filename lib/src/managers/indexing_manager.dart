import 'dart:async';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/engine/api.dart' as api;
import 'package:picquery_app/src/engine/models.dart';
import 'package:picquery_app/src/managers/album_manager.dart';
import 'package:picquery_app/src/utils/path_selector.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:watch_it/watch_it.dart';

IndexingManager get indexingManager => di<IndexingManager>();

enum AlbumUpdateStatus { idle, checking, updateAvailable, upToDate }

class IndexingManager extends ChangeNotifier {
  final autoUpdateIndexOnStartup = ValueNotifier<bool>(
    SettingsStore.getAutoUpdateIndexOnStartup(),
  );

  bool isIndexing = false;
  DateTime? startTime;
  String albumName = '';
  int current = 0;
  int total = 0;
  int alreadyIndexed = 0;

  AlbumUpdateStatus albumUpdateStatus = .idle;
  int pendingUpdateCount = 0;
  Set<String> updateAvailableAlbumPaths = const {};
  String? currentPath;
  String? currentAlbum;

  final _navigateToManageTabController = StreamController<void>.broadcast();
  Stream<void> get onNavigateToManageTab =>
      _navigateToManageTabController.stream;

  StreamSubscription<IndexProgress>? _subscription;
  Timer? _upToDateResetTimer;

  void start(String albumName, {int alreadyIndexed = 0}) {
    isIndexing = true;
    startTime = DateTime.now();
    this.albumName = albumName;
    this.alreadyIndexed = alreadyIndexed;
    current = 0;
    total = 0;
    currentPath = null;
    currentAlbum = null;
    notifyListeners();
  }

  void updateProgress(int current, int total) {
    this.current = current;
    this.total = total;
    notifyListeners();
  }

  double get imagesPerSecond {
    final startedAt = startTime;
    if (startedAt == null) return 0;
    final elapsedSeconds =
        DateTime.now().difference(startedAt).inMilliseconds / 1000;
    if (elapsedSeconds <= 0) return 0;
    return current / elapsedSeconds;
  }

  void complete() {
    isIndexing = false;
    notifyListeners();
  }

  void stop() {
    isIndexing = false;
    notifyListeners();
  }

  void cancel() {
    _subscription?.cancel();
    isIndexing = false;
    albumUpdateStatus = .idle;
    notifyListeners();
    // The engine finishes its current image and writes its checkpoint after
    // the stream subscription has been cancelled.
    Future.delayed(const Duration(milliseconds: 300), albumManager.reload);
  }

  void reset() {
    isIndexing = false;
    startTime = null;
    albumName = '';
    current = 0;
    total = 0;
    alreadyIndexed = 0;
    albumUpdateStatus = .idle;
    pendingUpdateCount = 0;
    updateAvailableAlbumPaths = const {};
    currentPath = null;
    currentAlbum = null;
    notifyListeners();
  }

  void startIndexing(
    String path,
    String displayName, {
    int alreadyIndexed = 0,
    List<String>? imagePaths,
    bool isUpdate = false,
    VoidCallback? onDone,
    void Function(Object error)? onError,
  }) {
    start(displayName, alreadyIndexed: alreadyIndexed);

    Stream<IndexProgress> stream;
    if (imagePaths != null) {
      stream = api.indexImages(
        albumName: path,
        displayName: displayName,
        imagePaths: imagePaths,
        isUpdate: isUpdate,
      );
    } else {
      stream = api.indexAlbum(albumPath: path, isUpdate: isUpdate);
    }

    _subscription?.cancel();
    var hasReloadedAlbums = false;
    _subscription = stream.listen(
      (progress) {
        currentPath = progress.currentPath;
        currentAlbum = progress.currentAlbum;
        updateProgress(progress.current, progress.total);
        // A new album record is created before the first progress event. Reload
        // once so its card can show the in-place indexing state immediately.
        if (!hasReloadedAlbums) {
          hasReloadedAlbums = true;
          unawaited(albumManager.reload());
        }
      },
      onDone: () {
        complete();
        onDone?.call();
      },
      onError: (error) {
        stop();
        onError?.call(error);
      },
    );
  }

  /// Checks indexed albums for file changes.
  ///
  /// Set [showToast] to false for background checks, such as the one run when
  /// the app starts. Set [updateAutomatically] to apply detected changes as
  /// an incremental index update after the check completes.
  Future<void> checkForUpdates({
    BuildContext? context,
    bool showToast = true,
    bool updateAutomatically = false,
  }) async {
    _upToDateResetTimer?.cancel();
    albumUpdateStatus = .checking;
    pendingUpdateCount = 0;
    notifyListeners();

    try {
      final results = await api.checkForUpdates();
      var totalNew = results.fold<int>(0, (sum, r) => sum + r.newCount);
      final updatePaths = results.map((r) => r.albumPath).toSet();
      // Mobile albums are MediaStore collections, not file-system albums, so
      // the engine's directory scan cannot inspect them. Compare their current
      // asset count here; pressing continue performs the full path-level sync.
      if (isMobile) {
        await albumManager.reload();
        for (final album in albumManager.albums.value) {
          final selection = await loadMobileAlbum(album.albumPath);
          if (selection != null &&
              selection.imagePaths!.length != album.imageCount) {
            updatePaths.add(album.albumPath);
            totalNew += (selection.imagePaths!.length - album.imageCount).abs();
          }
        }
      }
      updateAvailableAlbumPaths = updatePaths;

      if (updatePaths.isNotEmpty) {
        albumUpdateStatus = .updateAvailable;
        pendingUpdateCount = totalNew;
        if (updateAutomatically) {
          startUpdateIndexing(onDone: () => albumManager.reload());
        }
      } else {
        updateAvailableAlbumPaths = const {};
        setIndexingStatusUpToDate();
        if (showToast) {
          if (context != null && context.mounted) {
            Toast.showMessage(context.l10n.indexUpToDate);
          }
        }
      }
    } catch (e) {
      albumUpdateStatus = .idle;
      updateAvailableAlbumPaths = const {};
      if (showToast) {
        if (context != null && context.mounted) {
          Toast.showMessage(context.l10n.checkUpdatesFailed(e));
        }
      }
    }

    notifyListeners();
  }

  Future<void> setAutoUpdateIndexOnStartup(bool enabled) async {
    autoUpdateIndexOnStartup.value = enabled;
    await SettingsStore.setAutoUpdateIndexOnStartup(enabled);
  }

  void startUpdateIndexing({
    VoidCallback? onDone,
    void Function(Object error)? onError,
  }) {
    start('增量更新');
    albumUpdateStatus = .idle;
    updateAvailableAlbumPaths = const {};

    final stream = api.indexPendingUpdates();

    _subscription?.cancel();
    _subscription = stream.listen(
      (progress) {
        currentPath = progress.currentPath;
        currentAlbum = progress.currentAlbum;
        updateProgress(progress.current, progress.total);
      },
      onDone: () {
        setIndexingStatusUpToDate();
        complete();
        onDone?.call();
      },
      onError: (error) {
        albumUpdateStatus = .idle;
        stop();
        onError?.call(error);
      },
    );
  }

  @override
  void dispose() {
    _subscription?.cancel();
    _upToDateResetTimer?.cancel();
    _navigateToManageTabController.close();
    autoUpdateIndexOnStartup.dispose();
    super.dispose();
  }

  Future<void> pickAndIndexAlbum(BuildContext context) async {
    final result = await pickIndexPathToIndex(context);

    if (result == null || !context.mounted) return;

    final albums = albumManager.albums.value;
    final existingAlbum = albums
        .where((f) => f.albumPath == result.path)
        .firstOrNull;
    final alreadyIndexed = existingAlbum != null;

    if (alreadyIndexed) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(context.l10n.alreadyIndexed),
          content: Text(context.l10n.alreadyIndexedMessage),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(context.l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(context.l10n.update),
            ),
          ],
        ),
      );

      if (confirmed != true || !context.mounted) return;
    }

    _navigateToManageTabController.add(null);

    startIndexing(
      result.path,
      result.displayName,
      alreadyIndexed: alreadyIndexed ? existingAlbum.imageCount : 0,
      imagePaths: result.imagePaths,
      isUpdate: alreadyIndexed,
      onDone: () => albumManager.reload(),
      onError: (error) {
        Toast.showMessage(context.l10n.indexingError(error));
        albumManager.reload();
      },
    );
  }

  Future<void> continueIndexing(Album album, {BuildContext? context}) async {
    PathSelectionResult? selection;
    if (isMobile) {
      selection = await loadMobileAlbum(album.albumPath);
      if (selection == null) {
        if (context != null && context.mounted) {
          Toast.showMessage(context.l10n.albumUnavailableReAdd);
        }
        return;
      }
    }
    startIndexing(
      album.albumPath,
      selection?.displayName ?? album.albumPath,
      alreadyIndexed: album.imageCount,
      imagePaths: selection?.imagePaths,
      isUpdate: true,
      onDone: () => albumManager.reload(),
      onError: (_) => albumManager.reload(),
    );
  }

  Future<void> indexRecommendedAndroidAlbum({BuildContext? context}) async {
    final selection = await loadRecommendedAndroidAlbum();
    if (selection == null) {
      if (context != null && context.mounted) {
        Toast.showMessage(context.l10n.dcimNotFound);
      }
      return;
    }
    startIndexing(
      selection.path,
      selection.displayName,
      imagePaths: selection.imagePaths,
      onDone: () => albumManager.reload(),
      onError: (_) => albumManager.reload(),
    );
  }

  Future<void> cancelIndexing(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(context.l10n.cancelIndexingTitle),
        content: Text(context.l10n.cancelIndexingMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(context.l10n.keepIndexing),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(context.l10n.cancel),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      cancel();
      albumManager.reload();
    }
  }

  void setIndexingStatusUpToDate() {
    _upToDateResetTimer?.cancel();
    albumUpdateStatus = .upToDate;
    updateAvailableAlbumPaths = const {};
    notifyListeners();
    _upToDateResetTimer = Timer(const Duration(seconds: 5), () {
      if (albumUpdateStatus != .upToDate) {
        return;
      }
      albumUpdateStatus = .idle;
      notifyListeners();
    });
  }
}
