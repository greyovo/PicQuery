// Tests for the pure-Dart SentencePiece Unigram tokenizer
// (lib/src/engine/sp_tokenizer.dart) against HF `tokenizers` 0.22.2 reference
// outputs (test/fixtures/sp_tokenizer_fixtures.json), matching the HF
// pipeline used by the translation engine:
//   encode: normalize -> Metaspace -> Unigram Viterbi  (add_special_tokens=false)
//   decode: join pieces -> Metaspace inverse (skip_special_tokens=true)

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/engine/sp_tokenizer.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late SpTokenizer source;
  late SpTokenizer target;
  late Map<String, dynamic> fixtures;

  setUpAll(() async {
    source = SpTokenizer();
    target = SpTokenizer();
    source.initFromString(await File('assets/models/source_tokenizer.json').readAsString());
    target.initFromString(await File('assets/models/target_tokenizer.json').readAsString());
    fixtures =
        jsonDecode(await File('test/fixtures/sp_tokenizer_fixtures.json').readAsString())
            as Map<String, dynamic>;
  });

  group('source encode (vs HF reference)', () {
    test('all fixture sentences produce identical token id sequences', () {
      final cases = (fixtures['encode'] as List).cast<Map<String, dynamic>>();
      expect(cases.length, greaterThanOrEqualTo(20));
      for (final case_ in cases) {
        final ids = source.encode(case_['text'] as String);
        expect(
          ids,
          (case_['source_ids'] as List).cast<int>(),
          reason: "mismatch for input: '${case_['text']}'",
        );
      }
    });

    test('empty input produces no tokens', () {
      expect(source.encode(''), isEmpty);
    });
  });

  group('target decode (vs HF reference)', () {
    test('decoding reference id sequences reproduces HF decoded text', () {
      final cases = (fixtures['decode'] as List).cast<Map<String, dynamic>>();
      for (final case_ in cases) {
        final text = target.decode((case_['ids'] as List).cast<int>());
        expect(
          text,
          case_['decoded'] as String,
          reason: 'decode mismatch for ids ${case_['ids']}',
        );
      }
    });
  });
}
