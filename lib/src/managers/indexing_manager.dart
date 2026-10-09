import 'dart:async';

import 'package:logging/logging.dart';

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
  Object? lastIndexingError;
  final autoUpdateIndexOnStartup = ValueNotifier<bool>(
    SettingsStore.getAutoUpdateIndexOnStartup(),
  );

  bool isIndexing = false;
  DateTime? startTime;
  String albumName = '';
  int current = 0;
  int total = 0;
  int alreadyIndexed = 0;
  int _speedBaseline = 0;
  final Set<String> _pausedAlbumPaths = {};

  AlbumUpdateStatus albumUpdateStatus = .idle;
  int pendingUpdateCount = 0;
  Set<String> updateAvailableAlbumPaths = const {};
  Map<String, int> pendingUpdateCountsByAlbum = const {};
  final Map<String, Object> _errorsByAlbum = {};
  String? currentPath;
  String? currentAlbum;

  final _navigateToManageTabController = StreamController<void>.broadcast();
  Stream<void> get onNavigateToManageTab =>
      _navigateToManageTabController.stream;

  StreamSubscription<IndexProgress>? _subscription;
  Set<String> _activeAlbumPaths = const {};
  bool _isPausing = false;
  bool _isAddingAlbum = false;
  Future<void>? _pauseFuture;
  Timer? _upToDateResetTimer;

  bool get isPausing => _isPausing;

  bool isIndexingAlbum(String albumPath) =>
      isIndexing && _activeAlbumPaths.contains(albumPath);

  int pendingUpdateCountForAlbum(String albumPath) =>
      pendingUpdateCountsByAlbum[albumPath] ?? 0;

  bool isAlbumPaused(String albumPath) => _pausedAlbumPaths.contains(albumPath);

  Object? errorForAlbum(String albumPath) => _errorsByAlbum[albumPath];

  void start(String albumName, {int alreadyIndexed = 0}) {
    lastIndexingError = null;
    isIndexing = true;
    startTime = DateTime.now();
    this.albumName = albumName;
    this.alreadyIndexed = alreadyIndexed;
    _speedBaseline = alreadyIndexed;
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
    return (current - _speedBaseline).clamp(0, current) / elapsedSeconds;
  }

  void complete() {
    isIndexing = false;
    notifyListeners();
  }

  void stop() {
    isIndexing = false;
    notifyListeners();
  }

  Future<void> pause({bool refreshUpdates = true}) {
    final ongoingPause = _pauseFuture;
    if (ongoingPause != null) return ongoingPause;
    if (!isIndexing) return Future.value();

    final operation = _pauseAndRefresh(refreshUpdates: refreshUpdates);
    _pauseFuture = operation;
    return operation.whenComplete(() {
      if (identical(_pauseFuture, operation)) _pauseFuture = null;
    });
  }

  Future<void> _pauseAndRefresh({required bool refreshUpdates}) async {
    _isPausing = true;
    notifyListeners();

    _pausedAlbumPaths.addAll(_activeAlbumPaths);
    final subscription = _subscription;
    _subscription = null;
    await subscription?.cancel();

    isIndexing = false;
    _isPausing = false;
    _activeAlbumPaths = const {};
    albumUpdateStatus = .idle;
    notifyListeners();

    await albumManager.reload();
    if (refreshUpdates) {
      await checkForUpdates(showToast: false);
    }
  }

  void reset() {
    isIndexing = false;
    startTime = null;
    albumName = '';
    current = 0;
    total = 0;
    alreadyIndexed = 0;
    _speedBaseline = 0;
    _pausedAlbumPaths.clear();
    albumUpdateStatus = .idle;
    pendingUpdateCount = 0;
    updateAvailableAlbumPaths = const {};
    pendingUpdateCountsByAlbum = const {};
    currentPath = null;
    currentAlbum = null;
    _activeAlbumPaths = const {};
    _errorsByAlbum.clear();
    lastIndexingError = null;
    _isPausing = false;
    notifyListeners();
  }

  /// Refresh both album records and the engine's pending-update snapshot before
  /// allowing another indexing task to start.
  Future<void> _finishIndexing(VoidCallback? onDone) async {
    try {
      await albumManager.reload();
      await checkForUpdates(showToast: false);
    } catch (error, stackTrace) {
      Logger('IndexingManager')
          .severe('Failed to refresh albums after indexing.', error, stackTrace);
    } finally {
      complete();
      onDone?.call();
    }
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
    if (isIndexing || _isPausing) return;
    start(displayName, alreadyIndexed: alreadyIndexed);
    _activeAlbumPaths = {path};
    _pausedAlbumPaths.remove(path);
    _errorsByAlbum.remove(path);
    notifyListeners();

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
        if (progress.errors > 0) {
          lastIndexingError ??= StateError('Some images could not be indexed.');
          _errorsByAlbum[path] = lastIndexingError!;
        }
        currentPath = progress.currentPath;
        currentAlbum = progress.currentAlbum;
        // The first event includes images indexed before this run.
        if (!hasReloadedAlbums) {
          _speedBaseline = progress.current;
        }
        updateProgress(progress.current, progress.total);
        // A new album record is created before the first progress event. Reload
        // once so its card can show the in-place indexing state immediately.
        if (!hasReloadedAlbums) {
          hasReloadedAlbums = true;
          unawaited(albumManager.reload());
        }
      },
      onDone: () {
        if (lastIndexingError == null) _errorsByAlbum.remove(path);
        _subscription = null;
        _activeAlbumPaths = const {};
        unawaited(_finishIndexing(onDone));
      },
      onError: (Object error, StackTrace stackTrace) {
        lastIndexingError = error;
        Logger('IndexingManager')
            .severe('Album indexing failed.', error, stackTrace);
        _errorsByAlbum[path] = error;
        _subscription = null;
        _activeAlbumPaths = const {};
        stop();
        onError?.call(error);
      },
      cancelOnError: true,
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
    pendingUpdateCountsByAlbum = const {};
    notifyListeners();

    try {
      final results = await api.checkForUpdates();
      var totalNew = results.fold<int>(0, (sum, r) => sum + r.newCount);
      final updatePaths = results.map((r) => r.albumPath).toSet();
      final updateCounts = {
        for (final result in results) result.albumPath: result.newCount,
      };
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
            final pending = (selection.imagePaths!.length - album.imageCount)
                .abs();
            updateCounts[album.albumPath] = pending;
            totalNew += pending;
          }
        }
      }
      updateAvailableAlbumPaths = updatePaths;
      pendingUpdateCountsByAlbum = updateCounts;

      if (updatePaths.isNotEmpty) {
        albumUpdateStatus = .updateAvailable;
        pendingUpdateCount = totalNew;
        if (updateAutomatically) {
          startUpdateIndexing(onDone: () => albumManager.reload());
        }
      } else {
        updateAvailableAlbumPaths = const {};
        pendingUpdateCountsByAlbum = const {};
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
      pendingUpdateCountsByAlbum = const {};
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
    if (isIndexing || _isPausing || albumUpdateStatus == .checking) return;
    final pathsBeingUpdated = updateAvailableAlbumPaths;
    start('增量更新');
    _activeAlbumPaths = pathsBeingUpdated;
    _pausedAlbumPaths.removeAll(pathsBeingUpdated);
    for (final path in pathsBeingUpdated) {
      _errorsByAlbum.remove(path);
    }
    notifyListeners();
    albumUpdateStatus = .idle;
    updateAvailableAlbumPaths = const {};
    pendingUpdateCountsByAlbum = const {};

    final stream = api.indexPendingUpdates();

    _subscription?.cancel();
    _subscription = stream.listen(
      (progress) {
        if (progress.errors > 0) {
          lastIndexingError ??= StateError('Some images could not be indexed.');
          for (final path in pathsBeingUpdated) {
            _errorsByAlbum[path] = lastIndexingError!;
          }
        }
        currentPath = progress.currentPath;
        currentAlbum = progress.currentAlbum;
        updateProgress(progress.current, progress.total);
      },
      onDone: () {
        for (final path in pathsBeingUpdated) {
          if (lastIndexingError == null) _errorsByAlbum.remove(path);
        }
        _subscription = null;
        _activeAlbumPaths = const {};
        unawaited(_finishIndexing(onDone));
      },
      onError: (Object error, StackTrace stackTrace) {
        lastIndexingError = error;
        Logger('IndexingManager')
            .severe('Incremental indexing failed.', error, stackTrace);
        for (final path in pathsBeingUpdated) {
          _errorsByAlbum[path] = error;
        }
        _subscription = null;
        _activeAlbumPaths = const {};
        albumUpdateStatus = .idle;
        stop();
        onError?.call(error);
      },
      cancelOnError: true,
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
    if (isIndexing || _isAddingAlbum) return;
    _isAddingAlbum = true;
    try {
      final result = await pickIndexPathToIndex(context);
      if (result == null || !context.mounted) return;
      await _indexSelectedAlbum(context, result);
    } finally {
      _isAddingAlbum = false;
    }
  }

  Future<void> indexDroppedAlbum(
    BuildContext context,
    PathSelectionResult selection,
  ) async {
    if (_isAddingAlbum) return;
    if (isIndexing) {
      Toast.showMessage(context.l10n.addAlbumWhileIndexing);
      return;
    }
    _isAddingAlbum = true;
    try {
      await _indexSelectedAlbum(context, selection);
    } finally {
      _isAddingAlbum = false;
    }
  }

  Future<void> _indexSelectedAlbum(
    BuildContext context,
    PathSelectionResult result,
  ) async {
    if (!context.mounted || isIndexing) return;
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

    if (isIndexing) return;
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
      onError: (error) {
        if (context != null && context.mounted) {
          Toast.showMessage(context.l10n.indexingError(error));
        }
        albumManager.reload();
      },
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
      onError: (error) {
        if (context != null && context.mounted) {
          Toast.showMessage(context.l10n.indexingError(error));
        }
        albumManager.reload();
      },
    );
  }

  Future<void> pauseIndexing(BuildContext context) => pause();

  /// Stops an active task touching [album], then removes its persisted index.
  Future<void> deleteAlbum(Album album) async {
    if (isIndexingAlbum(album.albumPath)) {
      await pause(refreshUpdates: false);
    }
    await api.deleteAlbum(albumId: album.id);
    _errorsByAlbum.remove(album.albumPath);
    if (_errorsByAlbum.isEmpty) lastIndexingError = null;
    _pausedAlbumPaths.remove(album.albumPath);
    await albumManager.reload();
    if (!isIndexing) {
      await checkForUpdates(showToast: false);
    }
  }

  void setIndexingStatusUpToDate() {
    _upToDateResetTimer?.cancel();
    albumUpdateStatus = .upToDate;
    updateAvailableAlbumPaths = const {};
    pendingUpdateCountsByAlbum = const {};
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
