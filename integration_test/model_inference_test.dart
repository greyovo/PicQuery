import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:picquery_app/src/engine/ort_engine.dart';
import 'package:picquery_app/src/engine/sp_tokenizer.dart';
import 'package:picquery_app/src/engine/translator.dart';

// Real native inference requires a device runner:
// fvm flutter test integration_test/model_inference_test.dart -d macos
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  final engine = OrtEngine.instance;
  late Directory directory;

  Future<String> copyAsset(String name) async {
    final data = await rootBundle.load('assets/models/$name');
    final file = File('${directory.path}/$name');
    await file.writeAsBytes(
      data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
      flush: true,
    );
    return file.path;
  }

  void expectValidVector(Float32List values, String model) {
    expect(values, isNotEmpty, reason: '$model output must not be empty');
    expect(
      values.every((value) => value.isFinite),
      isTrue,
      reason: '$model output must not contain NaN or infinity',
    );
    expect(
      values.any((value) => value != 0),
      isTrue,
      reason: '$model output must not be all zero',
    );
  }

  void expectEmbedding(Float32List values, String model) {
    expect(values, hasLength(512), reason: '$model embedding dimensions');
    expectValidVector(values, model);
    expect(
      values.fold<double>(0, (sum, value) => sum + value * value),
      closeTo(1, 1e-5),
      reason: '$model embedding must be L2-normalized',
    );
  }

  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('picquery-model-test-');
    addTearDown(() => directory.delete(recursive: true));
    await engine.loadClipModels(
      textModelPath: await copyAsset('mobileclip2_s0_text.onnx'),
      visualModelPath: await copyAsset('mobileclip2_s0_visual.onnx'),
      force: true,
    );
  });

  testWidgets('text model produces a finite, nonzero embedding', (_) async {
    expectEmbedding(await engine.encodeText('a photo of a cat'), 'Text');
  });

  testWidgets('visual model produces a finite, nonzero embedding', (_) async {
    // Deterministic CHW image tensor in the production [0, 1] input range.
    final tensor = Float32List(3 * 256 * 256);
    for (var i = 0; i < tensor.length; i++) {
      tensor[i] = (i % 256) / 255.0;
    }
    expectEmbedding(await engine.encodeImage(tensor), 'Visual');
  });

  testWidgets('translation model produces valid logits and English', (_) async {
    final sourcePath = await copyAsset('source_tokenizer.json');
    await Translator.instance.load(
      modelPath: await copyAsset('mt_zho-eng.fp32.quantized.onnx'),
      sourceSpPath: sourcePath,
      targetSpPath: await copyAsset('target_tokenizer.json'),
      force: true,
    );

    const sentence = '一只猫';
    final source = SpTokenizer()
      ..initFromString(await File(sourcePath).readAsString());
    final inputIds = source.encode(sentence)..add(eosTokenId);
    final logits = await engine.runTranslationStep(
      inputIds: inputIds,
      attentionMask: List<int>.filled(inputIds.length, 1),
      decoderInputIds: const [decoderStartTokenId],
    );
    // One decoder position, with 32,001 vocabulary scores.
    expect(logits, hasLength(32001));
    expectValidVector(logits, 'Translation');

    final translated = (await Translator.instance.translate(sentence)).trim();
    expect(translated, isNotEmpty);
    expect(translated, isNot(sentence));
    expect(translated, matches(RegExp(r'[A-Za-z]')));
    expect(translated, isNot(matches(RegExp(r'[\u4e00-\u9fff]'))));
  }, timeout: const Timeout(Duration(minutes: 5)));
}
