import 'package:flutter/foundation.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:watch_it/watch_it.dart';

AlbumManager get albumManager => di<AlbumManager>();

class AlbumManager {
  final albums = ValueNotifier<List<Album>>([]);
  final isLoading = ValueNotifier<bool>(false);

  AlbumManager() {
    _load();
  }

  Future<void> _load() async {
    isLoading.value = true;
    try {
      albums.value = await getAllAlbums();
    } finally {
      isLoading.value = false;
    }
  }

  Future<void> reload() => _load();

  Future<void> deleteAlbums(Set<int> ids) async {
    for (final id in ids) {
      await deleteAlbum(albumId: id);
    }
    await _load();
  }
}
