package me.grey.picquery.common

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import me.grey.picquery.PicQueryApplication

private val context
    get() = PicQueryApplication.context


fun showToast(text: String, longToast: Boolean = false) {
    val duration = if (longToast) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    Toast.makeText(context, text, duration).show()
}


fun showConfirmDialog(
    context: Context,
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(confirmText) { dialog, _ ->
            onConfirm()
            dialog.dismiss()
        }
        .setNegativeButton(cancelText) { dialog, _ ->
            onCancel()
            dialog.dismiss()
        }
        .create()

    dialog.show()
}

fun showBottomSelectSheet() {

}

