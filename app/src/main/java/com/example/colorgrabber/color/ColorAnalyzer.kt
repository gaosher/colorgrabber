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

    private fun invGamma(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    private fun f(t: Double): Double =
        if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116.0

    fun toLab(rgb: Rgb): Lab {
        val r = invGamma(rgb.r / 255.0)
        val g = invGamma(rgb.g / 255.0)
        val b = invGamma(rgb.b / 255.0)
        // sRGB D65 -> XYZ
        val x = 0.4124 * r + 0.3576 * g + 0.1805 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = 0.0193 * r + 0.1192 * g + 0.9505 * b
        // D65 reference white
        val xn = 0.95047; val yn = 1.0; val zn = 1.08883
        val fx = f(x / xn); val fy = f(y / yn); val fz = f(z / zn)
        val l = 116.0 * fy - 16.0
        val aa = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        return Lab(l, aa, bb)
    }
}
