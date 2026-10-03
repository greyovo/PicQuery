import 'dart:io';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/utils/adaptive_display.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:window_manager/window_manager.dart';

/// Provides a transparent, draggable desktop title area while keeping window
/// controls accessible and application content clear of them.
class DesktopWindowFrame extends StatelessWidget {
  const DesktopWindowFrame({super.key, required this.child});

  final Widget child;

  static bool get isSupported =>
      Platform.isMacOS || Platform.isWindows || Platform.isLinux;

  @override
  Widget build(BuildContext context) {
    if (!isSupported) return child;

    final brightness = Theme.of(context).brightness;
    final titleBarHeight = Platform.isMacOS ? 30.0 : kWindowCaptionHeight;

    return ColoredBox(
      color: context.isLargeScreen
          ? context.colors.surfaceContainer
          : context.colors.surface,
      child: Column(
        children: [
          SizedBox(
            height: titleBarHeight,
            child: Platform.isMacOS
                ? const DragToMoveArea(child: SizedBox.expand())
                : WindowCaption(
                    title: const SizedBox.shrink(),
                    brightness: brightness,
                    backgroundColor: Colors.transparent,
                  ),
          ),
          Expanded(child: child),
        ],
      ),
    );
  }
}
