import 'package:flutter/material.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

class DesktopFolderDropHint extends StatelessWidget {
  const DesktopFolderDropHint({super.key});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
        child: Text(
          context.l10n.dragFolderIndexHint,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodySmall
              ?.copyWith(color: context.colors.onSurfaceVariant),
        ),
      ),
    );
  }
}
