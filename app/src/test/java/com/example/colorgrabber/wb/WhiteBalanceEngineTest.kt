package com.example.colorgrabber.wb

import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteBalanceEngineTest {
    private val eps = 1e-3

    @Test fun gains_makeWhitePatchWhite() {
        // 量到的白偏黄 (240,220,180) → 增益使其归一到 255
        val g = WhiteBalanceEngine.gainsFromWhite(Rgb(240, 220, 180))
        assertEquals(255.0 / 240.0, g.r, eps)
        assertEquals(255.0 / 220.0, g.g, eps)
        assertEquals(255.0 / 180.0, g.b, eps)
    }

    @Test fun normalize_appliesGainsAndClamps() {
        val g = Gains(2.0, 1.0, 1.5)
        val out = WhiteBalanceEngine.normalize(Rgb(100, 100, 200), g)
        assertEquals(200, out.r)   // 100*2
        assertEquals(100, out.g)   // 100*1
        assertEquals(255, out.b)   // 200*1.5=300 → 钳到 255
    }

    @Test fun gains_clampedToMax() {
        // 极暗白 (10,10,10) 不应产生超大增益
        val g = WhiteBalanceEngine.gainsFromWhite(Rgb(10, 10, 10))
        assertEquals(WhiteBalanceEngine.MAX_GAIN, g.r, eps)
    }

    @Test fun tempAdjust_zeroIsIdentity() {
        val base = Gains(1.2, 1.0, 1.5)
        val g = WhiteBalanceEngine.applyTempAdjust(base, 0.0)
        assertEquals(1.2, g.r, eps); assertEquals(1.0, g.g, eps); assertEquals(1.5, g.b, eps)
    }

    @Test fun tempAdjust_warmRaisesRedLowersBlue() {
        val base = Gains(1.0, 1.0, 1.0)
        val g = WhiteBalanceEngine.applyTempAdjust(base, 1.0)  // 最暖
        assertEquals(1.0 * (1 + 0.5), g.r, eps)
        assertEquals(1.0, g.g, eps)
        assertEquals(1.0 * (1 - 0.5), g.b, eps)
    }

    @Test fun displayKelvin_mapping() {
        assertEquals(5500, WhiteBalanceEngine.displayKelvin(0.0))
        assertEquals(2800, WhiteBalanceEngine.displayKelvin(1.0))
        assertEquals(8200, WhiteBalanceEngine.displayKelvin(-1.0))
    }

    @Test fun rgbAdjust_identityAtOne() {
        val base = Gains(1.2, 1.0, 1.5)
        val g = WhiteBalanceEngine.applyRgbAdjust(base, 1.0, 1.0, 1.0)
        assertEquals(1.2, g.r, eps); assertEquals(1.0, g.g, eps); assertEquals(1.5, g.b, eps)
    }

    @Test fun rgbAdjust_scalesEachChannel() {
        val base = Gains(1.0, 2.0, 1.0)
        val g = WhiteBalanceEngine.applyRgbAdjust(base, 1.5, 0.5, 1.2)
        assertEquals(1.5, g.r, eps)   // 1.0*1.5
        assertEquals(1.0, g.g, eps)   // 2.0*0.5
        assertEquals(1.2, g.b, eps)   // 1.0*1.2
    }

    @Test fun rgbAdjust_clampedToMax() {
        val g = WhiteBalanceEngine.applyRgbAdjust(Gains(7.0, 1.0, 1.0), 1.5, 1.0, 1.0)
        assertEquals(WhiteBalanceEngine.MAX_GAIN, g.r, eps)  // 7*1.5=10.5 → 8
    }
}
