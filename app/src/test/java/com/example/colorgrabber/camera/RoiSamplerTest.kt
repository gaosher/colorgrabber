package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoiSamplerTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun averagesRoiOnly() {
        // 2x2 图：左上红，其余黑；ROI 只覆盖左上 1x1
        val w = 2; val h = 2
        val px = intArrayOf(
            argb(200, 100, 50), argb(0, 0, 0),
            argb(0, 0, 0),      argb(0, 0, 0)
        )
        val res = RoiSampler.sample(px, w, h, RoiRect(0, 0, 1, 1))
        assertEquals(Rgb(200, 100, 50), res.mean)
    }

    @Test fun reportsOverexposure() {
        val w = 2; val h = 1
        val px = intArrayOf(argb(255, 255, 255), argb(10, 10, 10))
        val res = RoiSampler.sample(px, w, h, RoiRect(0, 0, 2, 1))
        assertTrue(res.overexposedRatio in 0.49..0.51)
    }

    @Test fun uniformRoiHasZeroStdDev() {
        val px = IntArray(4) { argb(200, 190, 180) }
        val res = RoiSampler.sample(px, 2, 2, RoiRect(0, 0, 2, 2))
        assertEquals(0.0, res.stdDev, 1e-9)
    }

    @Test fun stdDevIsMaxOverChannels() {
        // R 通道 100/200 → 标准差 50；G、B 恒定 → 0
        val px = intArrayOf(argb(100, 80, 80), argb(200, 80, 80))
        val res = RoiSampler.sample(px, 2, 1, RoiRect(0, 0, 2, 1))
        assertEquals(50.0, res.stdDev, 1e-9)
    }

    @Test fun rejectSigmaDropsOutliersFromMean() {
        // 10×10 均匀溶液色中有一个反光点（白）：不剔除时平均被拉高，剔除后回到溶液色
        val w = 10; val h = 10
        val px = IntArray(w * h) { argb(200, 100, 120) }
        px[55] = argb(255, 255, 255)
        val roi = RoiRect(0, 0, w, h)
        val plain = RoiSampler.sample(px, w, h, roi)
        val clipped = RoiSampler.sample(px, w, h, roi, rejectSigma = RoiSampler.DEFAULT_REJECT_SIGMA)
        assertEquals(Rgb(201, 102, 121), plain.mean)
        assertEquals(Rgb(200, 100, 120), clipped.mean)
        // 过曝比例与标准差仍按全部像素统计，不被剔除掩盖
        assertEquals(plain.overexposedRatio, clipped.overexposedRatio, 1e-9)
        assertEquals(plain.stdDev, clipped.stdDev, 1e-9)
    }

    @Test fun rejectSigmaKeepsUniformNoise() {
        // 只有 ±1 的轻微噪声：不应剔除任何像素（剔除范围有下限）
        val px = intArrayOf(argb(200, 200, 200), argb(201, 201, 201), argb(199, 199, 199), argb(200, 200, 200))
        val res = RoiSampler.sample(px, 2, 2, RoiRect(0, 0, 2, 2), rejectSigma = RoiSampler.DEFAULT_REJECT_SIGMA)
        assertEquals(Rgb(200, 200, 200), res.mean)
    }
}
