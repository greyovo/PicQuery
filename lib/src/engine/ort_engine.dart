// ONNX Runtime session management.
// Uses flutter_onnxruntime. Sessions: CLIP visual/text + MarianMT translation.

import 'dart:async';
import 'dart:io' show File, Platform;
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/clip_tokenizer.dart';
import 'package:picquery_app/src/engine/image_preprocess.dart';

final _log = Logger('engine.ort');

const int _embeddingDim = 512;

/// L2-normalize a vector. Returns the input vector unchanged if the norm is
/// (near) zero — mirrors the Rust implementation.
Float32List l2Normalize(Float32List v) {
  var sumSq = 0.0;
  for (final x in v) {
    sumSq += x * x;
  }
  final norm = math.sqrt(sumSq);
  if (norm < 1e-12) {
    return v;
  }
  final out = Float32List(v.length);
  for (var i = 0; i < v.length; i++) {
    out[i] = v[i] / norm;
  }
  return out;
}

/// Select the execution providers to request for a session, based on the
/// platform and what the runtime reports as available. Mirrors the platform
/// matrix of the Rust `init_ort_environment`.
List<OrtProvider> _preferredProviders(Set<OrtProvider> available) {
  // This graph is split into 83 CoreML partitions, while XNNPACK is also much
  // slower than the CPU EP in the matching macOS integration benchmark.
  if (Platform.isMacOS && available.contains(OrtProvider.CPU)) {
    return const [OrtProvider.CPU];
  }

  // On Android, ORT 1.28 crashes natively on the tested device when a session
  // is created with NNAPI and XNNPACK together (or when XNNPACK is selected by
  // the benchmark directly). NNAPI is preferred whenever it is available;
  // retain CPU solely as its safe fallback. Devices without NNAPI can still use
  // XNNPACK before falling back to CPU.
  if (Platform.isAndroid && available.contains(OrtProvider.NNAPI)) {
    return const [OrtProvider.NNAPI, OrtProvider.CPU];
  }

  final prefs = <OrtProvider>[
    if (Platform.isIOS) OrtProvider.CORE_ML,
    if (Platform.isWindows) OrtProvider.DIRECT_ML,
    OrtProvider.XNNPACK,
    OrtProvider.CPU,
  ];
  final picked = prefs.where(available.contains).toList();
  return picked.isEmpty ? [OrtProvider.CPU] : picked;
}

/// Manages ONNX sessions for the CLIP models and the translation model.
class OrtEngine {
  OrtEngine._();
  static final OrtEngine instance = OrtEngine._();

  OrtSession? _visualSession;
  OrtSession? _textSession;
  OrtSession? _translationSession;
  OrtValue? _reusableAndroidVisualInput;
  Future<void> _visualInputTail = Future.value();
  List<OrtProvider> _activeProviders = const [];

  bool get clipLoaded => _visualSession != null && _textSession != null;
  bool get translationLoaded => _translationSession != null;

  List<OrtProvider> get activeProviders => _activeProviders;

  Future<OrtSessionOptions?> _sessionOptions() async {
    try {
      final available = (await OnnxRuntime().getAvailableProviders()).toSet();
      _activeProviders = _preferredProviders(available);
      _log.info(
        'Available execution providers: $available; using: $_activeProviders.',
      );
      return OrtSessionOptions(
        providers: _activeProviders,
        intraOpNumThreads: Platform.isMacOS
            ? math.min(4, Platform.numberOfProcessors)
            : null,
      );
    } catch (_) {
      _log.severe('Failed to probe execution providers; using defaults.');
      return null;
    }
  }

  /// Uses CPU on Windows for small, autoregressive Marian decoder runs.
  Future<OrtSessionOptions?> _translationSessionOptions() async {
    if (Platform.isWindows) {
      _log.info('Using CPU execution provider for translation on Windows.');
      return OrtSessionOptions(providers: const [OrtProvider.CPU]);
    }
    return _sessionOptions();
  }

