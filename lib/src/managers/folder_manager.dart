import 'package:flutter/foundation.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:watch_it/watch_it.dart';

FolderManager get folderManager => di<FolderManager>();

class FolderManager {
  final folders = ValueNotifier<List<Folder>>([]);
  final isLoading = ValueNotifier<bool>(false);

  FolderManager() {
    _load();
  }

  Future<void> _load() async {
    isLoading.value = true;
    try {
      folders.value = await getAllFolders();
    } finally {
      isLoading.value = false;
    }
  }

  Future<void> reload() => _load();

  Future<void> deleteFolders(Set<int> ids) async {
    for (final id in ids) {
      await deleteFolder(folderId: id);
    }
    await _load();
  }
}
