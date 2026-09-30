import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:picquery_app/src/engine/image_preprocess.dart';

void main() {
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
}
