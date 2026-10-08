import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/services/update_service.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:watch_it/watch_it.dart';

UpdateManager get updateManager => di<UpdateManager>();

enum UpdatePhase {
  idle,
  checking,
  current,
  available,
  downloading,
  ready,
  installing,
  opened,
  permission,
  failed,
}

class UpdateManager extends ChangeNotifier {
  UpdateManager({
    UpdateService? service,
    String? Function()? readSkipped,
    Future<void> Function(String)? writeSkipped,
    String? os,
  }) : _service = service ?? UpdateService(),
       _readSkipped = readSkipped ?? SettingsStore.getSkippedUpdateVersion,
       _writeSkipped = writeSkipped ?? SettingsStore.setSkippedUpdateVersion,
       os = os ?? Platform.operatingSystem;

  final UpdateService _service;
  final String? Function() _readSkipped;
  final Future<void> Function(String) _writeSkipped;
  final String os;
  final _log = Logger('UpdateManager');
  UpdatePhase phase = UpdatePhase.idle;
  AppRelease? release;
  UpdateAsset? asset;
  String? error;
  double progress = 0;
  bool sheetVisible = false;
  bool _disposed = false;

  bool get busy =>
      phase == UpdatePhase.checking ||
      phase == UpdatePhase.downloading ||
      phase == UpdatePhase.installing;

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Future<bool> check({bool manual = false}) async {
    if (busy || _disposed) return false;
    phase = UpdatePhase.checking;
    error = null;
    release = null;
    asset = null;
    progress = 0;
    _emit();
    try {
      final installed = await _service.installedVersion();
      final latest = await _service.latestRelease();
      if (_disposed) return false;
      if (latest == null || !isNewerRelease(latest.version, installed)) {
        phase = UpdatePhase.current;
        _emit();
        return false;
      }
      release = latest;
      asset = latest.assetFor(os, await _service.architecture());
      final cached = asset == null
          ? null
          : await _service.cachedPackage(asset!);
      if (_disposed) return false;
      phase = cached == null ? UpdatePhase.available : UpdatePhase.ready;
      _emit();
      return manual || _readSkipped() != latest.version.toString();
    } catch (e, stack) {
      if (_disposed) return false;
      _log.warning('Update check failed', e, stack);
      error = e.toString();
      phase = UpdatePhase.failed;
      _emit();
      return false;
    }
  }

  Future<void> skip() async {
    if (release == null || busy) return;
    await _writeSkipped(release!.version.toString());
  }

  Future<void> update() async {
    final selected = asset;
    if (selected == null || busy || _disposed) return;
    phase = UpdatePhase.downloading;
    error = null;
    progress = 0;
    _emit();
    try {
      if (await _service.cachedPackage(selected) == null) {
        await _service.download(selected, (value) {
          progress = value;
          _emit();
        });
      }
      if (_disposed) return;
      phase = UpdatePhase.installing;
      _emit();
      final opened = await _service.install(selected);
      if (_disposed) return;
      phase = opened ? UpdatePhase.opened : UpdatePhase.permission;
    } catch (e, stack) {
      if (_disposed) return;
      _log.warning('Update installation failed', e, stack);
      error = e.toString();
      phase = UpdatePhase.failed;
    }
    _emit();
  }

  Future<void> openRelease() async {
    if (release == null || busy) return;
    try {
      await _service.openRelease(release!);
    } catch (e) {
      error = e.toString();
      phase = UpdatePhase.failed;
      _emit();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _service.dispose();
    super.dispose();
  }
}
