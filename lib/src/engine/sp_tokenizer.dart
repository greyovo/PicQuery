// SentencePiece Unigram tokenizer (pure Dart) for the MarianMT translation
// models — port of the HF `tokenizers` pipeline used by the translation engine
// (normalizer -> Metaspace pre-tokenizer -> Unigram Viterbi -> Metaspace decoder).
//
// Behavior verified against HF tokenizers 0.22.2 reference outputs:
// - NFKC subset: U+3000 -> space, fullwidth forms U+FF01..U+FF5E -> ASCII.
// - Replace "  " -> " " (single left-to-right pass).
// - Metaspace: replace ' ' -> '▁', prepend '▁', split on '▁' keeping the
//   prefix, drop the empty first chunk.
// - Unigram Viterbi with unk_score = min_score - 10; consecutive unmatched
//   runes are merged into a single `<unk>` token.
// - decode: join pieces, '▁' -> ' ', strip exactly one leading space.

import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

const int unkId = 1;
const double _unkPenalty = 10.0;

class SpTokenizer {
  SpTokenizer();

  final Map<String, int> _pieceToId = {};
  final Map<int, String> _idToPiece = {};
  final Map<int, double> _pieceScores = {};
  double _minScore = 0;
  int _maxPieceRunes = 1;
  bool _initialized = false;

  bool get isInitialized => _initialized;

  /// Load a tokenizer.json from the given asset path. Idempotent per asset.
  Future<void> init({required String assetPath}) async {
    if (_initialized) return;
    final json = await rootBundle.loadString(assetPath);
    initFromString(json);
  }

  /// Initialize from raw tokenizer.json contents (used by tests).
  void initFromString(String tokenizerJson) {
    final root = jsonDecode(tokenizerJson) as Map<String, dynamic>;
    final model = root['model'] as Map<String, dynamic>;
    final vocab = model['vocab'] as List<dynamic>;

    _pieceToId.clear();
    _idToPiece.clear();
    _pieceScores.clear();
    _minScore = double.infinity;
    _maxPieceRunes = 1;

    for (var i = 0; i < vocab.length; i++) {
      final entry = vocab[i] as List<dynamic>;
      final piece = entry[0] as String;
      final score = (entry[1] as num).toDouble();
      _pieceToId[piece] = i;
      _idToPiece[i] = piece;
      _pieceScores[i] = score;
      if (score < _minScore) _minScore = score;
      final runes = piece.runes.length;
      if (runes > _maxPieceRunes) _maxPieceRunes = runes;
    }

    _initialized = true;
  }

  /// Encode text to token ids (no special tokens added — the translator
  /// appends EOS itself, mirroring the Rust implementation).
  List<int> encode(String text) {
    if (!_initialized) {
      throw StateError('SpTokenizer not initialized. Call init() first.');
    }
    final normalized = _normalize(text);
    final chunks = _metaspacePreTokenize(normalized);
    final ids = <int>[];
    for (final chunk in chunks) {
      ids.addAll(_viterbiEncode(chunk));
    }
    return ids;
  }

  /// Decode token ids back to text, skipping nothing (all pieces decode to
  /// their literal string; matches HF `decode(ids, skip_special_tokens: true)`
  /// for this tokenizer since `added_tokens` is empty).
  String decode(List<int> ids) {
    if (!_initialized) {
      throw StateError('SpTokenizer not initialized. Call init() first.');
    }
    final buf = StringBuffer();
    for (final id in ids) {
      buf.write(_idToPiece[id] ?? '<unk>');
    }
    var text = buf.toString().replaceAll('▁', ' ');
    // Metaspace decoder with prepend_scheme=always strips one leading space.
    if (text.startsWith(' ')) {
      text = text.substring(1);
    }
    return text;
  }

  // ---- Normalizer (NFKC subset + declared Replace rules) ----

