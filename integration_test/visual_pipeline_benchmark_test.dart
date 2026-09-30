import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:integration_test/integration_test.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:picquery_app/src/engine/image_preprocess.dart';
import 'package:picquery_app/src/engine/ort_engine.dart';
import 'package:picquery_app/src/utils/models_config.dart';

final _log = Logger('benchmark.visual_pipeline');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'benchmarks a large sandboxed photo through the visual pipeline',
    (tester) async {
      final support = await getApplicationSupportDirectory();
      final photo = File('${support.path}/picquery-profile-photo.jpg');
      if (!await photo.exists()) {
        // Deliberately retain this 12 MP JPEG in the app data directory. It
        // makes repeated device benchmarks deterministic without depending on
        // MediaStore permissions or UI automation.
        final source = img.Image(width: 4000, height: 3000, numChannels: 3);
        for (var y = 0; y < source.height; y++) {
          final green = y * 255 ~/ source.height;
          for (var x = 0; x < source.width; x++) {
            source.setPixelRgb(x, y, x * 255 ~/ source.width, green, 127);
          }
        }
        await photo.writeAsBytes(
          img.encodeJpg(source, quality: 92),
          flush: true,
        );
      }

      await initClipModels();

      // Warm decoder, worker isolate, tensor transport, and execution provider.
      final warmup = await preprocessImageWithMetadata(photo.path);
      await OrtEngine.instance.encodeImage(warmup.tensor);

      final totalTimes = <int>[];
      for (var i = 0; i < 3; i++) {
        final total = Stopwatch()..start();
        final preprocessed = await preprocessImageWithMetadata(photo.path);
        final embedding = await OrtEngine.instance.encodeImage(
          preprocessed.tensor,
        );
        totalTimes.add(total.elapsedMilliseconds);
        expect((preprocessed.width, preprocessed.height), (4000, 3000));
        expect(embedding, hasLength(512));
      }
      final sorted = [...totalTimes]..sort();
      _log.info(
        '[benchmark] visual pipeline (12MP sandbox photo): '
        'runs=${totalTimes.join(',')}ms median=${sorted[1]}ms',
      );
    },
  );
}
