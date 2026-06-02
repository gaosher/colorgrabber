package com.example.colorgrabber.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.example.colorgrabber.color.Rgb

/** RGB → 十六进制串，如 #A09487。 */
fun hexOf(c: Rgb): String = "#%02X%02X%02X".format(c.r, c.g, c.b)

/** 把一个 View 当作色块：填充当前 RGB，并带一圈浅灰描边（避免接近白色时看不清边界）。 */
fun View.setSwatchColor(c: Rgb) {
    val d = (background as? GradientDrawable) ?: GradientDrawable().also {
        it.cornerRadius = 6f * resources.displayMetrics.density
        it.setStroke((1f * resources.displayMetrics.density).toInt() + 1, Color.parseColor("#888888"))
        background = it
    }
    d.setColor(Color.rgb(c.r, c.g, c.b))
}
