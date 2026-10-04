import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/engine/ort_engine.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('Android float32 transport and visual CPU correctness', (
    _,
  ) async {
    final directory = await Directory.systemTemp.createTemp('ort-correctness');
    final data = await rootBundle.load(
      'assets/models/mobileclip2_s0_visual.onnx',
    );
    final file = File('${directory.path}/visual.onnx');
    await file.writeAsBytes(
      data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
    );
    final session = await OnnxRuntime().createSession(
      file.path,
      options: OrtSessionOptions(
        providers: const [OrtProvider.CPU],
        graphOptimizationLevel: OrtGraphOptimizationLevel.basic,
      ),
    );
    try {
      final tensor = Float32List(3 * 256 * 256);
      for (var i = 0; i < tensor.length; i++) {
        tensor[i] = (i % 256) / 255.0;
      }
      for (final reusable in [false, true]) {
        final input = reusable
            ? await OrtValue.fromReusableFloat32Bytes(
                tensor.buffer.asUint8List(),
                [1, 3, 256, 256],
              )
            : await OrtValue.fromList(tensor, [1, 3, 256, 256]);
        try {
          final roundtrip = await input.asFlattenedList();
          expect(
            roundtrip,
            orderedEquals(tensor),
            reason: 'reusable=$reusable input roundtrip',
          );
          if (reusable) {
            // Verify updates alter the actual native storage, including its tail.
            for (var i = 0; i < tensor.length; i++) {
              tensor[i] = ((i + 17) % 256) / 255.0;
            }
            await input.updateReusableFloat32Bytes(tensor.buffer.asUint8List());
            expect(await input.asFlattenedList(), orderedEquals(tensor));
          }
          final outputs = await session.run({session.inputNames.first: input});
          try {
            final values = await outputs.values.first.asFlattenedList();
            final nonFinite = values.where((v) => !(v as num).isFinite).length;
            Logger('test.android_ort').info(
              'ORT_CORRECTNESS reusable=$reusable nonFinite=$nonFinite sample=${values.take(5).toList()}',
            );
            expect(values, hasLength(512));
            expect(nonFinite, 0);
          } finally {
            for (final output in outputs.values) {
              await output.dispose();
            }
          }
        } finally {
          await input.dispose();
        }
      }
      final textData = await rootBundle.load(
        'assets/models/mobileclip2_s0_text.onnx',
      );
      final textFile = File('${directory.path}/text.onnx');
      await textFile.writeAsBytes(
        textData.buffer.asUint8List(
          textData.offsetInBytes,
          textData.lengthInBytes,
        ),
      );
      await OrtEngine.instance.loadClipModels(
        textModelPath: textFile.path,
        visualModelPath: file.path,
        force: true,
      );
      for (var i = 0; i < 3; i++) {
        tensor[0] = i / 3;
        final embedding = await OrtEngine.instance.encodeImage(tensor);
        expect(embedding, hasLength(512));
        expect(embedding.every((v) => v.isFinite), isTrue);
        expect(
          embedding.fold<double>(0, (sum, v) => sum + v * v),
          closeTo(1, 1e-5),
        );
      }
      final textEmbedding = await OrtEngine.instance.encodeText(
        'a photo of a cat',
      );
      expect(textEmbedding.every((v) => v.isFinite), isTrue);
      expect(
        textEmbedding.fold<double>(0, (sum, v) => sum + v * v),
        closeTo(1, 1e-5),
      );
    } finally {
      await session.close();
      await directory.delete(recursive: true);
    }
  });
}
