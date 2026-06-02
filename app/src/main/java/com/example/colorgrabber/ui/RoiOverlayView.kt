package com.example.colorgrabber.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** 在图片上叠加一个可拖动的取色方框。 */
class RoiOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var boxSizePx = 120f
    private var cx = -1f; private var cy = -1f
    private val paint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    /** 拖动回调：传出方框左上角与边长（view 坐标）。 */
    var onBoxMoved: ((left: Float, top: Float, size: Float) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        if (cx < 0) { cx = width / 2f; cy = height / 2f }
        val half = boxSizePx / 2
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                cx = event.x.coerceIn(boxSizePx / 2, width - boxSizePx / 2)
                cy = event.y.coerceIn(boxSizePx / 2, height - boxSizePx / 2)
                invalidate()
                onBoxMoved?.invoke(cx - boxSizePx / 2, cy - boxSizePx / 2, boxSizePx)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
