import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('Android CPU versus NNAPI correctness and latency', (_) async {
    final directory = await Directory.systemTemp.createTemp('nnapi-benchmark');
    final asset = await rootBundle.load(
      'assets/models/mobileclip2_s0_visual.onnx',
    );
    final file = File('${directory.path}/visual.onnx');
    await file.writeAsBytes(
      asset.buffer.asUint8List(asset.offsetInBytes, asset.lengthInBytes),
    );
    final inputData = Float32List(3 * 256 * 256);
    final references = <int, List<double>>{};
    try {
      for (final provider in [
        OrtProvider.CPU,
        OrtProvider.NNAPI,
        OrtProvider.NNAPI,
        OrtProvider.CPU,
      ]) {
        final loading = Stopwatch()..start();
        final session = await OnnxRuntime().createSession(
          file.path,
          options: OrtSessionOptions(
            providers: [
              provider,
              if (provider != OrtProvider.CPU) OrtProvider.CPU,
            ],
            graphOptimizationLevel: OrtGraphOptimizationLevel.basic,
          ),
        );
        final loadMs = loading.elapsedMicroseconds / 1000;
        final input = await OrtValue.fromReusableFloat32Bytes(
          inputData.buffer.asUint8List(),
          [1, 3, 256, 256],
        );
        final times = <double>[];
        var minCosine = 1.0;
        var nonFinite = 0;
        try {
          for (var run = 0; run < 25; run++) {
            final variant = run % 5;
            for (var i = 0; i < inputData.length; i++) {
              inputData[i] = ((i + variant * 37) % 256) / 255.0;
            }
            await input.updateReusableFloat32Bytes(
              inputData.buffer.asUint8List(),
            );
            final watch = Stopwatch()..start();
            final outputs = await session.run({
              session.inputNames.first: input,
            });
            final ms = watch.elapsedMicroseconds / 1000;
            try {
              final values = (await outputs.values.first.asFlattenedList())
                  .map((v) => (v as num).toDouble())
                  .toList();
              expect(values, hasLength(512));
              nonFinite += values.where((v) => !v.isFinite).length;
              if (provider == OrtProvider.CPU) {
                references[variant] = values;
              } else if (values.every((v) => v.isFinite)) {
                final ref = references[variant]!;
                var dot = 0.0, normA = 0.0, normB = 0.0;
                for (var i = 0; i < values.length; i++) {
                  dot += values[i] * ref[i];
                  normA += values[i] * values[i];
                  normB += ref[i] * ref[i];
                }
                minCosine = math.min(minCosine, dot / math.sqrt(normA * normB));
              }
              if (run >= 5) {
                times.add(ms);
              }
            } finally {
              for (final value in outputs.values) {
                await value.dispose();
              }
            }
          }
          times.sort();
          // ignore: avoid_print
          print(
            'NNAPI_BENCH provider=${provider.name} load_ms=$loadMs median_ms=${times[times.length ~/ 2]} p95_ms=${times[(times.length * .95).ceil() - 1]} nonFinite=$nonFinite minCosine=$minCosine',
          );
          expect(nonFinite, 0, reason: '${provider.name} correctness');
          expect(
            minCosine,
            greaterThan(.999),
            reason: 'NNAPI versus CPU agreement',
          );
        } finally {
          await input.dispose();
          await session.close();
        }
      }
    } finally {
      await directory.delete(recursive: true);
    }
  });
}
