import 'package:flutter/material.dart';

class AppMenuItem<T> {
  const AppMenuItem({
    required this.value,
    required this.child,
    this.leadingIcon,
    this.enabled = true,
  });

  final T value;
  final Widget child;
  final Widget? leadingIcon;
  final bool enabled;
}

/// Project-wide Material 3 popup menu with rounded menu items.
class AppMenuButton<T> extends StatelessWidget {
  const AppMenuButton({
    super.key,
    required this.items,
    required this.onSelected,
    required this.builder,
    this.selectedValue,
    this.alignmentOffset = Offset.zero,
  });

  final List<AppMenuItem<T>> items;
  final ValueChanged<T> onSelected;
  final MenuAnchorChildBuilder builder;
  final T? selectedValue;
  final Offset alignmentOffset;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;

    return MenuAnchor(
      animated: true,
      style: MenuStyle(
        padding: const WidgetStatePropertyAll(EdgeInsets.all(6)),
        shape: WidgetStatePropertyAll(
          RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      builder: builder,
      menuChildren: [
        for (final item in items)
          Semantics(
            selected: selectedValue == item.value,
            child: MenuItemButton(
              onPressed: item.enabled ? () => onSelected(item.value) : null,
              leadingIcon: item.leadingIcon,
              trailingIcon: selectedValue == item.value
                  ? Icon(Icons.check_rounded, color: colors.primary, size: 18)
                  : null,
              style: ButtonStyle(
                // minimumSize: const WidgetStatePropertyAll(Size(0, 40)),
                shape: WidgetStatePropertyAll(
                  RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
              child: item.child,
            ),
          ),
      ],
    );
  }
}
