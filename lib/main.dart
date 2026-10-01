import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/models_config.dart';
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

final _log = Logger('main');

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  configureLogging();
  await SettingsStore.init();
  configureDependencies();

  try {
    await _initDatabase();
  } catch (_) {
    _log.severe('Database initialization failed.');
  }

  initClipModels().catchError((_) {
    _log.severe('CLIP model initialization failed.');
  });

  initTranslationModel().catchError((_) {
    _log.severe('Translation model initialization failed.');
  });

  runApp(PicQueryApp());
}

Future<void> _initDatabase() async {
  final appDir = await getApplicationDocumentsDirectory();
  final dbPath = '${appDir.path}/picquery_v2.db';
  await initDb(dbPath: dbPath);
}

class PicQueryApp extends WatchingWidget {
  const PicQueryApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seedColor = Color(0xFF0478D7);
    final themeMode = watchValue((ThemeManager m) => m.themeMode);
    final locale = watchValue((LocaleManager m) => m.locale);
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
      home: AppShell(),
      navigatorObservers: [FlutterSmartDialog.observer],
      builder: FlutterSmartDialog.init(),
    );
  }
}
