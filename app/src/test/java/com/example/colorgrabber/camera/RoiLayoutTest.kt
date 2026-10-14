package com.example.colorgrabber.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class RoiLayoutTest {

    private val w = 1000; private val h = 600                // 短边 600
    private val sample = RoiLayout.sampleFor(w, h)           // 默认 10% → 边长 60，中心 (500, 300)

    @Test fun sampleIsCenteredWithDefaultSize() {
        assertEquals(RoiRect(470, 270, 60, 60), sample)
    }

    @Test fun sampleFractionIsClamped() {
        assertEquals(18, RoiLayout.sampleFor(w, h, 0.01).w)  // 下限 3%
        assertEquals(120, RoiLayout.sampleFor(w, h, 0.5).w)  // 上限 20%
    }

    @Test fun defaultReference_allRotations() {
        // 默认偏移 (0.15, 0)：屏幕右侧 90 像素；参考白框与样品框同样大小
        assertEquals(RoiRect(560, 270, 60, 60), RoiLayout.referenceFor(sample, w, h, 0))
        assertEquals(RoiRect(470, 180, 60, 60), RoiLayout.referenceFor(sample, w, h, 90))   // 帧 −y
        assertEquals(RoiRect(380, 270, 60, 60), RoiLayout.referenceFor(sample, w, h, 180))  // 帧 −x
        assertEquals(RoiRect(470, 360, 60, 60), RoiLayout.referenceFor(sample, w, h, 270))  // 帧 +y
    }

    @Test fun customOffset_rotation0() {
        // 屏幕偏移 (−0.25, 0.1)：左 150、下 60 → 中心 (350, 360)
        assertEquals(RoiRect(320, 330, 60, 60), RoiLayout.referenceFor(sample, w, h, 0, -0.25, 0.1))
    }

    @Test fun customOffset_rotation90() {
        // 90°：屏幕 (x, y) → 帧 (y, −x) = (0.1, 0.25)：右 60、下 150 → 中心 (560, 450)
        assertEquals(RoiRect(530, 420, 60, 60), RoiLayout.referenceFor(sample, w, h, 90, -0.25, 0.1))
    }

    @Test fun customOffset_rotation270() {
        // 270°：屏幕 (x, y) → 帧 (−y, x) = (−0.1, −0.25)：左 60、上 150 → 中心 (440, 150)
        assertEquals(RoiRect(410, 120, 60, 60), RoiLayout.referenceFor(sample, w, h, 270, -0.25, 0.1))
    }

    @Test fun referencePositionIndependentOfSampleSize() {
        // 取色框调大后，参考白框中心不动（仍在 590），只是自身变大
        val big = RoiLayout.sampleFor(w, h, 0.2)
        val ref = RoiLayout.referenceFor(big, w, h, 0)
        assertEquals(590, ref.x + ref.w / 2)
        assertEquals(120, ref.w)
    }

    @Test fun farDetection() {
        assertEquals(false, RoiLayout.isFar(0.15, 0.0))
        assertEquals(false, RoiLayout.isFar(0.3, 0.3))   // ≈ 0.42
        assertEquals(true, RoiLayout.isFar(0.5, 0.2))    // ≈ 0.54
    }

    @Test fun referenceClampedInsideFrame() {
        val edge = RoiRect(940, 270, 60, 60)
        val ref = RoiLayout.referenceFor(edge, w, h, 0)
        assertEquals(w - ref.w, ref.x)
    }
}
