package com.example.colorgrabber.wb

import com.example.colorgrabber.camera.RoiResult
import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceWhiteTrackerTest {

    private val good = RoiResult(Rgb(220, 210, 200), 0.0, 4.0)
    private val dark = RoiResult(Rgb(100, 100, 100), 0.0, 4.0)

    @Test fun gainsOnlyAfterRequiredGoodFrames() {
        val t = ReferenceWhiteTracker(requiredGoodFrames = 3)
        assertNull(t.update(good))
        assertNull(t.update(good))
        assertEquals(ReferenceWhiteTracker.Status.Settling(2, 3), t.status)
        val g = t.update(good)
        assertNotNull(g)
        assertEquals(ReferenceWhiteTracker.Status.Ready, t.status)
        assertEquals(255.0 / 220.0, g!!.r, 1e-9)
    }

    @Test fun badFrameResetsCount() {
        val t = ReferenceWhiteTracker(requiredGoodFrames = 2)
        t.update(good)
        assertNull(t.update(dark))
        assertEquals(ReferenceWhiteTracker.Status.Bad(listOf(WhiteIssue.TOO_DARK)), t.status)
        assertNull(t.update(good))   // 重新从 1 开始累计
        assertNotNull(t.update(good))
    }

    @Test fun keepsReturningGainsWhileStable() {
        val t = ReferenceWhiteTracker(requiredGoodFrames = 1)
        assertNotNull(t.update(good))
        val brighter = RoiResult(Rgb(240, 230, 220), 0.0, 4.0)
        val g = t.update(brighter)
        assertEquals(255.0 / 240.0, g!!.r, 1e-9)   // 每帧按最新参考白重算
    }

    @Test fun resetGoesBackToSettling() {
        val t = ReferenceWhiteTracker(requiredGoodFrames = 1)
        t.update(good)
        t.reset()
        assertTrue(t.status is ReferenceWhiteTracker.Status.Settling)
    }
}
