package com.example.colorgrabber.camera

/** 取景页分析帧上的取色框布局。 */
object RoiLayout {
    /** 样品框：分析帧中心、边长为短边 20% 的正方形（与预览上居中的取色框对应）。 */
    fun sampleFor(w: Int, h: Int): RoiRect {
        val size = (minOf(w, h) * 0.2).toInt()
        return RoiRect((w - size) / 2, (h - size) / 2, size, size)
    }

    /**
     * 参考白框：在预览画面中位于样品框**右侧**，边长为样品框一半，中间留 1/4 边长的间隙，
     * 与布局里的 refBox（40dp，距样品框 20dp）对应。
     *
     * 分析帧是传感器方向，竖屏预览需要旋转 rotationDegrees 才是正的，
     * 所以「屏幕右侧」在分析帧里的方向要按旋转换算：
     * 0° → +x，90° → −y，180° → −x，270° → +y。
     */
    fun referenceFor(sample: RoiRect, w: Int, h: Int, rotationDegrees: Int): RoiRect {
        val (dx, dy) = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> 0 to -1
            180 -> -1 to 0
            270 -> 0 to 1
            else -> 1 to 0
        }
        val size = maxOf(1, sample.w / 2)
        // 两框中心距 = 样品半边 + 间隙 + 参考半边 = s/2 + s/4 + s/4 = s
        val cx = sample.x + sample.w / 2 + dx * sample.w
        val cy = sample.y + sample.h / 2 + dy * sample.h
        val x = (cx - size / 2).coerceIn(0, maxOf(0, w - size))
        val y = (cy - size / 2).coerceIn(0, maxOf(0, h - size))
        return RoiRect(x, y, size, size)
    }
}
