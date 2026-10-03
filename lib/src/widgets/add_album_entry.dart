import 'package:flutter/material.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:picquery_app/src/utils/localization.dart';

/// "添加相册" entry styled like [SearchScopeDisplay], shown below the scope
/// selector on the search tab (replaces the old floating action button).
class AddAlbumEntry extends StatelessWidget {
  const AddAlbumEntry({super.key});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      autofocus: false,
      contentPadding: const EdgeInsets.only(left: 16, right: 8, bottom: 2),
      title: Text(context.l10n.addAlbum),
      trailing: const Icon(Icons.add_photo_alternate_outlined),
      onTap: () {
        if (indexingManager.isIndexing) {
          Toast.showMessage(context.l10n.addAlbumWhileIndexing);
          return;
        }
        indexingManager.pickAndIndexAlbum(context);
      },
    );
  }
}
