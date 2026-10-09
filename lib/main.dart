import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:picquery_app/src/utils/app_startup.dart';
import 'package:picquery_app/src/widgets/startup_gate.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:picquery_app/src/managers/theme_manager.dart';
import 'package:picquery_app/src/managers/locale_manager.dart';
import 'package:picquery_app/l10n/generated/app_localizations.dart';
import 'package:picquery_app/src/locator.dart';
import 'package:picquery_app/src/app_shell.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:watch_it/watch_it.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/utils/app_logger.dart';
import 'package:picquery_app/src/utils/error_reporting.dart';
import 'package:picquery_app/src/widgets/desktop_window_frame.dart';
import 'package:picquery_app/src/widgets/privacy_agreement_gate.dart';
import 'package:window_manager/window_manager.dart';

final _log = Logger('main');

void main() async {
  runZonedGuarded(
    () async {
      WidgetsFlutterBinding.ensureInitialized();
      await configureLogging();
      _installGlobalErrorHandlers();
      _log.info(">>>>>>>>>>>>>>>>>>>>>");
      _log.info(">>>> App started >>>>");
      _log.info(">>>>>>>>>>>>>>>>>>>>>");

      try {
        final packageInfo = await PackageInfo.fromPlatform();
        _log.info(
          'App version: ${packageInfo.version}+${packageInfo.buildNumber}',
        );
      } catch (error, stackTrace) {
        _log.warning('Failed to read app version.', error, stackTrace);
      }

      await _configureDesktopWindow();
      await SettingsStore.init();
      await ErrorReporting.initialize();
      configureDependencies();

      runApp(PicQueryApp());
    },
    (error, stackTrace) {
      _log.severe('Unhandled asynchronous error.', error, stackTrace);
      unawaited(AppLogger.instance.flush());
    },
  );
}

void _installGlobalErrorHandlers() {
  FlutterError.onError = (details) {
    FlutterError.presentError(details);
    _log.severe(
      'Unhandled Flutter framework error.',
      details.exception,
      details.stack,
    );
    unawaited(AppLogger.instance.flush());
  };

  PlatformDispatcher.instance.onError = (error, stackTrace) {
    _log.severe('Unhandled platform error.', error, stackTrace);
    unawaited(AppLogger.instance.flush());
    return true;
  };
}

Future<void> _configureDesktopWindow() async {
  if (!Platform.isMacOS && !Platform.isWindows && !Platform.isLinux) return;

  await windowManager.ensureInitialized();
  const windowOptions = WindowOptions(
    titleBarStyle: TitleBarStyle.hidden,
    windowButtonVisibility: true,
    backgroundColor: Colors.transparent,
  );
  await windowManager.waitUntilReadyToShow(windowOptions, () async {
    await windowManager.show();
    await windowManager.focus();
  });
}

class PicQueryApp extends WatchingWidget {
  const PicQueryApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seedColor = Color(0xFF0478D7);
    final themeMode = watchValue((ThemeManager m) => m.themeMode);
    final locale = watchValue((LocaleManager m) => m.locale);
    final smartDialogBuilder = FlutterSmartDialog.init();
    return MaterialApp(
      title: 'PicQuery',
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      themeMode: themeMode,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seedColor),
        useMaterial3: true,
        fontFamily: 'Microsoft YaHei',
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: seedColor,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        fontFamily: 'Microsoft YaHei',
      ),
      home: const StartupGate(
        initialize: initializeApp,
        child: PrivacyAgreementGate(child: AppShell()),
      ),
      navigatorObservers: [FlutterSmartDialog.observer],
      builder: (context, child) =>
          DesktopWindowFrame(child: smartDialogBuilder(context, child)),
    );
  }
}
