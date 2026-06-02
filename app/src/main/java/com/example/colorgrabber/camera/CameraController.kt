package com.example.colorgrabber.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.colorgrabber.wb.Gains
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var camera: androidx.camera.core.Camera? = null
    private var camera2Control: Camera2CameraControl? = null

    /** 每帧回调：传出 ARGB 像素与宽高。节流由调用方做。 */
    var onFrame: ((px: IntArray, width: Int, height: Int) -> Unit)? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy -> handleFrame(proxy) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
            camera2Control = camera?.cameraControl?.let { Camera2CameraControl.from(it) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun handleFrame(proxy: ImageProxy) {
        try {
            val plane = proxy.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val w = proxy.width; val h = proxy.height
            val px = IntArray(w * h)
            val rowPadding = rowStride - pixelStride * w
            var offset = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    offset += pixelStride
                }
                offset += rowPadding
            }
            onFrame?.invoke(px, w, h)
        } finally {
            proxy.close()
        }
    }

    /** 设置手动白平衡增益；不支持的机型返回 false。 */
    @SuppressLint("UnsafeOptInUsageError")
    fun setManualWhiteBalance(gains: Gains): Boolean {
        val control = camera2Control ?: return false
        return runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF)
                // FAST 模式下 HAL 接受 app 提供的 GAINS 并自算 transform；
                // 不能用 TRANSFORM_MATRIX（那要求同时提供 3x3 矩阵，否则增益会被忽略/失真）。
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_FAST)
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(
                        gains.r.toFloat(), gains.g.toFloat(), gains.g.toFloat(), gains.b.toFloat()))
                .build()
            control.setCaptureRequestOptions(opts)
            true
        }.getOrDefault(false)
    }

    /** 锁定曝光与对焦。 */
    @SuppressLint("UnsafeOptInUsageError")
    fun lockExposureAndFocus() {
        val control = camera2Control ?: return
        runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF)
                .build()
            control.setCaptureRequestOptions(opts)
        }
    }

    /** 解锁曝光与对焦，恢复连续自动。 */
    @SuppressLint("UnsafeOptInUsageError")
    fun unlockExposureAndFocus() {
        val control = camera2Control ?: return
        runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                .build()
            control.setCaptureRequestOptions(opts)
        }
    }

    fun stop() {
        analysisExecutor.shutdown()
    }
}
