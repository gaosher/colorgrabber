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
}
