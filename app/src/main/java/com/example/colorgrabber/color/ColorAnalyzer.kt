package com.example.colorgrabber.color

import kotlin.math.max
import kotlin.math.min

object ColorAnalyzer {

    fun toHsv(rgb: Rgb): Hsv {
        val r = rgb.r / 255.0
        val g = rgb.g / 255.0
        val b = rgb.b / 255.0
        val cmax = max(r, max(g, b))
        val cmin = min(r, min(g, b))
        val delta = cmax - cmin
        val h = when {
            delta == 0.0 -> 0.0
            cmax == r -> 60.0 * (((g - b) / delta) % 6.0)
            cmax == g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }.let { if (it < 0) it + 360.0 else it }
        val s = if (cmax == 0.0) 0.0 else delta / cmax
        return Hsv(h, s, cmax)
    }
}
