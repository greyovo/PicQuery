import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/utils/asset_extractor.dart';
import 'package:picquery_app/src/utils/models_config.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'background extraction installs complete assets and reuses them',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'picquery-assets-',
      );
      const channel = MethodChannel('plugins.flutter.io/path_provider');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => directory.path,
      );
      try {
        final progress = <int>[];
        final modelDirectory = await extractTranslationModelAssets(
          onProgress: (completed, total) {
            expect(total, 3);
            progress.add(completed);
          },
        );
        expect(progress, [0, 1, 2, 3]);
        const files = [
          kTranslationModelPath,
          kSourceSpModelPath,
          kTargetSpModelPath,
        ];
        final cachedTime = DateTime(2020);
        for (final name in files) {
          final installed = File('$modelDirectory/$name');
          final bundled = File('assets/models/$name');
          expect(await installed.length(), await bundled.length());
          if (name.endsWith('.json')) {
            expect(
              listEquals(
                await installed.readAsBytes(),
                await bundled.readAsBytes(),
              ),
              isTrue,
            );
          }
          expect(await File('${installed.path}.part').exists(), isFalse);
          await installed.setLastModified(cachedTime);
        }
        await extractTranslationModelAssets();
        for (final name in files) {
          expect(
            await File('$modelDirectory/$name').lastModified(),
            cachedTime,
          );
        }
        // An empty file should be repaired rather than treated as installed.
        final tokenizer = File('$modelDirectory/$kSourceSpModelPath');
        await tokenizer.writeAsBytes([]);
        await extractTranslationModelAssets();
        expect(
          await tokenizer.length(),
          await File('assets/models/$kSourceSpModelPath').length(),
        );
      } finally {
        messenger.setMockMethodCallHandler(channel, null);
        await directory.delete(recursive: true);
      }
    },
  );
}
