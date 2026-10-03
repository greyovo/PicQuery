import 'package:flutter/material.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/managers/album_manager.dart';
import 'package:picquery_app/src/managers/search_manager.dart';
import 'package:picquery_app/src/widgets/album_list_item.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:watch_it/watch_it.dart';

class AlbumSelectorView extends WatchingStatefulWidget {
  final Set<int> initialSelection;

  const AlbumSelectorView({super.key, required this.initialSelection});

  @override
  State<AlbumSelectorView> createState() => _AlbumSelectorViewState();
}

class _AlbumSelectorViewState extends State<AlbumSelectorView> {
  late Set<int> _selectedIds;
  String _filterQuery = '';

  @override
  void initState() {
    super.initState();
    _selectedIds = Set.from(widget.initialSelection);
  }

  void _toggleSelectAll(List<Album> filteredAlbums) {
    if (filteredAlbums.isEmpty) return;
    setState(() {
      if (_selectedIds.length == filteredAlbums.length) {
        _selectedIds.clear();
      } else {
        _selectedIds = filteredAlbums.map((f) => f.id.toInt()).toSet();
      }
    });
  }

  void _confirm() {
    searchManager.setSelectedAlbumIds(_selectedIds);
    Navigator.of(context, rootNavigator: true).pop();
  }

  @override
  Widget build(BuildContext context) {
    final albums = watchValue((AlbumManager m) => m.albums);
    final isLoading = watchValue((AlbumManager m) => m.isLoading);

    final filteredAlbums = _filterQuery.isEmpty
        ? albums
        : albums
              .where(
                (f) => f.albumPath.toLowerCase().contains(
                  _filterQuery.toLowerCase(),
                ),
              )
              .toList();

    final rootNav = Navigator.of(context, rootNavigator: true);
    final allSelected =
        _selectedIds.length == filteredAlbums.length &&
        filteredAlbums.isNotEmpty;

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
                hintText: context.l10n.filterAlbums,
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
                onPressed: filteredAlbums.isEmpty
                    ? null
                    : () => _toggleSelectAll(filteredAlbums),
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
                : filteredAlbums.isEmpty
                ? Center(child: Text(context.l10n.noAlbumsFound))
                : ListView.builder(
                    itemCount: filteredAlbums.length,
                    itemBuilder: (context, index) {
                      final album = filteredAlbums[index];
                      final isSelected = _selectedIds.contains(
                        album.id.toInt(),
                      );
                      return AlbumListItem(
                        album: album,
                        selected: isSelected,
                        onSelected: (selected) {
                          setState(() {
                            if (selected) {
                              _selectedIds.add(album.id.toInt());
                            } else {
                              _selectedIds.remove(album.id.toInt());
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
