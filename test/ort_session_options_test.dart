import 'package:flutter_onnxruntime/flutter_onnxruntime.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('graph optimization defaults remain omitted', () {
    expect(
      OrtSessionOptions().toMap(),
      isNot(contains('graphOptimizationLevel')),
    );
  });

  test('Android graph optimization levels survive channel serialization', () {
    for (final level in OrtGraphOptimizationLevel.values) {
      expect(
        OrtSessionOptions(graphOptimizationLevel: level)
            .toMap()['graphOptimizationLevel'],
        level.name,
      );
    }
  });
}
