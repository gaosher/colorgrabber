package com.example.colorgrabber.camera

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 取景页分析帧上的取色框布局。
 *
 * 尺寸与位置都以**分析帧短边**为单位（0..1），与屏幕像素无关；
 * 预览上的框按同一比例换算（见 CaptureFragment 中的屏幕换算）。
 */
object RoiLayout {
    /** 取色框边长占短边的比例：可调范围与默认值。 */
    const val MIN_SAMPLE_FRACTION = 0.03
    const val MAX_SAMPLE_FRACTION = 0.20
    const val DEFAULT_SAMPLE_FRACTION = 0.10

    /** 参考白框默认位置：在样品框右侧，中心距为短边的 15%。 */
    const val DEFAULT_REF_OFFSET_X = 0.15
    const val DEFAULT_REF_OFFSET_Y = 0.0
    /** 参考白框中心离样品框中心超过短边的该比例即提示可能受光照不均影响。 */
    const val FAR_DISTANCE = 0.5

    fun clampFraction(f: Double): Double = f.coerceIn(MIN_SAMPLE_FRACTION, MAX_SAMPLE_FRACTION)

    /** 样品框：分析帧中心、边长为短边 fraction 的正方形（与预览上居中的取色框对应）。 */
    fun sampleFor(w: Int, h: Int, fraction: Double = DEFAULT_SAMPLE_FRACTION): RoiRect {
        val size = maxOf(1, (minOf(w, h) * clampFraction(fraction)).roundToInt())
        return RoiRect((w - size) / 2, (h - size) / 2, size, size)
    }

    /**
     * 参考白框：与样品框同样大小，中心位于样品框中心加上偏移 (offsetX, offsetY) 处。
     * 偏移以**短边**为单位、按**屏幕方向**表示（x 向右、y 向下）。
     * 偏移与样品框大小无关，因此调整取色框大小时参考白框不会跟着移动。
     *
     * 分析帧是传感器方向，竖屏预览需要顺时针旋转 rotationDegrees 才是正的，
     * 所以屏幕方向的偏移要按旋转换算到帧坐标：
     * 0° → (x, y)，90° → (y, −x)，180° → (−x, −y)，270° → (−y, x)。
     */
    fun referenceFor(
        sample: RoiRect, w: Int, h: Int, rotationDegrees: Int,
        offsetX: Double = DEFAULT_REF_OFFSET_X, offsetY: Double = DEFAULT_REF_OFFSET_Y
    ): RoiRect {
        val (bx, by) = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> offsetY to -offsetX
            180 -> -offsetX to -offsetY
            270 -> -offsetY to offsetX
            else -> offsetX to offsetY
        }
        val short = minOf(w, h)
        val size = sample.w
        val cx = sample.x + sample.w / 2 + (bx * short).roundToInt()
        val cy = sample.y + sample.h / 2 + (by * short).roundToInt()
        val x = (cx - size / 2).coerceIn(0, maxOf(0, w - size))
        val y = (cy - size / 2).coerceIn(0, maxOf(0, h - size))
        return RoiRect(x, y, size, size)
    }

    /** 参考白框是否离样品较远（可能受补光不均、镜头暗角影响）。 */
    fun isFar(offsetX: Double, offsetY: Double): Boolean = hypot(offsetX, offsetY) > FAR_DISTANCE
}
