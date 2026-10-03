import 'dart:io';

import 'package:flutter/material.dart';

Future<T?> showContentInDialogOrPage<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
}) {
  if (context.isLargeScreen) {
    return showDialog<T>(
      context: context,
      barrierDismissible: barrierDismissible,
      builder: (context) {
        return _Dialog(builder: builder);
      },
    );
  } else {
    return Navigator.push<T>(context, MaterialPageRoute(builder: builder));
  }
}

class _Dialog extends StatelessWidget {
  const _Dialog({required this.builder});

  final WidgetBuilder builder;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560, maxHeight: 640),
          child: Navigator(
            onGenerateRoute: (settings) {
              return MaterialPageRoute(builder: builder);
            },
          ),
        ),
      ),
    );
  }
}

/// Adaptive grid sizing: mobile 180dp, medium 220dp, wide 260dp
double getAdaptiveMaxCrossAxisExtent(double screenWidth) {
  if (screenWidth < 600) {
    return 160;
  } else if (screenWidth < 1200) {
    return 200;
  } else {
    return 220;
  }
}

extension MediaQueryExt on BuildContext {
  /// Whether the device is a desktop (large screen).
  bool get isLargeScreen => MediaQuery.of(this).size.width >= 600;

  bool get useNavigationRail => isLargeScreen;
}

bool get isMobile => Platform.isAndroid || Platform.isIOS;

bool get isDesktop =>
    Platform.isWindows || Platform.isLinux || Platform.isMacOS;
