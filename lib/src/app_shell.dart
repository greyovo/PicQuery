import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/pages/search_page.dart';
import 'package:picquery_app/src/pages/album_manage_page.dart';
import 'package:picquery_app/src/pages/settings_page.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/managers/folder_manager.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:watch_it/watch_it.dart';

class AppShell extends WatchingStatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _selectedIndex = 0;

  @override
  void initState() {
    super.initState();
    // Keep the update state current for the manage tab without interrupting
    // startup with success or error toasts.
    final indexing = indexingManager;
    unawaited(
      indexing.checkForUpdates(
        showToast: false,
        updateAutomatically: indexing.autoUpdateIndexOnStartup.value,
      ),
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_recommendAndroidDcim());
    });
  }

  Future<void> _recommendAndroidDcim() async {
    if (!Platform.isAndroid || SettingsStore.getAndroidDcimPromptShown()) {
      return;
    }
    await SettingsStore.setAndroidDcimPromptShown();
    await folderManager.reload();
    if (!mounted || folderManager.folders.value.isNotEmpty) return;
    final shouldIndex = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(context.l10n.indexDcimTitle),
        content: Text(context.l10n.indexDcimMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(context.l10n.notNow),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(context.l10n.startIndexing),
          ),
        ],
      ),
    );
    if (shouldIndex == true && mounted) {
      await indexingManager.indexRecommendedAndroidAlbum(context: context);
    }
  }

  double get _indexProgress {
    final indexing = indexingManager;
    return indexing.total > 0 ? indexing.current / indexing.total : 0.0;
  }

  static const _padding = EdgeInsets.symmetric(vertical: 6);

  // ─── Tab icon ───

  Widget _buildManageTabIcon(IndexingManager indexing) {
    if (indexing.isIndexing && _indexProgress > 0.0) {
      return SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(value: _indexProgress, strokeWidth: 2),
      );
    }
    final icon = const Icon(Icons.folder_outlined);
    if (indexing.albumUpdateStatus == AlbumUpdateStatus.updateAvailable) {
      return Badge(child: icon);
    }
    return icon;
  }

  String _buildManageTabLabel(BuildContext context, IndexingManager indexing) {
    if (indexing.isIndexing && _indexProgress > 0.0) {
      final percentage = (_indexProgress * 100).round();
      return '$percentage%';
    }
    return context.l10n.albums;
  }

  // ─── Build ───

  @override
  Widget build(BuildContext context) {
    final indexing = indexingManager;
    watch(indexing);

    registerStreamHandler<IndexingManager, void>(
      select: (IndexingManager m) => m.onNavigateToManageTab,
      handler: (context, _, __) {
        setState(() {
          _selectedIndex = 1;
        });
      },
    );

    final bool isLarge = context.isLargeScreen;

    final navigationRailDestinations = [
      NavigationRailDestination(
        icon: const Icon(Icons.search),
        label: Text(context.l10n.search),
        padding: _padding,
      ),
      NavigationRailDestination(
        icon: _buildManageTabIcon(indexing),
        label: Text(_buildManageTabLabel(context, indexing)),
        padding: _padding,
      ),
      NavigationRailDestination(
        icon: const Icon(Icons.settings_outlined),
        label: Text(context.l10n.settings),
        padding: _padding,
      ),
    ];

    final bottomNavDestinations = [
      NavigationDestination(
        icon: const Icon(Icons.search),
        label: context.l10n.search,
      ),
      NavigationDestination(
        icon: _buildManageTabIcon(indexing),
        label: _buildManageTabLabel(context, indexing),
      ),
      NavigationDestination(
        icon: const Icon(Icons.settings_outlined),
        label: context.l10n.settings,
      ),
    ];

    return Scaffold(
      body: Row(
        children: [
          if (isLarge)
            NavigationRail(
              selectedIndex: _selectedIndex,
              onDestinationSelected: (index) => setState(() {
                _selectedIndex = index;
              }),
              destinations: navigationRailDestinations,
              labelType: NavigationRailLabelType.all,
              groupAlignment: Alignment.center.y,
              backgroundColor: context.colors.surfaceContainerLow,
            ),
          Expanded(
            child: IndexedStack(
              index: _selectedIndex,
              children: [
                const SearchPage(),
                const AlbumManagePage(),
                const SettingsPage(),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: isLarge
          ? null
          : NavigationBar(
              selectedIndex: _selectedIndex,
              onDestinationSelected: (index) => setState(() {
                _selectedIndex = index;
              }),
              destinations: bottomNavDestinations,
            ),
    );
  }
}
