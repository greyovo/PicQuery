import 'package:flutter/material.dart';

extension ColorSchemeExt on BuildContext {
  ColorScheme get colors => Theme.of(this).colorScheme;
}
