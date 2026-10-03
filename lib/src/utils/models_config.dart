// const kClipVisualModelPath = 'mobileclip2_s2_visual_int8.onnx';
// const kClipTextModelPath = 'mobileclip2_s2_text_int8.onnx';

import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/asset_extractor.dart';

const kClipVisualModelPath = 'mobileclip2_s0_visual.onnx';
const kClipTextModelPath = 'mobileclip2_s0_text.onnx';

const kTranslationModelPath = 'mt_zho-eng.fp32.quantized.onnx';
const kSourceSpModelPath = 'source_tokenizer.json';
const kTargetSpModelPath = 'target_tokenizer.json';

Future<void> initClipModels({bool force = false}) async {
  final modelsDir = await extractClipModelAssets(force: force);
  final visualModelPath = '$modelsDir/$kClipVisualModelPath';
  final textModelPath = '$modelsDir/$kClipTextModelPath';
  await loadClipModels(
    textModelPath: textModelPath,
    visualModelPath: visualModelPath,
    force: force,
  );
}

Future<void> initTranslationModel({bool force = false}) async {
  final modelsDir = await extractTranslationModelAssets(force: force);
  final modelPath = '$modelsDir/$kTranslationModelPath';
  final sourceSpPath = '$modelsDir/$kSourceSpModelPath';
  final targetSpPath = '$modelsDir/$kTargetSpModelPath';
  await loadTranslationModel(
    modelPath: modelPath,
    sourceSpPath: sourceSpPath,
    targetSpPath: targetSpPath,
    force: force,
  );
}
