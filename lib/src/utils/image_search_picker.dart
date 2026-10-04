import 'package:file_picker/file_picker.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:flutter/widgets.dart';
import 'package:picquery_app/src/utils/localization.dart';

/// Opens the image picker and runs an image-similarity search against the
/// given album scope. Returns null if the user cancelled the picker; throws
/// if the search fails.
Future<List<SearchResult>?> pickImageAndSearch(
  BuildContext context,
  List<int> albumIds, {
  int limit = 50,
  int? modifiedAfter,
}) async {
  final file = await FilePicker.pickFile(
    type: FileType.custom,
    allowedExtensions: ['jpg', 'jpeg', 'png', 'webp', 'bmp'],
    dialogTitle: context.l10n.selectImageToSearch,
  );

  if (file == null) return null;

  final filePath = file.path;
  if (filePath == null) return null;

  return searchByImageWithFilters(
    imagePath: filePath,
    limit: limit,
    albumIds: albumIds,
    modifiedAfter: modifiedAfter,
  );
}
