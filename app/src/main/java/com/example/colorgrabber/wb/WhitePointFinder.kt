package com.example.colorgrabber.wb

import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import kotlin.math.hypot
import kotlin.math.max

/**
 * 在整张图里找适合点白的区域：用正方形窗口滑动扫描，先排除
 * [WhitePointQuality] 判为过曝 / 偏暗 / 不均匀的窗口，再在剩下的里面
 * 选「亮、接近中性、均匀、靠近参考点」得分最高的一个。
 */
object WhitePointFinder {
    /** 窗口边长占图片短边的比例。 */
    const val WINDOW_FRACTION = 0.1
    /** 色度 (max-min)/max 超过该值视为明显有颜色，不作为候选（暖光下的白纸约 0.2～0.4）。 */
    const val MAX_CHROMA = 0.45
    /** 每个窗口每边大约采样的像素数。 */
    private const val SAMPLES_PER_SIDE = 24

    private const val CHROMA_WEIGHT = 0.5
    private const val STD_WEIGHT = 0.1
    private const val DISTANCE_WEIGHT = 0.3

    /**
     * @param near 参考点（位图像素坐标，通常是当前取色框中心，一般在试管附近）；
     *             补光往往不均匀，越靠近样品的白背景受光越一致。为 null 时不考虑距离。
     * @return 推荐的取色框；找不到合适区域时返回 null。
     */
    fun find(px: IntArray, width: Int, height: Int, near: Pair<Float, Float>? = null): RoiRect? {
        val size = max(8, (minOf(width, height) * WINDOW_FRACTION).toInt())
        if (size > width || size > height) return null
        val stride = max(1, size / 2)
        val sampleStep = max(1, size / SAMPLES_PER_SIDE)
        val diag = hypot(width.toDouble(), height.toDouble())

        var best: RoiRect? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var y = 0
        while (y + size <= height) {
            var x = 0
            while (x + size <= width) {
                val roi = RoiRect(x, y, size, size)
                val res = RoiSampler.sample(px, width, height, roi, sampleStep)
                if (WhitePointQuality.check(res).isEmpty()) {
                    val m = res.mean
                    val hi = maxOf(m.r, m.g, m.b).toDouble()
                    val chroma = (hi - minOf(m.r, m.g, m.b)) / hi
                    if (chroma <= MAX_CHROMA) {
                        var score = hi / 255.0 -
                            CHROMA_WEIGHT * chroma -
                            STD_WEIGHT * res.stdDev / WhitePointQuality.MAX_STD_DEV
                        if (near != null) {
                            val d = hypot(x + size / 2.0 - near.first, y + size / 2.0 - near.second)
                            score -= DISTANCE_WEIGHT * d / diag
                        }
                        if (score > bestScore) { bestScore = score; best = roi }
                    }
                }
                x += stride
            }
            y += stride
        }
        return best
    }
}
