package com.example.colorgrabber.camera

import com.example.colorgrabber.color.Rgb
import kotlin.math.roundToInt

/**
 * 实时读数的时间平滑：取最近 [window] 次读数的平均，抵消小取色框带来的跳动。
 * 新读数与当前平均值的 RGB 距离超过 [jumpThreshold] 时（换了样品或画面明显变化）
 * 立即清空重来，避免平滑造成迟滞。
 */
class ReadingSmoother(private val window: Int = 8, private val jumpThreshold: Int = 33) {
    private val buf = ArrayDeque<Rgb>()

    fun add(c: Rgb): Rgb {
        val cur = current()
        if (cur != null && dist2(cur, c) > jumpThreshold * jumpThreshold) buf.clear()
        buf.addLast(c)
        while (buf.size > window) buf.removeFirst()
        return current()!!
    }

    fun current(): Rgb? {
        if (buf.isEmpty()) return null
        val n = buf.size.toDouble()
        return Rgb((buf.sumOf { it.r } / n).roundToInt(),
                   (buf.sumOf { it.g } / n).roundToInt(),
                   (buf.sumOf { it.b } / n).roundToInt())
    }

    fun reset() = buf.clear()

    private fun dist2(a: Rgb, b: Rgb): Int {
        val dr = a.r - b.r; val dg = a.g - b.g; val db = a.b - b.b
        return dr * dr + dg * dg + db * db
    }
}
