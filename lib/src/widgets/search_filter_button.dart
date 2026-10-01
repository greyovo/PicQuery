import 'package:flutter/material.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';

const double kSearchFilterButtonBorderRadius = 10;

/// A consistently styled filter control that can either perform an action or
/// open a popup menu.
class SearchFilterButton<T> extends StatelessWidget {
  const SearchFilterButton.action({
    super.key,
    required this.icon,
    required this.label,
    required VoidCallback this.onPressed,
    this.iconAlignment = IconAlignment.start,
  }) : initialValue = null,
       onSelected = null,
       items = null;

  const SearchFilterButton.menu({
    super.key,
    required this.icon,
    required this.label,
    required T this.initialValue,
    required ValueChanged<T> this.onSelected,
    required List<AppMenuItem<T>> this.items,
    this.iconAlignment = IconAlignment.start,
  }) : onPressed = null;

  final Widget icon;
  final Widget label;
  final IconAlignment iconAlignment;
  final VoidCallback? onPressed;
  final T? initialValue;
  final ValueChanged<T>? onSelected;
  final List<AppMenuItem<T>>? items;

  @override
  Widget build(BuildContext context) {
    final button = OutlinedButton.icon(
      onPressed: onPressed ?? () {},
      icon: icon,
      label: label,
      iconAlignment: iconAlignment,
      style: OutlinedButton.styleFrom(
        foregroundColor: Theme.of(context).colorScheme.onSurface,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        visualDensity: VisualDensity.compact,
        textStyle: Theme.of(context).textTheme.labelMedium,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(kSearchFilterButtonBorderRadius),
        ),
        side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant),
      ),
    );

    if (items == null) return button;

    return AppMenuButton<T>(
      selectedValue: initialValue,
      onSelected: onSelected!,
      items: items!,
      builder: (context, controller, child) => InkWell(
        borderRadius: BorderRadius.circular(kSearchFilterButtonBorderRadius),
        onTap: () => controller.isOpen ? controller.close() : controller.open(),
        child: IgnorePointer(child: button),
      ),
    );
  }
}
