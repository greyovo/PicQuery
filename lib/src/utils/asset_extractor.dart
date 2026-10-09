import 'dart:io';
import 'dart:isolate';

import 'package:flutter/services.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:picquery_app/src/utils/models_config.dart';

final _log = Logger('asset_extractor');

/// Extracts ONNX model files from the Flutter asset bundle to the app's
/// persistent storage directory. Only copies if files don't already exist.
Future<String> extractClipModelAssets({
  bool force = false,
  void Function(int completed, int total)? onProgress,
}) async {
  final appDir = await getApplicationSupportDirectory();
  final modelsDir = Directory('${appDir.path}/models');

  if (!await modelsDir.exists()) {
    await modelsDir.create(recursive: true);
  }

  const modelFiles = [
    'assets/models/$kClipVisualModelPath',
    'assets/models/$kClipTextModelPath',
  ];

  // Bump whenever the bytes or I/O contract of a bundled CLIP model changes.
  // Existing installations previously kept stale extracted models forever.
  const assetRevision = '4-mobileclip2-s0-int8-text-fp16-visual';
  final revisionFile = File('${modelsDir.path}/.clip-model-revision');
  final installedRevision = await revisionFile.exists()
      ? await revisionFile.readAsString()
      : null;
  final refresh = force || installedRevision != assetRevision;

  var completed = 0;
  onProgress?.call(completed, modelFiles.length);
  for (final assetPath in modelFiles) {
    final fileName = assetPath.split('/').last;
    final targetFile = File('${modelsDir.path}/$fileName');

    if (!await targetFile.exists() ||
        await targetFile.length() == 0 ||
        refresh) {
      _log.info('Extracting bundled CLIP model asset.');
      await _copyAsset(assetPath, targetFile.path);
    }
    onProgress?.call(++completed, modelFiles.length);
  }

  if (refresh) {
    await revisionFile.writeAsString(assetRevision, flush: true);
  }

  return modelsDir.path;
}

/// Extracts translation model files (ONNX model and tokenizer JSON files)
/// from the Flutter asset bundle to the app's persistent storage directory.
/// Only copies if files don't already exist.
Future<String> extractTranslationModelAssets({
  bool force = false,
  void Function(int completed, int total)? onProgress,
}) async {
  final appDir = await getApplicationSupportDirectory();
  final modelsDir = Directory('${appDir.path}/models');

  if (!await modelsDir.exists() || force) {
    await modelsDir.create(recursive: true);
  }

  const modelFiles = [
    'assets/models/$kTranslationModelPath',
    'assets/models/$kSourceSpModelPath',
    'assets/models/$kTargetSpModelPath',
  ];

  var completed = 0;
  onProgress?.call(completed, modelFiles.length);
  for (final assetPath in modelFiles) {
    final fileName = assetPath.split('/').last;
    final targetFile = File('${modelsDir.path}/$fileName');

    if (!await targetFile.exists() || await targetFile.length() == 0 || force) {
      _log.info('Extracting bundled translation model asset.');
      await _copyAsset(assetPath, targetFile.path);
    }
    onProgress?.call(++completed, modelFiles.length);
  }

  return modelsDir.path;
}

/// Asset loading uses Flutter's asynchronous bundle API. Write on a worker and
/// install via a temporary file, so interrupted first launches cannot leave a
/// partial model that a later launch mistakes for a usable one.
Future<void> _copyAsset(String assetPath, String targetPath) async {
  final data = await rootBundle.load(assetPath);
  if (data.lengthInBytes == 0) {
    throw StateError('Bundled model asset is empty: $assetPath');
  }
  // Transfer the buffer once rather than copying it when spawning the worker.
  final transferable = TransferableTypedData.fromList([
    Uint8List.sublistView(data),
  ]);
  await Isolate.run(() async {
    final bytes = transferable.materialize().asUint8List();
    final temporary = File('$targetPath.part');
    try {
      await temporary.writeAsBytes(bytes, flush: true);
      await temporary.rename(targetPath);
    } finally {
      if (await temporary.exists()) await temporary.delete();
    }
  });
}
