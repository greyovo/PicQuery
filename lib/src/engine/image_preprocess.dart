// Image preprocessing for MobileCLIP.
//
// Decode directly to the small image used by the model. Do not decode a large
// photo into Dart memory only to discard most of it in a later resize.

import 'dart:isolate';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';

const int kResizeDim = 256;
const int kTensorSize = 3 * kResizeDim * kResizeDim;

/// Wall-clock timings for the stages of one image preprocessing operation.
final class ImagePreprocessTimings {
  const ImagePreprocessTimings({
    required this.readAndHeader,
    required this.decode,
    required this.crop,
    required this.tensor,
    required this.total,
  });

  final Duration readAndHeader;
  final Duration decode;
  final Duration crop;
  final Duration tensor;
  final Duration total;
}

/// Tensor and source dimensions produced by one decode.
final class PreprocessedImage {
  const PreprocessedImage({
    required this.tensor,
    required this.width,
    required this.height,
    required this.timings,
  });

  final Float32List tensor;
  final int width;
  final int height;
  final ImagePreprocessTimings timings;
}

/// A persistent isolate keeps CHW conversion off the UI isolate. It receives
/// only a 256-ish pixel image, never a full-resolution decoded bitmap.
final class _TensorWorker {
  _TensorWorker._();

  static final instance = _TensorWorker._();
  Future<SendPort>? _sendPortFuture;

  Future<SendPort> _sendPort() => _sendPortFuture ??= _spawn();

  Future<SendPort> _spawn() async {
    final ready = ReceivePort();
    await Isolate.spawn(
      _tensorWorkerMain,
      ready.sendPort,
      debugName: 'picquery-tensor-preprocessor',
    );
    return await ready.first as SendPort;
  }

  Future<Float32List> process(Uint8List rgba) async {
    final reply = ReceivePort();
    final port = await _sendPort();
    port.send((TransferableTypedData.fromList([rgba]), reply.sendPort));
    try {
      final response = await reply.first as Map<Object?, Object?>;
      final error = response['error'] as String?;
      if (error != null) throw FormatException(error);
      return (response['tensor']! as TransferableTypedData)
          .materialize()
          .asFloat32List();
    } finally {
      reply.close();
    }
  }
}

void _tensorWorkerMain(SendPort ready) {
  final requests = ReceivePort();
  ready.send(requests.sendPort);
  requests.listen((message) {
    final (rgbaData, reply) = message as (TransferableTypedData, SendPort);
    try {
      final rgba = rgbaData.materialize().asUint8List();
      if (rgba.length != kResizeDim * kResizeDim * 4) {
        throw ArgumentError(
          'Unexpected decoded pixel buffer length: ${rgba.length}',
        );
      }
      final tensor = Float32List(kTensorSize);
      const plane = kResizeDim * kResizeDim;
      for (var pixelIndex = 0; pixelIndex < plane; pixelIndex++) {
        final sourceIndex = pixelIndex * 4;
        tensor[pixelIndex] = rgba[sourceIndex] / 255.0;
        tensor[plane + pixelIndex] = rgba[sourceIndex + 1] / 255.0;
        tensor[2 * plane + pixelIndex] = rgba[sourceIndex + 2] / 255.0;
      }
      reply.send(<Object?, Object?>{
        'tensor': TransferableTypedData.fromList([
          tensor.buffer.asUint8List(tensor.offsetInBytes, tensor.lengthInBytes),
        ]),
      });
    } catch (error, stackTrace) {
      reply.send(<Object?, Object?>{'error': '$error\n$stackTrace'});
    }
  });
}

/// Decode [imagePath] at the model's required scale, center crop/pad to 256², and
/// convert to an RGB CHW float tensor in [0, 1].
///
/// `ImageDescriptor` reads source dimensions from the encoded header, while
/// Flutter's cross-platform codec receives the scaled target dimensions. JPEG
/// decoders can consequently downsample instead of materializing megapixels.
Future<PreprocessedImage> preprocessImageWithMetadata(String imagePath) async {
  final total = Stopwatch()..start();
  final buffer = await ui.ImmutableBuffer.fromFilePath(imagePath);
  ui.ImageDescriptor? descriptor;
  ui.Codec? codec;
  ui.Image? image;
  try {
    descriptor = await ui.ImageDescriptor.encoded(buffer);
    final width = descriptor.width;
    final height = descriptor.height;
    final readAndHeader = total.elapsed;
    // Keep small images at their native size and pad the missing area.
    final scale = (kResizeDim / (width < height ? width : height)).clamp(
      0.0,
      1.0,
    );
    final targetWidth = (width * scale).round().clamp(1, 0x7fffffff);
    final targetHeight = (height * scale).round().clamp(1, 0x7fffffff);

    final decode = Stopwatch()..start();
    codec = await descriptor.instantiateCodec(
      targetWidth: targetWidth,
      targetHeight: targetHeight,
    );
    final frame = await codec.getNextFrame();
    image = frame.image;
    final decodeElapsed = decode.elapsed;

    final copyWidth = image.width.clamp(1, kResizeDim);
    final copyHeight = image.height.clamp(1, kResizeDim);
    final cropX = (image.width - copyWidth) ~/ 2;
    final cropY = (image.height - copyHeight) ~/ 2;
    final padX = (kResizeDim - copyWidth) ~/ 2;
    final padY = (kResizeDim - copyHeight) ~/ 2;
    final crop = Stopwatch()..start();
    final rgbaData = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
    if (rgbaData == null) {
      throw FormatException('Could not read decoded pixels');
    }
    final rgba = rgbaData.buffer.asUint8List(
      rgbaData.offsetInBytes,
      rgbaData.lengthInBytes,
    );
    final cropped = Uint8List(kResizeDim * kResizeDim * 4);
    for (var y = 0; y < copyHeight; y++) {
      final from = ((cropY + y) * image.width + cropX) * 4;
      cropped.setRange(
        ((padY + y) * kResizeDim + padX) * 4,
        ((padY + y) * kResizeDim + padX + copyWidth) * 4,
        rgba,
        from,
      );
    }
    final cropElapsed = crop.elapsed;

    final tensorWatch = Stopwatch()..start();
    final tensor = await _TensorWorker.instance.process(cropped);
    final tensorElapsed = tensorWatch.elapsed;
    return PreprocessedImage(
      tensor: tensor,
      width: width,
      height: height,
      timings: ImagePreprocessTimings(
        readAndHeader: readAndHeader,
        decode: decodeElapsed,
        crop: cropElapsed,
        tensor: tensorElapsed,
        total: total.elapsed,
      ),
    );
  } finally {
    image?.dispose();
    codec?.dispose();
    descriptor?.dispose();
    buffer.dispose();
  }
}

Future<Float32List> preprocessImage(String imagePath) async =>
    (await preprocessImageWithMetadata(imagePath)).tensor;

/// Read only the decoder header when no inference is otherwise needed.
Future<(int, int)?> probeImageDimensions(String imagePath) async {
  try {
    final buffer = await ui.ImmutableBuffer.fromFilePath(imagePath);
    try {
      final descriptor = await ui.ImageDescriptor.encoded(buffer);
      try {
        return (descriptor.width, descriptor.height);
      } finally {
        descriptor.dispose();
      }
    } finally {
      buffer.dispose();
    }
  } catch (_) {
    return null;
  }
}
