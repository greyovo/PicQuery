import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get_it/get_it.dart';
import 'package:picquery_app/l10n/generated/app_localizations.dart';
import 'package:picquery_app/src/managers/update_manager.dart';
import 'package:picquery_app/src/services/update_service.dart';
import 'package:picquery_app/src/widgets/app_update_sheet.dart';
import 'package:pub_semver/pub_semver.dart';

class FakeUpdateService extends UpdateService {
  Version installed = Version.parse('2.0.0');
  AppRelease? latest = AppRelease(
    version: Version.parse('2.1.0'),
    notes: 'New release notes',
    page: Uri.parse('https://github.com/greyovo/PicQuery/releases/tag/v2.1.0'),
    assets: [
      UpdateAsset(
        id: 1,
        name: 'PicQuery-2.1.0-android-arm64.apk',
        url: Uri.parse(
          'https://github.com/greyovo/PicQuery/releases/download/v2.1.0/PicQuery-2.1.0-android-arm64.apk',
        ),
        size: 10,
      ),
    ],
  );
  bool cached = false;
  bool permission = true;
  bool failCheck = false;
  bool failInstall = false;
  int downloads = 0;
  int installations = 0;
  Completer<void>? pending;
  @override
  Future<Version> installedVersion() async => installed;
  @override
  Future<AppRelease?> latestRelease() async {
    if (pending != null) await pending!.future;
    if (failCheck) throw const SocketException('offline');
    return latest;
  }

  @override
  Future<String> architecture() async => 'arm64';
  @override
  Future<File?> cachedPackage(UpdateAsset asset) async =>
      cached ? File('/cache/update.apk') : null;
  @override
  Future<File> download(
    UpdateAsset asset,
    void Function(double) onProgress,
  ) async {
    if (!cached) {
      downloads++;
      onProgress(.5);
      onProgress(1);
      cached = true;
    }
    return File('/cache/update.apk');
  }

  @override
  Future<bool> install(UpdateAsset asset) async {
    installations++;
    if (failInstall) throw const FileSystemException('installer failed');
    return permission;
  }
}

void main() {
  late FakeUpdateService service;
  late UpdateManager manager;
  String? skipped;
  setUp(() {
    service = FakeUpdateService();
    skipped = null;
    manager = UpdateManager(
      service: service,
      os: 'android',
      readSkipped: () => skipped,
      writeSkipped: (version) async {
        skipped = version;
      },
    );
  });
  tearDown(() async {
    manager.dispose();
    await GetIt.I.reset();
  });

  test(
    'skip persists exact version; manual overrides skip; newer versions prompt',
    () async {
      expect(await manager.check(), isTrue);
      await manager.skip();
      expect(skipped, '2.1.0');
      expect(await manager.check(), isFalse);
      expect(await manager.check(manual: true), isTrue);
      service.latest = AppRelease(
        version: Version.parse('2.2.0'),
        notes: '',
        page: service.latest!.page,
        assets: [],
      );
      expect(await manager.check(), isTrue);
    },
  );

  test('equal/older versions and startup failures never prompt', () async {
    service.installed = Version.parse('2.1.0');
    expect(await manager.check(), isFalse);
    expect(manager.phase, UpdatePhase.current);
    service.installed = Version.parse('3.0.0');
    expect(await manager.check(), isFalse);
    service.failCheck = true;
    expect(await manager.check(), isFalse);
    expect(manager.phase, UpdatePhase.failed);
    expect(manager.error, contains('offline'));
  });

  test('concurrent checks do not duplicate work', () async {
    service.pending = Completer<void>();
    final first = manager.check();
    expect(await manager.check(manual: true), isFalse);
    service.pending!.complete();
    expect(await first, isTrue);
  });

  test(
    'download progresses to installer and cached update skips network',
    () async {
      await manager.check();
      await manager.update();
      expect(manager.phase, UpdatePhase.opened);
      expect(service.downloads, 1);
      expect(service.installations, 1);
      await manager.check(manual: true);
      expect(manager.phase, UpdatePhase.ready);
      await manager.update();
      expect(service.downloads, 1);
      expect(service.installations, 2);
    },
  );

  test(
    'Android permission retry and installer failure retain downloaded package',
    () async {
      await manager.check();
      service.permission = false;
      await manager.update();
      expect(manager.phase, UpdatePhase.permission);
      service.permission = true;
      service.failInstall = true;
      await manager.update();
      expect(manager.phase, UpdatePhase.failed);
      service.failInstall = false;
      await manager.update();
      expect(manager.phase, UpdatePhase.opened);
      expect(service.downloads, 1);
    },
  );

  testWidgets(
    'manual update uses a bottom sheet, displays notes and permits skipping',
    (tester) async {
      GetIt.I.registerSingleton<UpdateManager>(manager);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showAppUpdateSheet(context, manual: true),
                child: const Text('Check'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Check'));
      await tester.pumpAndSettle();
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(find.byType(AlertDialog), findsNothing);
      expect(find.text('New release notes'), findsOneWidget);
      await tester.tap(find.text('Skip this version'));
      await tester.pumpAndSettle();
      expect(skipped, '2.1.0');
      expect(find.byType(BottomSheet), findsNothing);
    },
  );

  testWidgets(
    'manual no-update and network failure provide visible feedback and retry',
    (tester) async {
      GetIt.I.registerSingleton<UpdateManager>(manager);
      service.installed = Version.parse('2.1.0');
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showAppUpdateSheet(context, manual: true),
                child: const Text('Check'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Check'));
      await tester.pumpAndSettle();
      expect(find.text('You are using the latest version.'), findsOneWidget);
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      service.failCheck = true;
      await tester.tap(find.text('Check'));
      await tester.pumpAndSettle();
      expect(find.text('Try again'), findsOneWidget);
      service.failCheck = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('You are using the latest version.'), findsOneWidget);
    },
  );
}