  /// Creates a Windows DirectML session when possible, with a CPU-only retry
  /// for systems whose reported provider cannot initialize on the active GPU.
  /// Other platforms preserve their existing provider behavior.
  Future<OrtSession> _createSession(
    String modelPath,
    OrtSessionOptions? options,
  ) async {
    try {
      return await OnnxRuntime().createSession(modelPath, options: options);
    } catch (_) {
      if (!Platform.isWindows ||
          !_activeProviders.contains(OrtProvider.DIRECT_ML)) {
        rethrow;
      }
      _log.severe('DirectML session creation failed; retrying with CPU.');
      _activeProviders = const [OrtProvider.CPU];
      return OnnxRuntime().createSession(
        modelPath,
        options: OrtSessionOptions(providers: _activeProviders),
      );
    }
  }

  /// Load the CLIP visual/text models from file paths.
  /// Skips loading when already loaded and [force] is false.
  Future<void> loadClipModels({
    required String textModelPath,
    required String visualModelPath,
    required bool force,
  }) async {
    if (clipLoaded && !force) return;

    if (!await File(visualModelPath).exists()) {
      throw Exception('Visual model not found: $visualModelPath');
    }
    if (!await File(textModelPath).exists()) {
      throw Exception('Text model not found: $textModelPath');
    }

    // Tokenizer resources are required by text encoding; init alongside
    // model loading (mirrors Rust load_clip_models calling init_tokenizer).
    await ClipTokenizer.instance.init();

    _log.info('Loading visual model.');
    final options = await _sessionOptions();
    final OrtSession visual;
    try {
      visual = await _createSession(visualModelPath, options);
    } catch (_) {
      _log.severe('Failed to load visual model.');
      throw Exception('Failed to load visual model.');
    }

    _log.info('Loading text model.');
    final OrtSession text;
    try {
      text = await _createSession(textModelPath, options);
    } catch (_) {
      _log.severe('Failed to load text model.');
      throw Exception('Failed to load text model.');
    }

    _visualSession = visual;
    _textSession = text;
    _log.info('CLIP models loaded successfully.');
  }

  /// Load the MarianMT translation model from a file path.
  /// Skips loading when already loaded and [force] is false.
  /// Tokenizer loading is handled by the caller (sp_tokenizer).
  Future<void> loadTranslationModel({
    required String modelPath,
    required bool force,
  }) async {
    if (translationLoaded && !force) return;

    if (!await File(modelPath).exists()) {
      throw Exception('Translation model not found: $modelPath');
    }

    _log.info('Loading translation model.');
    final options = await _translationSessionOptions();
    final OrtSession session;
    try {
      session = await _createSession(modelPath, options);
    } catch (_) {
      _log.severe('Failed to load translation model.');
      throw Exception('Failed to load translation model.');
    }

    _translationSession = session;
    _log.info('Translation model loaded successfully.');
  }

  /// Run the visual model on a preprocessed [1,3,256,256] float tensor.
  /// Returns the L2-normalized 512-dim embedding.
  Future<Float32List> encodeImage(Float32List tensor) async {
    final previous = _visualInputTail;
    final completion = Completer<void>();
    _visualInputTail = completion.future;
    await previous;
    try {
      return await _encodeImageLocked(tensor);
    } finally {
      completion.complete();
    }
  }

  Future<Float32List> _encodeImageLocked(Float32List tensor) async {
    final session = _visualSession;
    if (session == null) {
      throw StateError('Models not initialized. Call loadClipModels() first.');
    }
    final inputName = session.inputNames.first;
    final sw = Stopwatch()..start();
    final input = await _visualInput(tensor);
    final createMs = sw.elapsedMilliseconds;
    Map<String, OrtValue> outputs;
    try {
      outputs = await session.run({inputName: input});
      final runMs = sw.elapsedMilliseconds - createMs;
      try {
        final flat = await outputs.values.first.asFlattenedList();
        final extractMs = sw.elapsedMilliseconds - createMs - runMs;
        final embedding = Float32List.fromList(
          flat.map((e) => (e as num).toDouble()).toList(),
        );
        if (embedding.length != _embeddingDim) {
          throw Exception(
            'Unexpected embedding dimension: got ${embedding.length}, expected $_embeddingDim',
          );
        }
        final result = l2Normalize(embedding);
        _log.fine(
          'Visual inference: create=${createMs}ms run=${runMs}ms extract=${extractMs}ms.',
        );
        return result;
      } finally {
        for (final v in outputs.values) {
          await v.dispose();
        }
      }
    } finally {
      if (!Platform.isAndroid) {
        await input.dispose();
      }
    }
  }

