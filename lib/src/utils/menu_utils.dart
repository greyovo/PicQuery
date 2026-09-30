import 'package:flutter/material.dart';

/// 显示一个菜单，位置在指定的按钮下方
/// 注意：context 必须为所点击的按钮所在的最近的 context，建议使用 Builder 组件包裹按钮获取正确的 context
Future<T?> showMenuAt<T>({
  required BuildContext context,
  required List<PopupMenuEntry<T>> items,
}) async {
  final button = context.findRenderObject() as RenderBox;
  final overlay = Overlay.of(context).context.findRenderObject() as RenderBox;
  final position = RelativeRect.fromRect(
    Rect.fromPoints(
      button.localToGlobal(Offset.zero, ancestor: overlay),
      button.localToGlobal(button.size.bottomRight(Offset.zero), ancestor: overlay),
    ),
    Offset.zero & overlay.size,
  );
  return showMenu<T>(
    context: context,
    position: position,
    items: items,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(10),
    ),
    clipBehavior: Clip.hardEdge,
    menuPadding: EdgeInsets.zero,
  );
}
