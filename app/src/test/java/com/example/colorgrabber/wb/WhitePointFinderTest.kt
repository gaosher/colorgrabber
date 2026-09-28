package com.example.colorgrabber.wb

import com.example.colorgrabber.camera.RoiRect
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitePointFinderTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** w×h 的图，先填底色，再按矩形覆盖。 */
    private fun image(w: Int, h: Int, bg: Int, vararg rects: Pair<RoiRect, Int>): IntArray {
        val px = IntArray(w * h) { bg }
        for ((r, c) in rects) for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) px[y * w + x] = c
        return px
    }

    private fun overlaps(a: RoiRect, b: RoiRect) =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

    private fun inside(a: RoiRect, b: RoiRect) =
        a.x >= b.x && a.y >= b.y && a.x + a.w <= b.x + b.w && a.y + a.h <= b.y + b.h

    @Test fun avoidsTubeClippedSpotAndShadow() {
        val w = 300; val h = 200
        val tube = RoiRect(130, 20, 40, 170)      // 粉色溶液
        val glare = RoiRect(140, 40, 10, 60)      // 管壁反光（过曝）
        val shadow = RoiRect(0, 150, 300, 50)     // 底部阴影
        val px = image(w, h, argb(225, 215, 200),
            tube to argb(200, 90, 120), glare to argb(255, 255, 255), shadow to argb(110, 105, 100))
        val roi = WhitePointFinder.find(px, w, h)
        assertNotNull(roi)
        assertTrue(!overlaps(roi!!, tube))
        assertTrue(!overlaps(roi, shadow))
    }

    @Test fun skipsOverexposedEvenIfBrighter() {
        val w = 200; val h = 100
        val clipped = RoiRect(0, 0, 100, 100)
        val px = image(w, h, argb(220, 220, 220), clipped to argb(255, 255, 255))
        val roi = WhitePointFinder.find(px, w, h)
        assertNotNull(roi)
        assertTrue(!overlaps(roi!!, clipped))
    }

    @Test fun prefersRegionNearReferencePoint() {
        // 左右两块同样的白背景，中间隔着深色；参考点在右侧
        val w = 300; val h = 100
        val divider = RoiRect(100, 0, 100, 100)
        val px = image(w, h, argb(225, 215, 200), divider to argb(40, 40, 40))
        val roi = WhitePointFinder.find(px, w, h, near = Pair(280f, 50f))
        assertNotNull(roi)
        assertTrue(inside(roi!!, RoiRect(200, 0, 100, 100)))
    }

    @Test fun returnsNullWhenNoWhiteArea() {
        val w = 200; val h = 100
        val px = image(w, h, argb(200, 60, 60))   // 整张都是红色
        assertNull(WhitePointFinder.find(px, w, h))
    }
}
