import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

class Toast {
  Toast._();

  static void showMessage(String message) {
    SmartDialog.showToast(message);
  }
}
