// CLIP BPE tokenizer
// (GPT-2 byte-encoder + BPE merges, SOT/EOT, 77 fixed length).

import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

const int _contextLength = 77;
const int sotToken = 49406;
const int eotToken = 49407;

final RegExp _splitRegExp = RegExp(
  r"'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+",
  unicode: true,
);

/// Build the GPT-2 byte-to-unicode mapping.
/// Printable ASCII bytes (33-126, 161-172, 174-255) map to their Unicode code
/// points directly; the rest map to code points starting at U+0100.
Map<int, String> buildByteEncoder() {
  final bs = <int>{
    for (var b = 33; b <= 126; b++) b,
    for (var b = 161; b <= 172; b++) b,
    for (var b = 174; b <= 255; b++) b,
  };
  final map = <int, String>{};
  var n = 0;
  for (var b = 0; b <= 255; b++) {
    if (bs.contains(b)) {
      map[b] = String.fromCharCode(b);
    } else {
      map[b] = String.fromCharCode(256 + n);
      n++;
    }
  }
  return map;
}

class ClipTokenizer {
  ClipTokenizer();
  static final ClipTokenizer instance = ClipTokenizer();

  Map<String, int> _bpeRanks = {};
  Map<String, int> _vocab = {};
  late Map<int, String> _byteEncoder;
  bool _initialized = false;

  bool get isInitialized => _initialized;

  /// Initialize with merges/vocab resources loaded from the given asset paths.
  /// Idempotent.
  Future<void> init({
    String mergesAsset = 'assets/models/clip-merges.txt',
    String vocabAsset = 'assets/models/clip-vocab.json',
  }) async {
    if (_initialized) return;
    final mergesText = await rootBundle.loadString(mergesAsset);
    final vocabJson = await rootBundle.loadString(vocabAsset);
    initFromString(mergesText, vocabJson);
  }

  /// Initialize from raw resource contents (used by tests).
  void initFromString(String mergesText, String vocabJson) {
    final ranks = <String, int>{};
    final lines = const LineSplitter().convert(mergesText);
    for (var i = 0; i < lines.length; i++) {
      // Skip version header line
      if (i == 0 && lines[i].startsWith('#')) continue;
      final parts = lines[i].split(' ');
      if (parts.length == 2) {
        ranks['${parts[0]} ${parts[1]}'] = (i - 1) < 0 ? 0 : i - 1;
      }
    }

    final decoded = jsonDecode(vocabJson) as Map<String, dynamic>;
    final vocab = <String, int>{
      for (final e in decoded.entries) e.key: (e.value as num).toInt(),
    };

    _bpeRanks = ranks;
    _vocab = vocab;
    _byteEncoder = buildByteEncoder();
    _initialized = true;
  }

  /// Tokenize text into a fixed-length list of 77 token IDs.
  List<int> tokenize(String text) {
    if (!_initialized) {
      throw StateError('Tokenizer not initialized. Call init() first.');
    }

    final lower = text.toLowerCase();

    // Step 1-2: Regex split
    final tokens = _splitRegExp.allMatches(lower).map((m) => m.group(0)!);

    // Step 3-4: Byte encode each token, then apply BPE
    final bpeTokens = <String>[];
    for (final token in tokens) {
      final encoded = _byteEncode(token);
      final bpeResult = _bpe(encoded);
      bpeTokens.addAll(bpeResult.split(' '));
    }

    // Step 5: Vocabulary lookup
    final tokenIds = <int>[];
    for (final tok in bpeTokens) {
      final id = _vocab[tok];
      if (id != null) tokenIds.add(id);
    }

    // Step 6-7: Build full token sequence with SOT/EOT, pad to 77
    final result = List<int>.filled(_contextLength, 0);
    result[0] = sotToken;

    final maxContent = _contextLength - 1; // reserve position 0 for SOT
    final contentLen = tokenIds.length < maxContent - 1
        ? tokenIds.length
        : maxContent - 1; // reserve 1 slot for EOT

    for (var i = 0; i < contentLen; i++) {
      result[1 + i] = tokenIds[i];
    }

    // Place EOT: right after content, clamped to the last position.
    final eotPos = (contentLen + 1) < _contextLength - 1
        ? contentLen + 1
        : _contextLength - 1;
    result[eotPos] = eotToken;

    return result;
  }

  String _byteEncode(String token) {
    final bytes = utf8.encode(token);
    final buf = StringBuffer();
    for (final b in bytes) {
      buf.write(_byteEncoder[b]);
    }
    return buf.toString();
  }

  /// Apply BPE merges to a byte-encoded token.
  /// Returns space-separated BPE tokens.
  String _bpe(String token) {
    if (token.length <= 1) {
      return '$token</w>';
    }

    // Split into characters, append </w> to the last one
    final chars = token.split('');
    final word = <String>[];
    for (var i = 0; i < chars.length; i++) {
      word.add(i == chars.length - 1 ? '${chars[i]}</w>' : chars[i]);
    }

    if (word.length < 2) {
      return word.join(' ');
    }

    while (true) {
      // Find all pairs and the one with lowest rank
      String? bestPair;
      var bestRank = -1;

      for (var i = 0; i < word.length - 1; i++) {
        final pair = '${word[i]} ${word[i + 1]}';
        final rank = _bpeRanks[pair];
        if (rank != null && (bestRank == -1 || rank < bestRank)) {
          bestRank = rank;
          bestPair = pair;
        }
      }

      if (bestPair == null) break; // No more merges possible

      final parts = bestPair.split(' ');
      final first = parts[0];
      // The pair key contains a space separator; second part may itself
      // contain the encoded chars (never spaces), so re-join defensively.
      final second = parts.sublist(1).join(' ');

      // Merge the best pair
      final merged = '$first$second';
      final newWord = <String>[];
      var i = 0;
      while (i < word.length) {
        if (i < word.length - 1 && word[i] == first && word[i + 1] == second) {
          newWord.add(merged);
          i += 2;
        } else {
          newWord.add(word[i]);
          i += 1;
        }
      }

      word
        ..clear()
        ..addAll(newWord);
      if (word.length < 2) break;
    }

    return word.join(' ');
  }
}
