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

    const val TEMP_SENSITIVITY = 0.5

    /** 在基础增益上施加色温偏移（暖向升 R 降 B），结果钳到 MAX_GAIN。 */
    fun applyTempAdjust(base: Gains, tempAdjust: Double): Gains {
        val t = tempAdjust.coerceIn(-1.0, 1.0)
        val r = (base.r * (1 + TEMP_SENSITIVITY * t)).coerceIn(0.0, MAX_GAIN)
        val b = (base.b * (1 - TEMP_SENSITIVITY * t)).coerceIn(0.0, MAX_GAIN)
        return Gains(r, base.g, b)
    }

    /** tempAdjust → 近似开尔文显示值。 */
    fun displayKelvin(tempAdjust: Double): Int =
        (5500 - tempAdjust.coerceIn(-1.0, 1.0) * 2700).roundToInt()
}
