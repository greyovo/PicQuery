import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:logging/logging.dart';

final _log = Logger('benchmark.visual_inference');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('visual inference benchmark', (tester) async {
    const assetPath = 'assets/models/mobileclip2_s0_visual.onnx';
    const externalModelPath = String.fromEnvironment(
      'VISUAL_BENCHMARK_MODEL_PATH',
    );
    late final String modelPath;
    if (externalModelPath.isNotEmpty) {
      modelPath = externalModelPath;
    } else {
      final directory = await Directory.systemTemp.createTemp(
        'picquery-visual-benchmark-',
      );
      addTearDown(() => directory.delete(recursive: true));
      modelPath = '${directory.path}/visual.onnx';
      final modelData = await rootBundle.load(assetPath);
      await File(modelPath).writeAsBytes(
        modelData.buffer.asUint8List(
          modelData.offsetInBytes,
          modelData.lengthInBytes,
        ),
        flush: true,
      );
    }

    final tensor = Float32List(3 * 256 * 256);
    for (var index = 0; index < tensor.length; index++) {
      tensor[index] = (index % 256) / 255.0;
    }

    Future<({double create, double run, double extract})> runOnce(
      OrtSession session,
    ) async {
      final watch = Stopwatch()..start();
      final input = await OrtValue.fromList(tensor, const [1, 3, 256, 256]);
      final createMs = watch.elapsedMicroseconds / 1000;
      try {
        final outputs = await session.run({session.inputNames.first: input});
        final runMs = watch.elapsedMicroseconds / 1000 - createMs;
        try {
          final values = await outputs.values.first.asFlattenedList();
          expect(values, hasLength(512));
          final extractMs = watch.elapsedMicroseconds / 1000 - createMs - runMs;
          return (create: createMs, run: runMs, extract: extractMs);
        } finally {
          for (final output in outputs.values) {
            await output.dispose();
          }
        }
      } finally {
        await input.dispose();
      }
    }

    double median(Iterable<double> samples) {
      final sorted = samples.toList()..sort();
      return sorted[sorted.length ~/ 2];
    }

    final available = (await OnnxRuntime().getAvailableProviders()).toSet();
    _log.info('PICQUERY_VISUAL_BENCHMARK available_providers=$available');
    final configurations =
        <({String label, List<OrtProvider> providers, int? threads})>[
          for (final threads in <int?>[null, 1, 2, 4, 6])
            (
              label: 'cpu',
              providers: const [OrtProvider.CPU],
              threads: threads,
            ),
          // DirectML does not allow ORT parallel graph execution or memory
          // patterns. The native plugin applies those requirements; keeping
          // threads null here makes this benchmark representative of the app.
          if (Platform.isWindows && available.contains(OrtProvider.DIRECT_ML))
            (
              label: 'directml',
              providers: const [OrtProvider.DIRECT_ML, OrtProvider.CPU],
              threads: null,
            ),
          if (available.contains(OrtProvider.XNNPACK))
            (
              label: 'xnnpack',
              providers: const [OrtProvider.XNNPACK, OrtProvider.CPU],
              threads: 4,
            ),
        ];
    for (final configuration in configurations) {
      final sessionWatch = Stopwatch()..start();
      final session = await OnnxRuntime().createSession(
        modelPath,
        options: OrtSessionOptions(
          providers: configuration.providers,
          intraOpNumThreads: configuration.threads,
        ),
      );
      final sessionMs = sessionWatch.elapsedMicroseconds / 1000;
      try {
        final first = await runOnce(session);
        for (var index = 0; index < 3; index++) {
          await runOnce(session);
        }
        final measured = <({double create, double run, double extract})>[];
        for (var index = 0; index < 10; index++) {
          measured.add(await runOnce(session));
        }

        _log.info(
          'PICQUERY_VISUAL_BENCHMARK provider=${configuration.label} '
          'threads=${configuration.threads ?? 'default'} '
          'session=${sessionMs.toStringAsFixed(2)}ms '
          'first(create=${first.create.toStringAsFixed(2)}ms,'
          'run=${first.run.toStringAsFixed(2)}ms,'
          'extract=${first.extract.toStringAsFixed(2)}ms) '
          'steady_median(create=${median(measured.map((e) => e.create)).toStringAsFixed(2)}ms,'
          'run=${median(measured.map((e) => e.run)).toStringAsFixed(2)}ms,'
          'extract=${median(measured.map((e) => e.extract)).toStringAsFixed(2)}ms)',
        );

        if (configuration.label == 'cpu' && configuration.threads == 4) {
          final rssBefore = ProcessInfo.currentRss;
          final maxRssBefore = ProcessInfo.maxRss;
          for (var iteration = 0; iteration < 200; iteration++) {
            // Exercise the reported ORT 1.24 KleidiAI issue with changing
            // inputs, matching the application's long-running indexer.
            for (var index = 0; index < tensor.length; index++) {
              tensor[index] = ((index + iteration) & 0xff) / 255.0;
            }
            await runOnce(session);
          }
          final mib = 1024 * 1024;
          _log.info(
            'PICQUERY_VISUAL_MEMORY runs=200 '
            'rss_before=${(rssBefore / mib).toStringAsFixed(1)}MiB '
            'rss_after=${(ProcessInfo.currentRss / mib).toStringAsFixed(1)}MiB '
            'max_before=${(maxRssBefore / mib).toStringAsFixed(1)}MiB '
            'max_after=${(ProcessInfo.maxRss / mib).toStringAsFixed(1)}MiB',
          );
        }
      } finally {
        await session.close();
      }
    }
  });
}
