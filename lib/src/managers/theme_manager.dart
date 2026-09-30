import 'package:flutter/material.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:watch_it/watch_it.dart';

ThemeManager get themeManager => di<ThemeManager>();

class ThemeManager {
  final themeMode = ValueNotifier<ThemeMode>(ThemeMode.system);

  ThemeManager() {
    themeMode.value = SettingsStore.getThemeMode();
  }

  void setThemeMode(ThemeMode mode) {
    SettingsStore.setThemeMode(mode);
    themeMode.value = mode;
  }
}
