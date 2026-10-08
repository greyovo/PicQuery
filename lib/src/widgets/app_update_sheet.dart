import 'dart:async';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/managers/update_manager.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:watch_it/watch_it.dart';

Future<void> checkAppUpdatesOnStartup(BuildContext context) async {
  final manager = updateManager;
  final prompt = await manager.check();
  if (context.mounted && prompt && !manager.sheetVisible) {
    await showAppUpdateSheet(context);
  }
}

Future<void> showAppUpdateSheet(
  BuildContext context, {
  bool manual = false,
}) async {
  final manager = updateManager;
  if (manager.sheetVisible) return;
  manager.sheetVisible = true;
  if (manual) unawaited(manager.check(manual: true));
  try {
    await showModalBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      constraints: const BoxConstraints(maxWidth: 640),
      builder: (_) => const AppUpdateSheet(),
    );
  } finally {
    manager.sheetVisible = false;
  }
}

class AppUpdateSheet extends WatchingWidget {
  const AppUpdateSheet({super.key});

  @override
  Widget build(BuildContext context) {
    final manager = watchIt<UpdateManager>();
    final l = context.l10n;
    final release = manager.release;
    final phase = manager.phase;
    final hasUpdate = release != null;
    final completed = phase == UpdatePhase.opened;
    final ready = phase == UpdatePhase.ready || phase == UpdatePhase.permission;
    final status = switch (phase) {
      UpdatePhase.idle || UpdatePhase.checking => l.appUpdateChecking,
      UpdatePhase.current => l.appUpdateCurrent,
      UpdatePhase.downloading => l.appUpdateDownloading(
        (manager.progress * 100).floor().toString(),
      ),
      UpdatePhase.installing => l.appUpdateInstalling,
      UpdatePhase.opened => l.appUpdateOpened,
      UpdatePhase.permission => l.appUpdatePermission,
      UpdatePhase.failed => l.appUpdateFailed,
      _ => null,
    };
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .85,
        ),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l.appUpdateTitle,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              if (release != null) ...[
                Text(
                  l.appUpdateAvailable(release.version.toString()),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if (release.notes.trim().isNotEmpty) ...[
                  const SizedBox(height: 12),
                  SelectableText(release.notes),
                ],
                const SizedBox(height: 16),
                if (manager.asset == null) Text(l.appUpdateUnsupported),
                if (manager.asset != null && manager.os == 'macos')
                  Text(l.appUpdateMacGuidance),
                if (manager.asset != null && manager.os == 'linux')
                  Text(l.appUpdateLinuxGuidance),
              ],
              if (status != null) ...[const SizedBox(height: 12), Text(status)],
              if (manager.error != null) ...[
                const SizedBox(height: 8),
                SelectableText(
                  manager.error!,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
              if (manager.busy) ...[
                const SizedBox(height: 16),
                LinearProgressIndicator(
                  value: phase == UpdatePhase.downloading
                      ? manager.progress
                      : null,
                ),
              ],
              const SizedBox(height: 20),
              Wrap(
                alignment: WrapAlignment.end,
                spacing: 8,
                runSpacing: 8,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(
                      hasUpdate && !completed
                          ? l.appUpdateLater
                          : l.appUpdateClose,
                    ),
                  ),
                  if (hasUpdate && !completed)
                    TextButton(
                      onPressed: manager.busy
                          ? null
                          : () async {
                              await manager.skip();
                              if (context.mounted) Navigator.pop(context);
                            },
                      child: Text(l.appUpdateSkip),
                    ),
                  if (hasUpdate)
                    TextButton(
                      onPressed: manager.busy ? null : manager.openRelease,
                      child: Text(l.appUpdateReleasePage),
                    ),
                  if (manager.asset != null && !completed)
                    FilledButton.icon(
                      onPressed: manager.busy ? null : manager.update,
                      icon: Icon(ready ? Icons.system_update : Icons.download),
                      label: Text(
                        ready
                            ? l.appUpdateInstall
                            : phase == UpdatePhase.failed
                            ? l.appUpdateRetry
                            : l.appUpdateDownload,
                      ),
                    ),
                  if (!hasUpdate && phase == UpdatePhase.failed)
                    FilledButton(
                      onPressed: () => manager.check(manual: true),
                      child: Text(l.appUpdateRetry),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
