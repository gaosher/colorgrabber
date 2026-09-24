package com.example.colorgrabber.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
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

    /**
     * 锁定 / 解锁曝光、自动白平衡与对焦。
     * 白平衡增益只在软件里施加（见 WhiteBalanceEngine），相机这边只负责把
     * AE/AWB 冻结住，保证点白之后的每一帧都在同一套曝光与颜色处理下取得。
     * 注意 setCaptureRequestOptions 会整体替换之前的选项，所以三者必须一起设置。
     * @param onSubmitted 选项提交到相机后在主线程回调。
     * @return 相机尚未就绪或设置失败时返回 false（此时不会回调）。
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun setLocked(lock: Boolean, onSubmitted: (() -> Unit)? = null): Boolean {
        val control = camera2Control ?: return false
        return runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lock)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lock)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                    if (lock) CaptureRequest.CONTROL_AF_MODE_OFF
                    else CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                .build()
            val future = control.setCaptureRequestOptions(opts)
            if (onSubmitted != null) {
                future.addListener({ onSubmitted() }, ContextCompat.getMainExecutor(context))
            }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        analysisExecutor.shutdown()
    }
}
