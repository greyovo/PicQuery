import 'dart:async';

import 'package:picquery_app/src/widgets/app_update_sheet.dart';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/pages/search_page.dart';
import 'package:picquery_app/src/widgets/desktop_album_drop_target.dart';
import 'package:picquery_app/src/pages/album_manage_page.dart';
import 'package:picquery_app/src/pages/settings_page.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
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
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(checkAppUpdatesOnStartup(context));
    });
    // Keep the update state current for the manage tab without interrupting
    // startup with success or error toasts.
    final indexing = indexingManager;
    unawaited(
      indexing.checkForUpdates(
        showToast: false,
        updateAutomatically: indexing.autoUpdateIndexOnStartup.value,
      ),
    );
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

    final bool useNavigationRail = context.useNavigationRail;

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

    Widget appBody = IndexedStack(
      index: _selectedIndex,
      children: [
        const SearchPage(),
        const AlbumManagePage(),
        const SettingsPage(),
      ],
    );
    if (context.isLargeScreen) {
      appBody = Padding(
        padding: const EdgeInsets.fromLTRB(0, 0, 12.0, 12.0),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: appBody,
        ),
      );
    }

    final scaffold = Scaffold(
      backgroundColor: context.colors.surfaceContainer,
      body: Row(
        children: [
          if (useNavigationRail)
            NavigationRail(
              selectedIndex: _selectedIndex,
              onDestinationSelected: (index) => setState(() {
                _selectedIndex = index;
              }),
              destinations: navigationRailDestinations,
              labelType: NavigationRailLabelType.all,
              groupAlignment: Alignment.center.y,
              backgroundColor: context.colors.surfaceContainer,
            ),
          Expanded(child: appBody),
        ],
      ),
      bottomNavigationBar: useNavigationRail
          ? null
          : NavigationBar(
              backgroundColor: context.colors.surfaceContainer,
              selectedIndex: _selectedIndex,
              onDestinationSelected: (index) => setState(() {
                _selectedIndex = index;
              }),
              destinations: bottomNavDestinations,
            ),
    );

    return ProgressIndicatorTheme(
      // ignore: deprecated_member_use
      data: ProgressIndicatorThemeData(year2023: false),
      child: isDesktop
          ? DesktopAlbumDropTarget(
              onFolderDropped: (selection) =>
                  indexing.indexDroppedAlbum(context, selection),
              child: scaffold,
            )
          : scaffold,
    );
  }
}
