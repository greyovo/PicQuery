import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:picquery_app/src/engine/image_preprocess.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'reusable worker preprocesses repeated requests and returns dimensions',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'picquery-preprocess-test-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final path = '${directory.path}/sample.png';
      final source = img.Image(width: 320, height: 240, numChannels: 3);
      source.setPixelRgb(160, 120, 255, 128, 0);
      await File(path).writeAsBytes(img.encodePng(source));

      final first = await preprocessImageWithMetadata(path);
      final second = await preprocessImageWithMetadata(path);

      expect((first.width, first.height), (320, 240));
      expect(first.tensor, hasLength(kTensorSize));
      expect(second.tensor, hasLength(kTensorSize));
      expect(second.tensor, orderedEquals(first.tensor));
    },
  );
  for (final dimensions in [(1, 1), (32, 64), (512, 32)]) {
    test('small image $dimensions is centered with black padding', () async {
      final directory = await Directory.systemTemp.createTemp(
        'picquery-small-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final file = File('${directory.path}/small.png');
      final source = img.Image(width: dimensions.$1, height: dimensions.$2);
      img.fill(source, color: img.ColorRgb8(255, 128, 64));
      await file.writeAsBytes(img.encodePng(source));
      final result = await preprocessImageWithMetadata(file.path);
      expect((result.width, result.height), dimensions);
      expect(result.tensor, hasLength(kTensorSize));
      const plane = kResizeDim * kResizeDim;
      final copyWidth = dimensions.$1.clamp(1, kResizeDim);
      final copyHeight = dimensions.$2.clamp(1, kResizeDim);
      final center =
          ((kResizeDim - copyHeight) ~/ 2 + copyHeight ~/ 2) * kResizeDim +
          (kResizeDim - copyWidth) ~/ 2 +
          copyWidth ~/ 2;
      expect(result.tensor[center], 1);
      expect(result.tensor[plane + center], closeTo(128 / 255, 1e-6));
      expect(result.tensor[2 * plane + center], closeTo(64 / 255, 1e-6));
      expect(result.tensor[0], 0);
      expect(result.tensor[plane], 0);
      expect(result.tensor[2 * plane], 0);
    });
  }

  test(
    'missing and corrupt files fail without poisoning the next decode',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'picquery-invalid-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final corrupt = File('${directory.path}/corrupt.png');
      await corrupt.writeAsBytes([0, 1, 2]);
      await expectLater(preprocessImage(corrupt.path), throwsA(anything));
      await expectLater(
        preprocessImage('${directory.path}/missing.png'),
        throwsA(anything),
      );
      final valid = File('${directory.path}/valid.png');
      await valid.writeAsBytes(
        img.encodePng(img.Image(width: 256, height: 256)),
      );
      expect(await preprocessImage(valid.path), hasLength(kTensorSize));
    },
  );
}
