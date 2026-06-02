package com.example.colorgrabber.color

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorAnalyzerTest {
    private val eps = 1e-2

    @Test fun hsv_pureRed() {
        val hsv = ColorAnalyzer.toHsv(Rgb(255, 0, 0))
        assertEquals(0.0, hsv.h, eps)
        assertEquals(1.0, hsv.s, eps)
        assertEquals(1.0, hsv.v, eps)
    }

    @Test fun hsv_white() {
        val hsv = ColorAnalyzer.toHsv(Rgb(255, 255, 255))
        assertEquals(0.0, hsv.s, eps)
        assertEquals(1.0, hsv.v, eps)
    }

    @Test fun hsv_green() {
        val hsv = ColorAnalyzer.toHsv(Rgb(0, 255, 0))
        assertEquals(120.0, hsv.h, eps)
    }

    @Test fun lab_white() {
        val lab = ColorAnalyzer.toLab(Rgb(255, 255, 255))
        assertEquals(100.0, lab.l, 1e-1)
        assertEquals(0.0, lab.a, 1e-1)
        assertEquals(0.0, lab.b, 1e-1)
    }

    @Test fun lab_black() {
        val lab = ColorAnalyzer.toLab(Rgb(0, 0, 0))
        assertEquals(0.0, lab.l, 1e-1)
    }

    @Test fun lab_red() {
        val lab = ColorAnalyzer.toLab(Rgb(255, 0, 0))
        assertEquals(53.24, lab.l, 0.5)
        assertEquals(80.09, lab.a, 0.5)
        assertEquals(67.20, lab.b, 0.5)
    }

    @Test fun absorbance_halfIntensity() {
        // I = I0/2 → A = -log10(0.5) ≈ 0.301
        val a = ColorAnalyzer.toAbsorbance(Rgb(128, 128, 128), Rgb(255, 255, 255))
        assertEquals(0.301, a.aR, 1e-2)
        assertEquals(0.301, a.aG, 1e-2)
        assertEquals(0.301, a.aB, 1e-2)
    }

    @Test fun absorbance_equalIsZero() {
        val a = ColorAnalyzer.toAbsorbance(Rgb(200, 200, 200), Rgb(200, 200, 200))
        assertEquals(0.0, a.aR, 1e-3)
    }

    @Test fun absorbance_clampsZeroSample() {
        // 样品为 0 时按 0.5 处理，避免 log(0)
        val a = ColorAnalyzer.toAbsorbance(Rgb(0, 0, 0), Rgb(255, 255, 255))
        assertEquals(-Math.log10(0.5 / 255.0), a.aR, 1e-3)
    }
}
