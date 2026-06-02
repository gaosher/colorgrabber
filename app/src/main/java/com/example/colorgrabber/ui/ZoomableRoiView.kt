package com.example.colorgrabber.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.colorgrabber.camera.RoiRect
import kotlin.math.hypot
import kotlin.math.min

/**
 * 自绘的可缩放/平移图片视图，叠加一个可拖动、可拉角缩放的取色框。
 * 取色框以「位图像素坐标」存储，随图片一起缩放平移。
 * 手势：双指捏合缩放图片；单指——按在角把手上→拉伸取色框，按在框内→移动取色框，按在框外→平移图片。
 */
class ZoomableRoiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val imgMatrix = Matrix()
    private val inverse = Matrix()
    private val roi = RectF()          // 位图像素坐标
    private var initialized = false
    private var pendingRoi: RoiRect? = null   // 外部指定的初始取色框（如从历史还原）

    private val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B"); style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B"); style = Paint.Style.FILL
    }

    /** 取色框变化回调，传出位图像素坐标的矩形。 */
    var onRoiChanged: ((RoiRect) -> Unit)? = null

    private val handleTouchPx = 48f   // 角把手命中半径（屏幕像素）
    private val handleDrawR = 14f

    fun setBitmap(bmp: Bitmap?) {
        bitmap = bmp; initialized = false
        if (bmp != null && width > 0 && height > 0) fitAndCenter()
        invalidate()
    }

    /** 指定初始取色框（位图像素坐标），用于从历史记录还原。需在 setBitmap 前后均可调用。 */
    fun setInitialRoi(r: RoiRect) {
        pendingRoi = r
        if (initialized) { applyRoiClamped(r); invalidate(); emit() }
    }

    /** 用三通道增益给整张显示图像着色，使画面与取样读数一致。 */
    fun setGains(gr: Double, gg: Double, gb: Double) {
        imgPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            gr.toFloat(), 0f, 0f, 0f, 0f,
            0f, gg.toFloat(), 0f, 0f, 0f,
            0f, 0f, gb.toFloat(), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        invalidate()
    }

    private fun applyRoiClamped(r: RoiRect) {
        val bmp = bitmap ?: return
        val l = r.x.toFloat().coerceIn(0f, bmp.width - 1f)
        val t = r.y.toFloat().coerceIn(0f, bmp.height - 1f)
        roi.set(l, t,
            (l + r.w).coerceAtMost(bmp.width.toFloat()),
            (t + r.h).coerceAtMost(bmp.height.toFloat()))
    }

    private fun fitAndCenter() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val dx = (width - bmp.width * scale) / 2f
        val dy = (height - bmp.height * scale) / 2f
        imgMatrix.reset()
        imgMatrix.postScale(scale, scale)
        imgMatrix.postTranslate(dx, dy)
        val pr = pendingRoi
        if (pr != null) {
            applyRoiClamped(pr)
        } else {
            val s = min(bmp.width, bmp.height) * 0.2f
            roi.set((bmp.width - s) / 2f, (bmp.height - s) / 2f, (bmp.width + s) / 2f, (bmp.height + s) / 2f)
        }
        initialized = true
        emit()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (!initialized) fitAndCenter()
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                imgMatrix.postScale(d.scaleFactor, d.scaleFactor, d.focusX, d.focusY)
                invalidate(); emit(); return true
            }
        })

    private enum class Mode { NONE, PAN, MOVE_BOX, RESIZE }
    private var mode = Mode.NONE
    private var resizeCornerX = 0     // -1 左 / +1 右
    private var resizeCornerY = 0     // -1 上 / +1 下
    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) { mode = Mode.NONE; return true }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                mode = pickMode(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) return true
                val dx = event.x - lastX; val dy = event.y - lastY
                lastX = event.x; lastY = event.y
                when (mode) {
                    Mode.PAN -> { imgMatrix.postTranslate(dx, dy); invalidate() }
                    Mode.MOVE_BOX -> moveBox(dx, dy)
                    Mode.RESIZE -> resizeBox(event.x, event.y)
                    Mode.NONE -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = Mode.NONE
        }
        return true
    }

    private fun screenToBitmap(x: Float, y: Float): FloatArray {
        imgMatrix.invert(inverse)
        val pts = floatArrayOf(x, y)
        inverse.mapPoints(pts)
        return pts
    }

    private fun bitmapRectToScreen(): RectF {
        val r = RectF(roi); imgMatrix.mapRect(r); return r
    }

    private fun pickMode(x: Float, y: Float): Mode {
        val sr = bitmapRectToScreen()
        val corners = arrayOf(
            Triple(sr.left, sr.top, -1 to -1),
            Triple(sr.right, sr.top, 1 to -1),
            Triple(sr.left, sr.bottom, -1 to 1),
            Triple(sr.right, sr.bottom, 1 to 1))
        for ((cx, cy, c) in corners) {
            if (hypot((x - cx).toDouble(), (y - cy).toDouble()) <= handleTouchPx) {
                resizeCornerX = c.first; resizeCornerY = c.second; return Mode.RESIZE
            }
        }
        return if (sr.contains(x, y)) Mode.MOVE_BOX else Mode.PAN
    }

    private fun currentScale(): Float {
        val v = FloatArray(9); imgMatrix.getValues(v); return v[Matrix.MSCALE_X]
    }

    private fun moveBox(dx: Float, dy: Float) {
        val bmp = bitmap ?: return
        val s = currentScale()
        val nl = (roi.left + dx / s).coerceIn(0f, bmp.width - roi.width())
        val nt = (roi.top + dy / s).coerceIn(0f, bmp.height - roi.height())
        roi.offsetTo(nl, nt)
        invalidate(); emit()
    }

    private fun resizeBox(sx: Float, sy: Float) {
        val bmp = bitmap ?: return
        val p = screenToBitmap(sx, sy)
        val minSize = 8f
        if (resizeCornerX < 0) roi.left = p[0].coerceIn(0f, roi.right - minSize)
        else roi.right = p[0].coerceIn(roi.left + minSize, bmp.width.toFloat())
        if (resizeCornerY < 0) roi.top = p[1].coerceIn(0f, roi.bottom - minSize)
        else roi.bottom = p[1].coerceIn(roi.top + minSize, bmp.height.toFloat())
        invalidate(); emit()
    }

    private fun emit() {
        val bmp = bitmap ?: return
        val x = roi.left.toInt().coerceIn(0, bmp.width - 1)
        val y = roi.top.toInt().coerceIn(0, bmp.height - 1)
        val w = roi.width().toInt().coerceAtLeast(1).coerceAtMost(bmp.width - x)
        val h = roi.height().toInt().coerceAtLeast(1).coerceAtMost(bmp.height - y)
        onRoiChanged?.invoke(RoiRect(x, y, w, h))
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, imgMatrix, imgPaint)
        val sr = bitmapRectToScreen()
        canvas.drawRect(sr, boxPaint)
        canvas.drawCircle(sr.left, sr.top, handleDrawR, handlePaint)
        canvas.drawCircle(sr.right, sr.top, handleDrawR, handlePaint)
        canvas.drawCircle(sr.left, sr.bottom, handleDrawR, handlePaint)
        canvas.drawCircle(sr.right, sr.bottom, handleDrawR, handlePaint)
    }
}
