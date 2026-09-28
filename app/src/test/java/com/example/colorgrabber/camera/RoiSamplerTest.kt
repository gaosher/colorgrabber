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
}
