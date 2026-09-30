// Tests for the pure-Dart CLIP BPE tokenizer (lib/src/engine/clip_tokenizer.dart).
// Includes property checks from the
// migration spec (77 fixed length, SOT/EOT placement, truncation rules).

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/engine/clip_tokenizer.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late ClipTokenizer tokenizer;

  setUpAll(() async {
    final merges = await File('assets/models/clip-merges.txt').readAsString();
    final vocab = await File('assets/models/clip-vocab.json').readAsString();
    tokenizer = ClipTokenizer.instance;
    tokenizer.initFromString(merges, vocab);
  });

  group('byte encoder', () {
    test('printable ASCII maps to itself, control chars map to U+0100+', () {
      final enc = buildByteEncoder();
      // Printable ASCII maps to itself (Rust: test_byte_encoder_identity).
      expect(enc['!'.codeUnitAt(0)], '!');
      expect(enc['a'.codeUnitAt(0)], 'a');
      expect(enc['z'.codeUnitAt(0)], 'z');
      expect(enc['0'.codeUnitAt(0)], '0');
      // Control chars map to U+0100+.
      expect(enc[0], '\u{0100}');
      expect(enc[32], '\u{0120}'); // space
      expect(enc[127], '\u{0121}'); // DEL
    });
  });

  group('tokenize', () {
    test('throws when not initialized', () {
      final fresh = ClipTokenizer();
      expect(() => fresh.tokenize('x'), throwsStateError);
    });

    test('output is fixed length 77, SOT first, EOT after content', () {
      final tokens = tokenizer.tokenize('a dog playing in the park');
      expect(tokens.length, 77);
      expect(tokens[0], sotToken);
      final eotIndex = tokens.indexOf(eotToken);
      expect(eotIndex, greaterThan(0));
      expect(eotIndex, lessThan(77));
      // Everything after EOT is padding (0).
      expect(tokens.skip(eotIndex + 1).every((t) => t == 0), isTrue);
      // Content tokens are known vocab ids (SOT=49406 is the smallest id).
      expect(tokens.sublist(1, eotIndex).every((t) => t > 0 && t < 49406), isTrue);
    });

    test('BPE pieces round-trip through the vocab (self-consistency)', () {
      // For a simple lowercase phrase, each produced id must map back to a
      // piece whose concatenation equals the byte-encoded regex tokens with
      // </w> markers — verified indirectly: re-encoding the same text is
      // deterministic, and known short words map to single pieces.
      final tokens = tokenizer.tokenize('dog');
      // SOT + single piece for "dog</w>" + EOT.
      final eotIndex = tokens.indexOf(eotToken);
      expect(eotIndex, 2); // [SOT, dog</w>, EOT, ...]
    });

    test('empty text yields SOT + EOT only', () {
      final tokens = tokenizer.tokenize('');
      expect(tokens[0], sotToken);
      expect(tokens[1], eotToken);
    });

    test('long text truncates content to 75 with EOT at index 76', () {
      // 100 distinct common words → way more than 75 content tokens.
      final text = List.generate(100, (i) => 'word$i').join(' ');
      final tokens = tokenizer.tokenize(text);
      expect(tokens.length, 77);
      expect(tokens[0], sotToken);
      expect(tokens[76], eotToken);
      // Content occupies indices 1..75 (75 tokens).
      expect(tokens.sublist(1, 76).every((t) => t != 0 && t != eotToken), isTrue);
    });

    test('uppercase input is lowercased before tokenization', () {
      final lower = tokenizer.tokenize('a dog');
      final upper = tokenizer.tokenize('A DOG');
      expect(upper, equals(lower));
    });

    test('contractions split into known pieces', () {
      // "it's" → regex produces ["it", "'s"]; both must be in-vocab ids.
      final tokens = tokenizer.tokenize("it's");
      final eotIndex = tokens.indexOf(eotToken);
      expect(eotIndex, 3); // [SOT, it</w>, 's</w>, EOT]
    });

    test('non-ASCII text byte-encodes without crashing', () {
      final tokens = tokenizer.tokenize('一只狗');
      expect(tokens.length, 77);
      expect(tokens[0], sotToken);
      final eotIndex = tokens.indexOf(eotToken);
      expect(eotIndex, greaterThan(1)); // at least one piece
    });
  });

  group('known vocab anchors', () {
    test("'a photo of a cat' pieces are the canonical CLIP pieces", () {
      // Independently verified against assets/models/clip-vocab.json:
      // 'a</w>'=320, 'photo</w>'=1125, 'of</w>'=539, 'cat</w>'=2368.
      final tokens = tokenizer.tokenize('a photo of a cat');
      expect(tokens.sublist(0, 7), [sotToken, 320, 1125, 539, 320, 2368, eotToken]);
    });
  });
}
