import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';

class SettingsStore {
  static const String _boxName = 'settings';
  static const String _themeModeKey = 'theme_mode';
  static const String _appLocaleKey = 'app_locale';
  static const String _autoUpdateIndexOnStartupKey =
      'auto_update_index_on_startup';
  static const String _searchResultLimitKey = 'search_result_limit';
  static const String _recentSearchesKey = 'recent_searches';
  static const String _recentViewedPhotosKey = 'recent_viewed_photos';
  static const String _privacyAgreementAcceptedKey =
      'privacy_agreement_accepted';

  static String? getSkippedUpdateVersion() =>
      _box.get('skipped_update_version') as String?;

  static Future<void> setSkippedUpdateVersion(String version) =>
      _box.put('skipped_update_version', version);

  static Future<void> init() async {
    await Hive.initFlutter();
    await Hive.openBox(_boxName);
  }

  static Box get _box => Hive.box(_boxName);

  static ThemeMode getThemeMode() {
    final index =
        _box.get(_themeModeKey, defaultValue: ThemeMode.system.index) as int;
    return ThemeMode.values[index];
  }

  static Future<void> setThemeMode(ThemeMode mode) async {
    await _box.put(_themeModeKey, mode.index);
  }

  static String getAppLocaleName() {
    return _box.get(_appLocaleKey, defaultValue: 'system') as String;
  }

  static Future<void> setAppLocaleName(String name) async {
    await _box.put(_appLocaleKey, name);
  }

  static bool getAutoUpdateIndexOnStartup() {
    return _box.get(_autoUpdateIndexOnStartupKey, defaultValue: false) == true;
  }

  static Future<void> setAutoUpdateIndexOnStartup(bool enabled) async {
    await _box.put(_autoUpdateIndexOnStartupKey, enabled);
  }

  static int getSearchResultLimit({required int defaultValue}) {
    final value = _box.get(_searchResultLimitKey, defaultValue: defaultValue);
    return value is int ? value : defaultValue;
  }

  static Future<void> setSearchResultLimit(int limit) async {
    await _box.put(_searchResultLimitKey, limit);
  }

  static List<String> getRecentSearches() {
    final value = _box.get(_recentSearchesKey);
    if (value is List) return value.cast<String>();
    return const [];
  }

  static Future<void> setRecentSearches(List<String> searches) async {
    await _box.put(_recentSearchesKey, searches);
  }

  static List<String> getRecentViewedPhotos() {
    final value = _box.get(_recentViewedPhotosKey);
    if (value is List) return value.whereType<String>().toList();
    return const [];
  }

  static Future<void> setRecentViewedPhotos(List<String> paths) async {
    await _box.put(_recentViewedPhotosKey, paths);
  }

  static bool getPrivacyAgreementAccepted() =>
      _box.get(_privacyAgreementAcceptedKey, defaultValue: false) == true;

  static Future<void> acceptPrivacyAgreement() async {
    await _box.put(_privacyAgreementAcceptedKey, true);
  }
}
