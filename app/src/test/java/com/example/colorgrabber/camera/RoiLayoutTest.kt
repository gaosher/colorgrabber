package com.example.colorgrabber.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class RoiLayoutTest {

    private val w = 1000; private val h = 600
    private val sample = RoiLayout.sampleFor(w, h)   // 边长 120，中心 (500, 300)

    @Test fun sampleIsCentered() {
        assertEquals(RoiRect(440, 240, 120, 120), sample)
    }

    @Test fun rotation0_referenceToTheRight() {
        // 边长 60，中心在样品中心右侧 120 处 → (620, 300)
        assertEquals(RoiRect(590, 270, 60, 60), RoiLayout.referenceFor(sample, w, h, 0))
    }

    @Test fun rotation90_referenceAbove() {
        // 帧需顺时针转 90° 才正：屏幕右侧对应帧的 −y
        assertEquals(RoiRect(470, 150, 60, 60), RoiLayout.referenceFor(sample, w, h, 90))
    }

    @Test fun rotation180_referenceToTheLeft() {
        assertEquals(RoiRect(350, 270, 60, 60), RoiLayout.referenceFor(sample, w, h, 180))
    }

    @Test fun rotation270_referenceBelow() {
        assertEquals(RoiRect(470, 390, 60, 60), RoiLayout.referenceFor(sample, w, h, 270))
    }

    @Test fun customOffset_rotation0() {
        // 屏幕偏移 (−1.5, 1)：样品中心左 180、下 120 → (320, 420)
        assertEquals(RoiRect(290, 390, 60, 60), RoiLayout.referenceFor(sample, w, h, 0, -1.5, 1.0))
    }

    @Test fun customOffset_rotation90() {
        // 90°：屏幕 (x, y) → 帧 (y, −x) = (1, 1.5)：右 120、下 180 → (620, 480)
        assertEquals(RoiRect(590, 450, 60, 60), RoiLayout.referenceFor(sample, w, h, 90, -1.5, 1.0))
    }

    @Test fun customOffset_rotation270() {
        // 270°：屏幕 (x, y) → 帧 (−y, x) = (−1, −1.5)：左 120、上 180 → (380, 120)
        assertEquals(RoiRect(350, 90, 60, 60), RoiLayout.referenceFor(sample, w, h, 270, -1.5, 1.0))
    }

    @Test fun defaultOffsetMatchesExplicitDefault() {
        assertEquals(
            RoiLayout.referenceFor(sample, w, h, 90),
            RoiLayout.referenceFor(sample, w, h, 90, RoiLayout.DEFAULT_REF_OFFSET_X, RoiLayout.DEFAULT_REF_OFFSET_Y)
        )
    }

    @Test fun farDetection() {
        assertEquals(false, RoiLayout.isFar(1.0, 0.0))
        assertEquals(false, RoiLayout.isFar(2.0, 2.0))   // 距离 ≈ 2.83
        assertEquals(true, RoiLayout.isFar(3.0, 1.0))    // 距离 ≈ 3.16
    }

    @Test fun referenceClampedInsideFrame() {
        val edge = RoiRect(940, 240, 60, 60)
        val ref = RoiLayout.referenceFor(edge, w, h, 0)
        assertEquals(w - ref.w, ref.x)
    }
}
