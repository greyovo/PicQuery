import 'dart:io';

import 'package:flutter/services.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:picquery_app/src/utils/models_config.dart';

final _log = Logger('asset_extractor');

/// Extracts ONNX model files from the Flutter asset bundle to the app's
/// persistent storage directory. Only copies if files don't already exist.
Future<String> extractClipModelAssets({bool force = false}) async {
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
  const assetRevision = '3-mobileclip2-s0-fp16-fp32-io';
  final revisionFile = File('${modelsDir.path}/.clip-model-revision');
  final installedRevision = await revisionFile.exists()
      ? await revisionFile.readAsString()
      : null;
  final refresh = force || installedRevision != assetRevision;

  for (final assetPath in modelFiles) {
    final fileName = assetPath.split('/').last;
    final targetFile = File('${modelsDir.path}/$fileName');

    if (!await targetFile.exists() || refresh) {
      _log.info('Extracting bundled CLIP model asset.');
      final data = await rootBundle.load(assetPath);
      final bytes = data.buffer.asUint8List();
      await targetFile.writeAsBytes(bytes);
    }
  }

  if (refresh) {
    await revisionFile.writeAsString(assetRevision, flush: true);
  }

  return modelsDir.path;
}

/// Extracts translation model files (ONNX model and tokenizer JSON files)
/// from the Flutter asset bundle to the app's persistent storage directory.
/// Only copies if files don't already exist.
Future<String> extractTranslationModelAssets({bool force = false}) async {
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

  for (final assetPath in modelFiles) {
    final fileName = assetPath.split('/').last;
    final targetFile = File('${modelsDir.path}/$fileName');

    if (!await targetFile.exists() || force) {
      _log.info('Extracting bundled translation model asset.');
      final data = await rootBundle.load(assetPath);
      final bytes = data.buffer.asUint8List();
      await targetFile.writeAsBytes(bytes);
    }
  }

  return modelsDir.path;
}
