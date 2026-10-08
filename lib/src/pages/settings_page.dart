import 'package:flutter/material.dart';
import 'package:picquery_app/src/widgets/app_update_sheet.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:picquery_app/src/managers/album_manager.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/search_manager.dart';
import 'package:picquery_app/src/managers/theme_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/managers/locale_manager.dart';
import 'package:picquery_app/src/utils/models_config.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';
import 'package:picquery_app/src/pages/logs_page.dart';
import 'package:watch_it/watch_it.dart';
import 'package:url_launcher/url_launcher.dart';

class SettingsPage extends WatchingWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final themeMode = watchValue((ThemeManager m) => m.themeMode);
    final appLocale = watchValue((LocaleManager m) => m.selection);
    final resultLimit = watchValue((SearchManager m) => m.resultLimit);
    final autoUpdateIndexOnStartup = watchValue(
      (IndexingManager m) => m.autoUpdateIndexOnStartup,
    );

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 88,
        titleSpacing: 20,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        forceMaterialTransparency: true,
        title: Text(
          context.l10n.settings,
          style: Theme.of(context).textTheme.headlineMedium,
        ),
      ),
      body: ListTileTheme(
        data: ListTileThemeData(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 18,
            vertical: 4,
          ),
        ),
        child: ListView(
          children: [
            _buildSection(
              context,
              title: context.l10n.general,
              children: [
                _buildSelectionRow<ThemeMode>(
                  icon: Icons.palette_outlined,
                  title: context.l10n.appearance,
                  value: themeMode,
                  values: ThemeMode.values,
                  labelFor: (value) => _themeModeLabel(context, value),
                  iconFor: _themeModeIcon,
                  onChanged: themeManager.setThemeMode,
                ),
                _buildSelectionRow<AppLocale>(
                  icon: Icons.language_rounded,
                  title: context.l10n.language,
                  value: appLocale,
                  values: AppLocale.values,
                  labelFor: (value) => _localeLabel(context, value),
                  onChanged: localeManager.setLocale,
                ),
                _buildActionRow(
                  context,
                  icon: Icons.system_update_alt,
                  label: context.l10n.checkAppUpdates,
                  onTap: () => showAppUpdateSheet(context, manual: true),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _buildSection(
              context,
              title: context.l10n.search,
              children: [
                _buildSelectionRow<int>(
                  icon: Icons.format_list_numbered_rounded,
                  title: context.l10n.searchResultCount,
                  value: resultLimit,
                  values: SearchManager.resultLimitOptions,
                  labelFor: (value) => '$value',
                  onChanged: searchManager.setResultLimit,
                ),
                SwitchListTile(
                  secondary: const Icon(Icons.autorenew),
                  title: Text(context.l10n.autoUpdateOnStartup),
                  value: autoUpdateIndexOnStartup,
                  onChanged: indexingManager.setAutoUpdateIndexOnStartup,
                ),
                _ReloadModelTile(),
              ],
            ),
            const SizedBox(height: 12),
            _buildSection(
              context,
              title: context.l10n.dataManagement,
              children: [
                _buildActionRow(
                  context,
                  icon: Icons.delete_forever_outlined,
                  label: context.l10n.clearAllIndexes,
                  onTap: () => _deleteAllIndexes(context),
                  textColor: context.colors.error,
                  iconColor: context.colors.error,
                ),
                _buildActionRow(
                  context,
                  icon: Icons.history_toggle_off_outlined,
                  label: context.l10n.clearRecentSearches,
                  onTap: () => _clearRecentSearches(context),
                ),
                _buildActionRow(
                  context,
                  icon: Icons.description_outlined,
                  label: context.l10n.viewLogs,
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(builder: (_) => const LogsPage()),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 28),
            const _AppInfoFooter(),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  String _localeLabel(BuildContext context, AppLocale locale) =>
      switch (locale) {
        AppLocale.system => context.l10n.systemLanguage,
        AppLocale.simplifiedChinese => context.l10n.simplifiedChinese,
        AppLocale.traditionalChinese => context.l10n.traditionalChinese,
        AppLocale.english => context.l10n.english,
      };

  IconData _themeModeIcon(ThemeMode mode) => switch (mode) {
    ThemeMode.light => Icons.light_mode_outlined,
    ThemeMode.dark => Icons.dark_mode_outlined,
    ThemeMode.system => Icons.brightness_auto_outlined,
  };

  String _themeModeLabel(BuildContext context, ThemeMode mode) =>
      switch (mode) {
        ThemeMode.light => context.l10n.light,
        ThemeMode.dark => context.l10n.dark,
        ThemeMode.system => context.l10n.followSystem,
      };

  Widget _buildSection(
    BuildContext context, {
    required String title,
    required List<Widget> children,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
          child: Text(
            title,
            style: Theme.of(context).textTheme.titleSmall
                ?.copyWith(color: context.colors.primary),
          ),
        ),
        ...children,
      ],
    );
  }

  Widget _buildSelectionRow<T>({
    required IconData icon,
    required String title,
    required T value,
    required List<T> values,
    required String Function(T value) labelFor,
    IconData Function(T value)? iconFor,
    required ValueChanged<T> onChanged,
  }) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      trailing: AppMenuButton<T>(
        selectedValue: value,
        onSelected: onChanged,
        items: values
            .map(
              (option) => AppMenuItem<T>(
                value: option,
                leadingIcon: iconFor == null
                    ? null
                    : Icon(iconFor(option), size: 18),
                child: Text(labelFor(option)),
              ),
            )
            .toList(),
        builder: (context, controller, child) => TextButton.icon(
          onPressed: () =>
              controller.isOpen ? controller.close() : controller.open(),
          iconAlignment: IconAlignment.end,
          icon: const Icon(Icons.arrow_drop_down),
          style: TextButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
          label: Text(labelFor(value)),
        ),
      ),
    );
  }

  Widget _buildActionRow(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    Color? textColor,
    Color? iconColor,
  }) {
    return ListTile(
      leading: Icon(icon, color: iconColor),
      title: Text(
        label,
        style: textColor != null ? TextStyle(color: textColor) : null,
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }

  Future<void> _deleteAllIndexes(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(context.l10n.deleteAllIndexesTitle),
        content: Text(context.l10n.deleteAllIndexesMessage),
        actions: [
          FilledButton.tonal(
            onPressed: () => Navigator.pop(context, false),
            child: Text(context.l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(
              backgroundColor: context.colors.error,
            ),
            child: Text(context.l10n.delete),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await deleteAllAlbums();
      await albumManager.reload();
    }
  }

  Future<void> _clearRecentSearches(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(context.l10n.clearRecentSearchesTitle),
        content: Text(context.l10n.clearRecentSearchesMessage),
        actions: [
          FilledButton.tonal(
            onPressed: () => Navigator.pop(context, false),
            child: Text(context.l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(context.l10n.clear),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await searchManager.clearRecentSearches();
      if (context.mounted) {
        Toast.showMessage(context.l10n.recentSearchesCleared);
      }
    }
  }
}

class _AppInfoFooter extends StatefulWidget {
  const _AppInfoFooter();

  @override
  State<_AppInfoFooter> createState() => _AppInfoFooterState();
}

class _AppInfoFooterState extends State<_AppInfoFooter> {
  static const String _rawBuildDate = String.fromEnvironment(
    'BUILD_DATE',
    defaultValue: '—',
  );
  static final Uri _repositoryUri = Uri.parse(
    'https://github.com/greyovo/PicQuery',
  );

  late final Future<PackageInfo> _packageInfo = PackageInfo.fromPlatform();

  String get _buildDate {
    final match = RegExp(r'^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})\+08:00$')
        .firstMatch(_rawBuildDate);
    if (match == null) return '—';
    return '${match.group(1)} ${match.group(2)} UTC+8';
  }

  @override
  Widget build(BuildContext context) {
    final textStyle = Theme.of(context).textTheme.bodySmall
        ?.copyWith(color: context.colors.onSurfaceVariant);
    final linkStyle = TextButton.styleFrom(
      foregroundColor: context.colors.onSurfaceVariant,
      textStyle: textStyle?.copyWith(decoration: TextDecoration.underline),
      padding: EdgeInsets.zero,
      minimumSize: Size.zero,
      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
    );

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 18),
      child: DefaultTextStyle(
        style: textStyle ?? const TextStyle(fontSize: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            FutureBuilder<PackageInfo>(
              future: _packageInfo,
              builder: (context, snapshot) {
                final info = snapshot.data;
                final version = info == null
                    ? '—'
                    : '${info.version}+${info.buildNumber}';
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${context.l10n.currentVersion}: $version'),
                    const SizedBox(height: 4),
                    Text('${context.l10n.buildDate}: $_buildDate'),
                  ],
                );
              },
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Text('${context.l10n.githubRepository}: '),
                Flexible(
                  child: TextButton(
                    style: linkStyle,
                    onPressed: () => launchUrl(_repositoryUri),
                    child: Text(_repositoryUri.toString()),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            TextButton(
              style: linkStyle,
              onPressed: () => showLicensePage(
                context: context,
                applicationName: context.l10n.appTitle,
              ),
              child: Text(context.l10n.openSourceLicenses),
            ),
          ],
        ),
      ),
    );
  }
}

class _ReloadModelTile extends StatefulWidget {
  @override
  State<_ReloadModelTile> createState() => _ReloadModelTileState();
}

class _ReloadModelTileState extends State<_ReloadModelTile> {
  bool _isLoadingModel = false;

  Future<void> _reloadModels() async {
    try {
      setState(() => _isLoadingModel = true);
      await Future.microtask(() async {
        await Future.wait([
          initClipModels(force: true),
          initTranslationModel(force: true),
        ]);
      });
      if (!mounted) return;
      setState(() => _isLoadingModel = false);
      Toast.showMessage(context.l10n.modelsReloaded);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isLoadingModel = false);
      Toast.showMessage(context.l10n.modelsReloadFailed(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: const Icon(Icons.refresh_outlined),
      title: Text(context.l10n.reloadModels),
      trailing: _isLoadingModel
          ? const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.chevron_right),
      onTap: _reloadModels,
    );
  }
}
