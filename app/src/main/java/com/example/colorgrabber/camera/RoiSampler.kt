package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import kotlin.math.roundToInt

data class RoiRect(val x: Int, val y: Int, val w: Int, val h: Int)

data class RoiResult(val mean: Rgb, val overexposedRatio: Double)

object RoiSampler {
    private const val OVEREXPOSED = 250

    /** px 为 ARGB 行优先数组，宽 width 高 height；返回 ROI 内平均 RGB 与过曝比例。 */
    fun sample(px: IntArray, width: Int, height: Int, roi: RoiRect): RoiResult {
        val x0 = roi.x.coerceIn(0, width)
        val y0 = roi.y.coerceIn(0, height)
        val x1 = (roi.x + roi.w).coerceIn(0, width)
        val y1 = (roi.y + roi.h).coerceIn(0, height)
        var sumR = 0L; var sumG = 0L; var sumB = 0L; var n = 0L; var over = 0L
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = px[y * width + x]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                sumR += r; sumG += g; sumB += b; n++
                if (r >= OVEREXPOSED || g >= OVEREXPOSED || b >= OVEREXPOSED) over++
            }
        }
        if (n == 0L) return RoiResult(Rgb(0, 0, 0), 0.0)
        val mean = Rgb((sumR.toDouble() / n).roundToInt(),
                       (sumG.toDouble() / n).roundToInt(),
                       (sumB.toDouble() / n).roundToInt())
        return RoiResult(mean, over.toDouble() / n)
    }
}
