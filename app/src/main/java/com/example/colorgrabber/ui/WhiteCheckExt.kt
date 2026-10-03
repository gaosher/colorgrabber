package com.example.colorgrabber.ui

import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.colorgrabber.wb.WhiteIssue
import com.google.android.material.snackbar.Snackbar

/**
 * 点白完成后的反馈：选区合格时简短提示；有问题时用多行 Snackbar 逐条列出
 * （Android 12+ 的 Toast 最多显示两行，放不下多条提示）。
 */
fun View.showWhiteCheck(issues: List<WhiteIssue>, okText: String) {
    if (issues.isEmpty()) {
        Toast.makeText(context, okText, Toast.LENGTH_SHORT).show()
        return
    }
    val text = "已点白校准，但白点可能不理想：\n" + issues.joinToString("\n") { "· ${it.message}" }
    val bar = Snackbar.make(this, text, 8000)
    bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = 6
    bar.setAction("知道了") {}
    bar.show()
}
