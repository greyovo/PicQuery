// MarianMT Chinese→English translation.
// Autoregressive greedy decoding: DECODER_START=32000, EOS=0, ≤255 steps,
// argmax over the last position's logits each step.

import 'dart:io' show File;

import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/ort_engine.dart';
import 'package:picquery_app/src/engine/sp_tokenizer.dart';

final _log = Logger('engine.translator');

const int eosTokenId = 0;
const int decoderStartTokenId = 32000;
const int maxPositionEmbeddings = 256;
const int maxDecoderSteps = maxPositionEmbeddings - 1;

/// Manages the translation model session and the source/target SentencePiece
/// tokenizers.
class Translator {
  Translator._();
  static final Translator instance = Translator._();

  SpTokenizer? _sourceTokenizer;
  SpTokenizer? _targetTokenizer;

  bool get isLoaded =>
      OrtEngine.instance.translationLoaded &&
      _sourceTokenizer != null &&
      _targetTokenizer != null;

  /// Load the translation ONNX model and the SentencePiece tokenizers from
  /// the given file paths. Skips when already loaded and [force] is false.
  Future<void> load({
    required String modelPath,
    required String sourceSpPath,
    required String targetSpPath,
    required bool force,
  }) async {
    if (isLoaded && !force) {
      _log.fine('Translation model is already initialized.');
      return;
    }

    _log.info('Loading translation model.');

    // Load tokenizers first so a failure here doesn't leave a half-initialized
    // session state.
    final source = SpTokenizer();
    final target = SpTokenizer();
    try {
      source.initFromString(await File(sourceSpPath).readAsString());
      target.initFromString(await File(targetSpPath).readAsString());
    } catch (_) {
      _log.severe('Failed to load SentencePiece tokenizers.');
      throw Exception('Failed to load SentencePiece tokenizers.');
    }

    await OrtEngine.instance.loadTranslationModel(
      modelPath: modelPath,
      force: force,
    );

    _sourceTokenizer = source;
    _targetTokenizer = target;
    _log.info('Translation model loaded successfully.');
  }

  /// Translate a Chinese sentence to English via greedy decoding.
  Future<String> translate(String sentence) async {
    final source = _sourceTokenizer;
    final target = _targetTokenizer;
    if (source == null || target == null) {
      throw StateError(
        'Translation model not initialized. Call loadTranslationModel() first.',
      );
    }

    if (sentence.isEmpty) {
      return '';
    }

    // 1. Tokenize source text. MarianMT expects the source sequence to end
    // with EOS; the raw SentencePiece tokenizer does not add it automatically.
    final inputIds = source.encode(sentence)..add(eosTokenId);
    if (inputIds.length > maxPositionEmbeddings) {
      inputIds.length = maxPositionEmbeddings;
    }
    final srcLen = inputIds.length;

    if (srcLen == 0) {
      throw Exception('Tokenization produced empty input');
    }

    // 2. Source attention mask.
    final attentionMask = List<int>.filled(srcLen, 1);

    // 3. Autoregressive greedy decoding.
    final decoderInputIds = <int>[decoderStartTokenId];
    for (var step = 0; step < maxDecoderSteps; step++) {
      final decLen = decoderInputIds.length;
      final logits = await OrtEngine.instance.runTranslationStep(
        inputIds: inputIds,
        attentionMask: attentionMask,
        decoderInputIds: decoderInputIds,
      );

      // logits flat length = decLen * vocabSize; take the last position's
      // slice and argmax.
      final vocabSize = logits.length ~/ decLen;
      final lastPosStart = (decLen - 1) * vocabSize;

      var nextToken = eosTokenId;
      var bestValue = double.negativeInfinity;
      for (var i = 0; i < vocabSize; i++) {
        final v = logits[lastPosStart + i];
        if (v > bestValue) {
          bestValue = v;
          nextToken = i;
        }
      }

      if (nextToken == eosTokenId) {
        break;
      }

      decoderInputIds.add(nextToken);
    }

    // 4. Decode output tokens (skip the initial decoder_start_token).
    final outputIds = decoderInputIds.sublist(1);
    if (outputIds.isEmpty) {
      return '';
    }

    return target.decode(outputIds);
  }
}
