import 'package:flutter/material.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:watch_it/watch_it.dart';

LocaleManager get localeManager => di<LocaleManager>();

enum AppLocale { system, simplifiedChinese, traditionalChinese, english }

class LocaleManager {
  final locale = ValueNotifier<Locale?>(null);
  final selection = ValueNotifier<AppLocale>(AppLocale.system);

  LocaleManager() {
    final savedName = SettingsStore.getAppLocaleName();
    final saved = AppLocale.values.firstWhere(
      (value) => value.name == savedName,
      orElse: () => AppLocale.system,
    );
    selection.value = saved;
    locale.value = _localeFor(saved);
  }

  Future<void> setLocale(AppLocale value) async {
    selection.value = value;
    locale.value = _localeFor(value);
    await SettingsStore.setAppLocaleName(value.name);
  }

  Locale? _localeFor(AppLocale value) => switch (value) {
    AppLocale.system => null,
    AppLocale.simplifiedChinese => const Locale('zh'),
    AppLocale.traditionalChinese => const Locale.fromSubtags(
      languageCode: 'zh',
      scriptCode: 'Hant',
    ),
    AppLocale.english => const Locale('en'),
  };
}
