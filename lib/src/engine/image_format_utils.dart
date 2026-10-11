import 'dart:io';

import 'package:flutter/foundation.dart';

abstract final class ImageFormatUtils {
  /// Read a bounded header to identify an image format. Extensions can be wrong,
  /// and recognizing a container does not imply the platform can decode it.
  static Future<String> detectImageFormat(String path) =>
      _detectImageFormat(path);

  static Future<String> _detectImageFormat(String path) async {
    RandomAccessFile? file;
    try {
      file = await File(path).open();
      final header = await file.read(64);
      bool startsWith(List<int> signature) =>
          header.length >= signature.length &&
          listEquals(header.sublist(0, signature.length), signature);
      String tag(int offset, int length) => header.length >= offset + length
          ? String.fromCharCodes(header.sublist(offset, offset + length))
          : '';

      if (startsWith([0xff, 0xd8, 0xff])) return 'jpeg';
      if (startsWith([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) {
        return 'png';
      }
      if (tag(0, 6) == 'GIF87a' || tag(0, 6) == 'GIF89a') return 'gif';
      if (tag(0, 2) == 'BM') return 'bmp';
      if (tag(0, 4) == 'RIFF' && tag(8, 4) == 'WEBP') return 'webp';
      if (startsWith([0x49, 0x49, 0x2a, 0x00]) ||
          startsWith([0x4d, 0x4d, 0x00, 0x2a])) {
        return 'tiff';
      }
      if (tag(4, 4) == 'ftyp') {
        final brand = tag(8, 4);
        // Inspect major and compatible brands within the ftyp box only.
        final boxSize = ByteData.sublistView(header).getUint32(0);
        final brands = [
          brand,
          for (
            var offset = 16;
            offset + 4 <= header.length && offset + 4 <= boxSize;
            offset += 4
          )
            tag(offset, 4),
        ];
        if (brands.any((value) => value == 'avif' || value == 'avis')) {
          return 'avif';
        }
        if (brands.any(
          (value) => ['heic', 'heix', 'hevc', 'hevx'].contains(value),
        )) {
          return 'heic';
        }
        if (brands.any((value) => value == 'mif1' || value == 'msf1')) {
          return 'heif';
        }
        return 'iso-bmff(brand=$brand)';
      }
      return header.isEmpty ? 'empty' : 'unknown';
    } catch (_) {
      return 'unreadable';
    } finally {
      // Diagnostic collection must never replace the original indexing error.
      try {
        await file?.close();
      } catch (_) {}
    }
  }
}