  Future<OrtValue> _visualInput(Float32List tensor) async {
    if (!Platform.isAndroid) {
      return OrtValue.fromList(tensor, [1, 3, 256, 256]);
    }
    final bytes = tensor.buffer.asUint8List(
      tensor.offsetInBytes,
      tensor.lengthInBytes,
    );
    final reusable = _reusableAndroidVisualInput;
    if (reusable == null) {
      final created = await OrtValue.fromReusableFloat32Bytes(bytes, [
        1,
        3,
        256,
        256,
      ]);
      _reusableAndroidVisualInput = created;
      return created;
    }
    await reusable.updateReusableFloat32Bytes(bytes);
    return reusable;
  }

  /// Run the text model on a 77-token sequence. Returns the L2-normalized
  /// 512-dim embedding. Token ids are passed as Int64List (explicit int64).
  Future<Float32List> encodeTextTokens(List<int> tokens) async {
    final session = _textSession;
    if (session == null) {
      throw StateError('Models not initialized. Call loadClipModels() first.');
    }
    final inputName = session.inputNames.first;
    final input = await OrtValue.fromList(Int64List.fromList(tokens), [1, 77]);
    Map<String, OrtValue> outputs;
    try {
      outputs = await session.run({inputName: input});
    } finally {
      await input.dispose();
    }
    try {
      final flat = await outputs.values.first.asFlattenedList();
      final embedding = Float32List.fromList(
        flat.map((e) => (e as num).toDouble()).toList(),
      );
      if (embedding.length != _embeddingDim) {
        throw Exception(
          'Unexpected embedding dimension: got ${embedding.length}, expected $_embeddingDim',
        );
      }
      return l2Normalize(embedding);
    } finally {
      for (final v in outputs.values) {
        await v.dispose();
      }
    }
  }

  /// Encode an image file: preprocess then run the visual model.
  Future<Float32List> encodeImageFile(String imagePath) async {
    return (await encodeImageFileWithMetadata(imagePath)).embedding;
  }

  /// Encode once and retain dimensions from the same decode. Indexing uses
  /// this to avoid a second file read and a second short-lived isolate.
  Future<({Float32List embedding, int width, int height})>
  encodeImageFileWithMetadata(String imagePath) async {
    final sw = Stopwatch()..start();
    final preprocessed = await preprocessImageWithMetadata(imagePath);
    final preprocessMs = sw.elapsedMilliseconds;
    final embedding = await encodeImage(preprocessed.tensor);
    _log.fine(
      'Image encoding: preprocess=${preprocessMs}ms total=${sw.elapsedMilliseconds}ms.',
    );
    return (
      embedding: embedding,
      width: preprocessed.width,
      height: preprocessed.height,
    );
  }

  /// Encode a text query: tokenize then run the text model.
  Future<Float32List> encodeText(String text) async {
    final tokens = ClipTokenizer.instance.tokenize(text);
    return encodeTextTokens(tokens);
  }

  /// Run the translation model one decoding step.
  /// Returns the flattened logits of shape [1, decLen, vocab].
  Future<Float32List> runTranslationStep({
    required List<int> inputIds,
    required List<int> attentionMask,
    required List<int> decoderInputIds,
  }) async {
    final session = _translationSession;
    if (session == null) {
      throw StateError(
        'Translation model not initialized. Call loadTranslationModel() first.',
      );
    }
    final input = await OrtValue.fromList(Int64List.fromList(inputIds), [
      1,
      inputIds.length,
    ]);
    final mask = await OrtValue.fromList(Int64List.fromList(attentionMask), [
      1,
      attentionMask.length,
    ]);
    final decoder = await OrtValue.fromList(
      Int64List.fromList(decoderInputIds),
      [1, decoderInputIds.length],
    );
    Map<String, OrtValue> outputs;
    try {
      outputs = await session.run({
        'input_ids': input,
        'attention_mask': mask,
        'decoder_input_ids': decoder,
      });
    } finally {
      await input.dispose();
      await mask.dispose();
      await decoder.dispose();
    }
    try {
      final flat = await outputs.values.first.asFlattenedList();
      return Float32List.fromList(
        flat.map((e) => (e as num).toDouble()).toList(),
      );
    } finally {
      for (final v in outputs.values) {
        await v.dispose();
      }
    }
  }
}
