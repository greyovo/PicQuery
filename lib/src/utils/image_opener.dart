import 'dart:io';

import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:url_launcher/url_launcher.dart';

const _mediaChannel = MethodChannel('picquery/media');

/// Opens the original Android media item, or the Photos app on iOS.
/// iOS does not expose a supported URL for selecting a particular photo.
Future<void> openImageExternally(String path) async {
  if (Platform.isIOS) {
    // Best effort: Photos' redirect scheme is not a documented Apple API,
    // so check the launch result and report failure on unsupported systems.
    // Launch directly: canLaunchUrl requires a query-scheme allowlist and
    // can incorrectly reject a URL the system is able to open.
    final opened = await launchUrl(
      Uri.parse('photos-redirect://'),
      mode: LaunchMode.externalApplication,
    );
    if (!opened) {
      throw PlatformException(
        code: 'open_failed',
        message: 'Unable to open Photos',
      );
    }
    return;
  }

  if (Platform.isAndroid) {
    final opened = await _mediaChannel.invokeMethod<bool>('openImage', {
      'path': path,
    });
    if (opened == true) return;
    // Files outside MediaStore can still be viewed using FileProvider.
  }

  final result = await OpenFilex.open(path);
  if (result.type != ResultType.done) {
    throw PlatformException(code: result.type.name, message: result.message);
  }
}
