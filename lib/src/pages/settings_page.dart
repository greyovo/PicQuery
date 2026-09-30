import 'package:flutter/material.dart';
import 'package:picquery_app/src/managers/folder_manager.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/search_manager.dart';
import 'package:picquery_app/src/managers/theme_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/managers/locale_manager.dart';
import 'package:picquery_app/src/utils/models_config.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:watch_it/watch_it.dart';

class SettingsPage extends WatchingWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final themeMode = watchValue((ThemeManager m) => m.themeMode);
    final appLocale = watchValue((LocaleManager m) => m.selection);
    final autoUpdateIndexOnStartup = watchValue(
      (IndexingManager m) => m.autoUpdateIndexOnStartup,
    );

    return Scaffold(
      appBar: AppBar(title: Text(context.l10n.settings)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          _buildSection(
            context,
            icon: Icons.palette_outlined,
            title: context.l10n.appearance,
            children: [
              _buildThemeModeRow(
                context,
                icon: Icons.light_mode_outlined,
                label: context.l10n.light,
                value: ThemeMode.light,
                current: themeMode,
              ),
              _buildThemeModeRow(
                context,
                icon: Icons.dark_mode_outlined,
                label: context.l10n.dark,
                value: ThemeMode.dark,
                current: themeMode,
              ),
              _buildThemeModeRow(
                context,
                icon: Icons.brightness_auto_outlined,
                label: context.l10n.followSystem,
                value: ThemeMode.system,
                current: themeMode,
              ),
            ],
          ),
          const SizedBox(height: 16),
          _buildSection(
            context,
            icon: Icons.language_rounded,
            title: context.l10n.language,
            children: AppLocale.values
                .map(
                  (value) => ListTile(
                    leading: Icon(_localeIcon(value)),
                    title: Text(_localeLabel(context, value)),
                    trailing: value == appLocale
                        ? Icon(
                            Icons.check_rounded,
                            color: context.colors.primary,
                          )
                        : null,
                    onTap: () => localeManager.setLocale(value),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 16),
          _buildSection(
            context,
            icon: Icons.update_outlined,
            title: context.l10n.indexUpdates,
            children: [
              SwitchListTile(
                secondary: const Icon(Icons.autorenew),
                title: Text(context.l10n.autoUpdateOnStartup),
                subtitle: Text(context.l10n.autoUpdateOnStartupDescription),
                value: autoUpdateIndexOnStartup,
                onChanged: indexingManager.setAutoUpdateIndexOnStartup,
              ),
            ],
          ),
          const SizedBox(height: 16),
          _buildSection(
            context,
            icon: Icons.memory,
            title: context.l10n.model,
            children: [_ReloadModelTile()],
          ),
          const SizedBox(height: 16),
          _buildSection(
            context,
            icon: Icons.storage_outlined,
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
            ],
          ),
        ],
      ),
    );
  }

  IconData _localeIcon(AppLocale locale) => switch (locale) {
    AppLocale.system => Icons.settings_suggest_outlined,
    AppLocale.simplifiedChinese => Icons.translate_rounded,
    AppLocale.traditionalChinese => Icons.translate_rounded,
    AppLocale.english => Icons.abc_rounded,
  };

  String _localeLabel(BuildContext context, AppLocale locale) =>
      switch (locale) {
        AppLocale.system => context.l10n.systemLanguage,
        AppLocale.simplifiedChinese => context.l10n.simplifiedChinese,
        AppLocale.traditionalChinese => context.l10n.traditionalChinese,
        AppLocale.english => context.l10n.english,
      };

  Widget _buildSection(
    BuildContext context, {
    required IconData icon,
    required String title,
    required List<Widget> children,
  }) {
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.hardEdge,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            child: Row(
              children: [
                Icon(icon, size: 20, color: context.colors.primary),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: context.colors.primary,
                  ),
                ),
              ],
            ),
          ),
          ...children,
        ],
      ),
    );
  }

  Widget _buildThemeModeRow(
    BuildContext context, {
    required IconData icon,
    required String label,
    required ThemeMode value,
    required ThemeMode current,
  }) {
    final isSelected = current == value;
    return ListTile(
      leading: Icon(icon, size: 20),
      title: Text(label),
      trailing: isSelected
          ? Icon(Icons.check, size: 20, color: context.colors.primary)
          : null,
      onTap: () => themeManager.setThemeMode(value),
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
      await deleteAllFolders();
      await folderManager.reload();
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
