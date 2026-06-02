package com.example.colorgrabber.wb

import com.example.colorgrabber.color.Rgb
import kotlin.math.roundToInt

/** 三通道增益 */
data class Gains(val r: Double, val g: Double, val b: Double)

object WhiteBalanceEngine {
    const val MAX_GAIN = 8.0
    private const val TARGET_WHITE = 255.0

    /** 由量到的白色 ROI 平均色，反推使其归一到纯白的增益（钳到 MAX_GAIN）。 */
    fun gainsFromWhite(white: Rgb): Gains {
        fun gain(c: Int): Double {
            val v = c.coerceAtLeast(1)
            return (TARGET_WHITE / v).coerceIn(0.0, MAX_GAIN)
        }
        return Gains(gain(white.r), gain(white.g), gain(white.b))
    }

    /** 对像素施加增益并钳到 0..255。 */
    fun normalize(rgb: Rgb, gains: Gains): Rgb {
        fun ap(c: Int, g: Double): Int = (c * g).roundToInt().coerceIn(0, 255)
        return Rgb(ap(rgb.r, gains.r), ap(rgb.g, gains.g), ap(rgb.b, gains.b))
    }
}
