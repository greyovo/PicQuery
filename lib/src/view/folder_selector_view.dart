import 'package:flutter/material.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/folder_manager.dart';
import 'package:picquery_app/src/managers/search_manager.dart';
import 'package:picquery_app/src/widgets/folder_list_item.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:watch_it/watch_it.dart';

class FolderSelectorView extends WatchingStatefulWidget {
  final Set<int> initialSelection;

  const FolderSelectorView({super.key, required this.initialSelection});

  @override
  State<FolderSelectorView> createState() => _FolderSelectorViewState();
}

class _FolderSelectorViewState extends State<FolderSelectorView> {
  late Set<int> _selectedIds;
  String _filterQuery = '';

  @override
  void initState() {
    super.initState();
    _selectedIds = Set.from(widget.initialSelection);
  }

  void _toggleSelectAll(List<Folder> filteredFolders) {
    if (filteredFolders.isEmpty) return;
    setState(() {
      if (_selectedIds.length == filteredFolders.length) {
        _selectedIds.clear();
      } else {
        _selectedIds = filteredFolders.map((f) => f.id.toInt()).toSet();
      }
    });
  }

  void _confirm() {
    searchManager.setSelectedFolderIds(_selectedIds);
    Navigator.of(context, rootNavigator: true).pop();
  }

  @override
  Widget build(BuildContext context) {
    final folders = watchValue((FolderManager m) => m.folders);
    final isLoading = watchValue((FolderManager m) => m.isLoading);

    final filteredFolders = _filterQuery.isEmpty
        ? folders
        : folders
              .where(
                (f) => f.folderPath.toLowerCase().contains(
                  _filterQuery.toLowerCase(),
                ),
              )
              .toList();

    final rootNav = Navigator.of(context, rootNavigator: true);
    final allSelected =
        _selectedIds.length == filteredFolders.length &&
        filteredFolders.isNotEmpty;

    return Scaffold(
      appBar: AppBar(title: Text(context.l10n.selectScope)),
      bottomNavigationBar: Container(
        alignment: Alignment.centerRight,
        height: 70,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        color: context.colors.surfaceContainer,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            FilledButton.tonal(
              onPressed: () => rootNav.pop(),
              child: Text(context.l10n.cancel),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _confirm,
              child: Text(context.l10n.confirm),
            ),
          ],
        ),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextField(
              decoration: InputDecoration(
                hintText: context.l10n.filterFolders,
                prefixIcon: const Icon(Icons.search),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              onChanged: (value) => setState(() => _filterQuery = value),
            ),
          ),
          if (!isLoading)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: TextButton.icon(
                onPressed: filteredFolders.isEmpty
                    ? null
                    : () => _toggleSelectAll(filteredFolders),
                icon: Icon(
                  allSelected ? Icons.check_box : Icons.check_box_outline_blank,
                ),
                label: Text(context.l10n.selectAll),
              ),
            ),
          const SizedBox(height: 8),
          Expanded(
            child: isLoading
                ? const Center(child: CircularProgressIndicator())
                : filteredFolders.isEmpty
                ? Center(child: Text(context.l10n.noFoldersFound))
                : ListView.builder(
                    itemCount: filteredFolders.length,
                    itemBuilder: (context, index) {
                      final folder = filteredFolders[index];
                      final isSelected = _selectedIds.contains(
                        folder.id.toInt(),
                      );
                      return FolderListItem(
                        folder: folder,
                        selected: isSelected,
                        onSelected: (selected) {
                          setState(() {
                            if (selected) {
                              _selectedIds.add(folder.id.toInt());
                            } else {
                              _selectedIds.remove(folder.id.toInt());
                            }
                          });
                        },
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
