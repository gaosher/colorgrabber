package com.example.colorgrabber.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

/** 收集样品名/备注/降解时间点的对话框；确认后回调三元组。 */
object RecordDialog {
    fun show(
        context: Context,
        onConfirm: (sampleName: String, note: String, degradationTime: String) -> Unit
    ) {
        fun field(hint: String) = EditText(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val nameEt = field("样品名（可空）")
        val degEt = field("降解时间点（可空，如 30min）")
        val noteEt = field("备注（可空）")
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(nameEt); addView(degEt); addView(noteEt)
        }
        AlertDialog.Builder(context)
            .setTitle("保存记录")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                onConfirm(nameEt.text.toString().trim(),
                          noteEt.text.toString().trim(),
                          degEt.text.toString().trim())
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
