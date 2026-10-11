import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
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
    if (isDesktop) {
      await showDialog<void>(
        context: context,
        useRootNavigator: true,
        builder: (_) => const Dialog(
          child: SizedBox(width: 520, height: 560, child: AppUpdateSheet()),
        ),
      );
    } else {
      await showModalBottomSheet<void>(
        context: context,
        useRootNavigator: true,
        isScrollControlled: true,
        useSafeArea: true,
        showDragHandle: true,
        builder: (_) => const AppUpdateSheet(),
      );
    }
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
        child: Padding(
          padding: EdgeInsets.fromLTRB(24, isDesktop ? 24 : 8, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l.appUpdateTitle,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (release != null) ...[
                        Text(
                          l.appUpdateAvailable(release.version.toString()),
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        if (release.notes.trim().isNotEmpty) ...[
                          const SizedBox(height: 12),
                          ConstrainedBox(
                            constraints: BoxConstraints(
                              maxHeight: isDesktop
                                  ? 280
                                  : MediaQuery.sizeOf(context).height * .5,
                            ),
                            child: SingleChildScrollView(
                              primary: false,
                              child: MarkdownBody(
                                data: release.notes,
                                selectable: true,
                              ),
                            ),
                          ),
                        ],
                        const SizedBox(height: 16),
                        if (manager.asset == null) Text(l.appUpdateUnsupported),
                        if (manager.asset != null && manager.os == 'macos')
                          Text(l.appUpdateMacGuidance),
                        if (manager.asset != null && manager.os == 'linux')
                          Text(l.appUpdateLinuxGuidance),
                      ],
                      if (status != null) ...[
                        const SizedBox(height: 12),
                        Text(status),
                      ],
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
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Wrap(
                alignment: WrapAlignment.end,
                spacing: 8,
                runSpacing: 8,
                children: [
                  if (!hasUpdate || completed)
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: Text(l.appUpdateClose),
                    ),
                  if (hasUpdate && !completed) ...[
                    TextButton(
                      onPressed: manager.busy
                          ? null
                          : () async {
                              await manager.skip();
                              if (context.mounted) Navigator.pop(context);
                            },
                      child: Text(l.appUpdateSkip),
                    ),
                    FilledButton.icon(
                      onPressed: manager.busy
                          ? null
                          : manager.asset == null
                          ? manager.openRelease
                          : manager.update,
                      icon: const Icon(Icons.download),
                      label: Text(l.appUpdateDownload),
                    ),
                  ],
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