  String _normalize(String text) {
    var s = _nfkcSubset(text);
    // The tokenizer.json normalizer is Sequence[NFKC, Replace("  " -> " ")].
    s = s.replaceAll('  ', ' ');
    return s;
  }

  /// NFKC compatibility subset covering the ranges that matter for
  /// Chinese/English query text. Full NFKC is not available in pure Dart;
  /// this covers ideographic space, fullwidth ASCII forms, and the common
  /// fullwidth punctuation — verified against the HF reference fixtures.
  String _nfkcSubset(String text) {
    final buf = StringBuffer();
    for (final cp in text.runes) {
      if (cp == 0x3000) {
        buf.write(' '); // ideographic space
      } else if (cp >= 0xFF01 && cp <= 0xFF5E) {
        buf.writeCharCode(cp - 0xFEE0); // fullwidth forms -> ASCII
      } else if (cp >= 0xFF5F && cp <= 0xFF60) {
        buf.writeCharCode(cp - 0xFEB6); // ｟ ｠ -> ((
      } else {
        buf.writeCharCode(cp);
      }
    }
    return buf.toString();
  }

  // ---- Metaspace pre-tokenizer ----

  List<String> _metaspacePreTokenize(String text) {
    if (text.isEmpty) return [];
    final replaced = text.replaceAll(' ', '▁');
    final prepended = '▁$replaced';
    // Split on '▁' keeping the delimiter attached to the following chunk;
    // the leading empty chunk is dropped.
    final chunks = <String>[];
    final parts = prepended.split('▁');
    for (var i = 1; i < parts.length; i++) {
      chunks.add('▁${parts[i]}');
    }
    return chunks;
  }

  // ---- Unigram Viterbi ----

  List<int> _viterbiEncode(String chunk) {
    final runes = chunk.runes.toList();
    final n = runes.length;
    if (n == 0) return [];

    final unkScore = _minScore - _unkPenalty;

    // best[i] = best (score, prevIndex) ending exactly before position i.
    final bestScore = List<double>.filled(n + 1, double.negativeInfinity);
    final bestPrev = List<int>.filled(n + 1, -1);
    final bestPieceId = List<int>.filled(n + 1, -1);
    bestScore[0] = 0;

    // substring cache for pieces
    final pieceAt = <int, int>{}; // (start * (n+1) + len) -> piece id

    String runesToString(int start, int end) {
      return String.fromCharCodes(runes.sublist(start, end));
    }

    for (var i = 0; i < n; i++) {
      if (bestScore[i] == double.negativeInfinity) continue;
      // Unknown single-rune node (always available; merged with consecutive
      // unks after path reconstruction).
      final sUnk = bestScore[i] + unkScore;
      if (sUnk > bestScore[i + 1]) {
        bestScore[i + 1] = sUnk;
        bestPrev[i + 1] = i;
        bestPieceId[i + 1] = unkId;
      }
      // All pieces starting at i.
      final maxLen = (i + _maxPieceRunes) < n ? (i + _maxPieceRunes) : n;
      for (var j = i + 1; j <= maxLen; j++) {
        final key = i * (n + 1) + j;
        final id = pieceAt[key] ?? _pieceToId[runesToString(i, j)];
        if (id == null) continue;
        final s = bestScore[i] + _pieceScores[id]!;
        if (s > bestScore[j]) {
          bestScore[j] = s;
          bestPrev[j] = i;
          bestPieceId[j] = id;
        }
      }
    }

    // Reconstruct the best path, then merge consecutive <unk> tokens
    // (HF groups consecutive unknown characters into a single unk token).
    final revIds = <int>[];
    var pos = n;
    while (pos > 0) {
      revIds.add(bestPieceId[pos]);
      pos = bestPrev[pos];
    }
    final ids = revIds.reversed.toList();
    final merged = <int>[];
    for (final id in ids) {
      if (id == unkId && merged.isNotEmpty && merged.last == unkId) continue;
      merged.add(id);
    }
    return merged;
  }
}
