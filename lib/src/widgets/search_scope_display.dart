import 'package:flutter/material.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/widgets/app_menu_button.dart';

class SearchScopeDisplay extends StatelessWidget {
  const SearchScopeDisplay({
    super.key,
    required this.isCustom,
    required this.scopeSubtitle,
    required this.onScopeSelected,
  });

  final bool isCustom;
  final String? scopeSubtitle;
  final ValueChanged<String> onScopeSelected;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      autofocus: false,
      contentPadding: EdgeInsets.only(left: 16, right: 8, bottom: 2),
      title: Text(context.l10n.searchScope),
      subtitle: isCustom && scopeSubtitle != null && scopeSubtitle!.isNotEmpty
          ? Text(scopeSubtitle!, maxLines: 2, overflow: TextOverflow.ellipsis)
          : null,
      trailing: AppMenuButton<String>(
        selectedValue: isCustom ? 'custom' : 'all',
        onSelected: onScopeSelected,
        items: [
          AppMenuItem(value: 'all', child: Text(context.l10n.all)),
          AppMenuItem(value: 'custom', child: Text(context.l10n.custom)),
        ],
        builder: (context, controller, child) => TextButton.icon(
          onPressed: () =>
              controller.isOpen ? controller.close() : controller.open(),
          iconAlignment: IconAlignment.end,
          icon: const Icon(Icons.arrow_drop_down),
          style: TextButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 8),
          ),
          label: Text(isCustom ? context.l10n.custom : context.l10n.all),
        ),
      ),
      onTap: isCustom ? () => onScopeSelected('custom') : null,
    );
  }
}
