// Run: fvm dart run scripts/generate_desktop_icons.dart [square-source.png]
import 'dart:io';
import 'dart:math' as math;

import 'package:image/image.dart' as img;

void main(List<String> args) {
  if (args.length > 1) {
    throw ArgumentError('Expected one optional source path.');
  }
  final root = File.fromUri(Platform.script).parent.parent.path;
  final path = args.isEmpty ? '$root/assets/icon-picquery.png' : args.single;
  final decoded = img.decodeImage(File(path).readAsBytesSync());
  if (decoded == null || decoded.width != decoded.height) {
    throw ArgumentError('Source must be a decodable square image: $path');
  }
  final source = decoded.convert(numChannels: 4);

  img.Image render(int size, {required bool macos}) {
    // Supersampling smooths the rounded boundary, including small ICO frames.
    final canvasSize = size * 4;
    final tileSize = macos ? (canvasSize * 824 / 1024).round() : canvasSize;
    final tile = img.copyResize(
      source,
      width: tileSize,
      height: tileSize,
      interpolation: img.Interpolation.cubic,
    );
    final radius = tileSize * 225 / 1024;
    for (final pixel in tile) {
      final x = pixel.x + 0.5;
      final y = pixel.y + 0.5;
      final dx = math.max(math.max(radius - x, x - (tileSize - radius)), 0.0);
      final dy = math.max(math.max(radius - y, y - (tileSize - radius)), 0.0);
      if (dx * dx + dy * dy > radius * radius) pixel.a = 0;
    }
    final canvas = img.Image(
      width: canvasSize,
      height: canvasSize,
      numChannels: 4,
    );
    final inset = (canvasSize - tileSize) ~/ 2;
    for (final pixel in tile) {
      canvas.setPixelRgba(
        pixel.x + inset,
        pixel.y + inset,
        pixel.r,
        pixel.g,
        pixel.b,
        pixel.a,
      );
    }
    final result = img.copyResize(
      canvas,
      width: size,
      height: size,
      interpolation: img.Interpolation.average,
    );
    final last = size - 1;
    for (final point in [(0, 0), (last, 0), (0, last), (last, last)]) {
      if (result.getPixel(point.$1, point.$2).a != 0) {
        throw StateError('Icon corners must be transparent.');
      }
    }
    if (result.getPixel(size ~/ 2, size ~/ 2).a == 0) {
      throw StateError('Icon center must remain visible.');
    }
    return result;
  }

  for (final size in [16, 32, 64, 128, 256, 512, 1024]) {
    File(
      '$root/macos/Runner/Assets.xcassets/AppIcon.appiconset/app_icon_$size.png',
    ).writeAsBytesSync(img.encodePng(render(size, macos: true)));
  }
  final frames = [
    256,
    128,
    64,
    48,
    32,
    24,
    16,
  ].map((size) => render(size, macos: false)).toList();
  File('$root/windows/runner/resources/app_icon.ico')
      .writeAsBytesSync(img.IcoEncoder().encodeImages(frames));
  stdout.writeln(
    'Generated and verified 7 macOS PNGs and 7 Windows ICO sizes.',
  );
}
