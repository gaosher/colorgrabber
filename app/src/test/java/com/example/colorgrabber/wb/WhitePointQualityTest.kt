package com.example.colorgrabber.wb

import com.example.colorgrabber.camera.RoiResult
import com.example.colorgrabber.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Test

class WhitePointQualityTest {

    private fun check(mean: Rgb, over: Double = 0.0, std: Double = 4.0) =
        WhitePointQuality.check(RoiResult(mean, over, std))

    @Test fun goodWhitePasses() {
        assertEquals(emptyList<WhiteIssue>(), check(Rgb(225, 215, 200)))
    }

    @Test fun overexposedFlagged() {
        assertEquals(listOf(WhiteIssue.OVEREXPOSED), check(Rgb(250, 248, 240), over = 0.3))
    }

    @Test fun fewClippedPixelsTolerated() {
        assertEquals(emptyList<WhiteIssue>(), check(Rgb(240, 235, 230), over = 0.005))
    }

    @Test fun darkFlagged() {
        assertEquals(listOf(WhiteIssue.TOO_DARK), check(Rgb(120, 110, 100)))
    }

    @Test fun warmWhiteNotDark() {
        // 暖光下蓝通道偏低，但最亮通道足够亮，不应判为偏暗
        assertEquals(emptyList<WhiteIssue>(), check(Rgb(210, 180, 120)))
    }

    @Test fun nonUniformFlagged() {
        assertEquals(listOf(WhiteIssue.NON_UNIFORM), check(Rgb(220, 210, 200), std = 30.0))
    }

    @Test fun multipleIssuesReported() {
        assertEquals(
            listOf(WhiteIssue.TOO_DARK, WhiteIssue.NON_UNIFORM),
            check(Rgb(100, 100, 100), std = 25.0)
        )
    }
}
