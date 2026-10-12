package com.example.colorgrabber.camera

import kotlin.math.hypot
import kotlin.math.roundToInt

/** 取景页分析帧上的取色框布局。 */
object RoiLayout {
    /** 参考白框默认位置：样品框右侧，中心距 1 个样品框边长（与布局里的 refBox 对应）。 */
    const val DEFAULT_REF_OFFSET_X = 1.0
    const val DEFAULT_REF_OFFSET_Y = 0.0
    /** 参考白框中心离样品框中心超过该距离（单位：样品框边长）即提示可能受光照不均影响。 */
    const val FAR_DISTANCE = 3.0

    /** 样品框：分析帧中心、边长为短边 20% 的正方形（与预览上居中的取色框对应）。 */
    fun sampleFor(w: Int, h: Int): RoiRect {
        val size = (minOf(w, h) * 0.2).toInt()
        return RoiRect((w - size) / 2, (h - size) / 2, size, size)
    }

    /**
     * 参考白框：边长为样品框一半，中心位于样品框中心加上偏移量 (offsetX, offsetY) 处。
     * 偏移量以样品框边长为单位、按**屏幕方向**表示（x 向右、y 向下），
     * 默认 (1, 0) 即紧挨在样品框右侧，用户拖动参考白框时改变的就是这个偏移量。
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
        val size = maxOf(1, sample.w / 2)
        val cx = sample.x + sample.w / 2 + (bx * sample.w).roundToInt()
        val cy = sample.y + sample.h / 2 + (by * sample.h).roundToInt()
        val x = (cx - size / 2).coerceIn(0, maxOf(0, w - size))
        val y = (cy - size / 2).coerceIn(0, maxOf(0, h - size))
        return RoiRect(x, y, size, size)
    }

    /** 参考白框是否离样品较远（可能受补光不均、镜头暗角影响）。 */
    fun isFar(offsetX: Double, offsetY: Double): Boolean = hypot(offsetX, offsetY) > FAR_DISTANCE
}
