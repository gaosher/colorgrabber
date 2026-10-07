package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RoiRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * mean：ROI 平均色（开启离群剔除时只统计保留下来的像素）。
 * overexposedRatio / stdDev：始终按 ROI 内全部像素统计，用于判断过曝与是否均匀，
 * 不受离群剔除影响（否则剔除会掩盖反光、阴影等问题）。
 * stdDev 为三通道各自标准差中的最大值。
 */
data class RoiResult(val mean: Rgb, val overexposedRatio: Double, val stdDev: Double = 0.0)

object RoiSampler {
    private const val OVEREXPOSED = 250

    /** 默认离群剔除阈值：亮度偏离均值超过 2.1 倍标准差的像素不计入平均。 */
    const val DEFAULT_REJECT_SIGMA = 2.1
    /** 剔除范围下限（亮度 0..255），避免非常均匀的区域因标准差过小而误剔正常像素。 */
    private const val MIN_REJECT_RANGE = 1.5

    /**
     * px 为 ARGB 行优先数组，宽 width 高 height；返回 ROI 内平均 RGB、过曝比例与标准差。
     * step > 1 时每隔 step 个像素取一个，用于大面积快速估计。
     * rejectSigma > 0 时开启离群剔除：按亮度 (max+min)/2 剔除偏离均值超过
     * rejectSigma 倍标准差的像素（气泡、反光点、管壁边缘等）后再求平均。
     */
    fun sample(
        px: IntArray, width: Int, height: Int, roi: RoiRect,
        step: Int = 1, rejectSigma: Double = 0.0
    ): RoiResult {
        val x0 = roi.x.coerceIn(0, width)
        val y0 = roi.y.coerceIn(0, height)
        val x1 = (roi.x + roi.w).coerceIn(0, width)
        val y1 = (roi.y + roi.h).coerceIn(0, height)
        var sumR = 0L; var sumG = 0L; var sumB = 0L; var n = 0L; var over = 0L
        var sqR = 0L; var sqG = 0L; var sqB = 0L
        var sumL = 0L; var sqL = 0L   // 亮度×2，即 max+min，保持整数运算
        for (y in y0 until y1 step step) {
            for (x in x0 until x1 step step) {
                val c = px[y * width + x]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                sumR += r; sumG += g; sumB += b; n++
                sqR += r * r; sqG += g * g; sqB += b * b
                val l2 = lightness2(r, g, b)
                sumL += l2; sqL += l2 * l2
                if (r >= OVEREXPOSED || g >= OVEREXPOSED || b >= OVEREXPOSED) over++
            }
        }
        if (n == 0L) return RoiResult(Rgb(0, 0, 0), 0.0)

        fun std(sum: Long, sq: Long): Double {
            val m = sum.toDouble() / n
            return sqrt((sq.toDouble() / n - m * m).coerceAtLeast(0.0))
        }
        val stdDev = maxOf(std(sumR, sqR), std(sumG, sqG), std(sumB, sqB))
        val overRatio = over.toDouble() / n

        var mR = sumR; var mG = sumG; var mB = sumB; var mN = n
        if (rejectSigma > 0) {
            val meanL2 = sumL.toDouble() / n
            val range2 = max(rejectSigma * std(sumL, sqL), MIN_REJECT_RANGE * 2)
            var kR = 0L; var kG = 0L; var kB = 0L; var k = 0L
            for (y in y0 until y1 step step) {
                for (x in x0 until x1 step step) {
                    val c = px[y * width + x]
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    if (abs(lightness2(r, g, b) - meanL2) <= range2) {
                        kR += r; kG += g; kB += b; k++
                    }
                }
            }
            if (k > 0) { mR = kR; mG = kG; mB = kB; mN = k }
        }
        val mean = Rgb((mR.toDouble() / mN).roundToInt(),
                       (mG.toDouble() / mN).roundToInt(),
                       (mB.toDouble() / mN).roundToInt())
        return RoiResult(mean, overRatio, stdDev)
    }

    private fun lightness2(r: Int, g: Int, b: Int): Int = max(r, max(g, b)) + min(r, min(g, b))
}
