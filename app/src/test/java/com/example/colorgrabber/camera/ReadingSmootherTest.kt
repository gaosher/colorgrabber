package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingSmootherTest {

    @Test fun averagesRecentReadings() {
        val s = ReadingSmoother(window = 4)
        s.add(Rgb(100, 100, 100))
        s.add(Rgb(102, 98, 100))
        assertEquals(Rgb(101, 99, 100), s.add(Rgb(101, 99, 100)))
    }

    @Test fun keepsOnlyLastWindow() {
        val s = ReadingSmoother(window = 2)
        s.add(Rgb(100, 100, 100))
        s.add(Rgb(110, 110, 110))
        assertEquals(Rgb(115, 115, 115), s.add(Rgb(120, 120, 120)))   // 只平均 110、120
    }

    @Test fun largeJumpRestarts() {
        // 换了样品（距离远超阈值）：立即用新读数，不和旧读数平均
        val s = ReadingSmoother(window = 8, jumpThreshold = 33)
        repeat(5) { s.add(Rgb(200, 100, 100)) }
        assertEquals(Rgb(100, 200, 100), s.add(Rgb(100, 200, 100)))
    }

    @Test fun resetClears() {
        val s = ReadingSmoother()
        s.add(Rgb(1, 2, 3))
        s.reset()
        assertNull(s.current())
    }
}
