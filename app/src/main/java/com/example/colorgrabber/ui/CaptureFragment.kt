package com.example.colorgrabber.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorgrabber.R
import com.example.colorgrabber.camera.CameraController
import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import com.example.colorgrabber.color.ColorAnalyzer
import com.example.colorgrabber.color.Rgb
import com.example.colorgrabber.data.Measurement
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentCaptureBinding
import com.example.colorgrabber.wb.Gains
import com.example.colorgrabber.wb.WhiteBalanceEngine
import kotlinx.coroutines.launch

class CaptureFragment : Fragment() {
    private var _b: FragmentCaptureBinding? = null
    private val b get() = _b!!
    private lateinit var camera: CameraController
    private lateinit var repo: MeasurementRepository

    private var baseGains = Gains(1.0, 1.0, 1.0)   // 点白得到
    private var tempAdjust = 0.0
    private var rAdj = 1.0
    private var gAdj = 1.0
    private var bAdj = 1.0
    private var locked = false
    private var lastNormRgb: Rgb = Rgb(0, 0, 0)
    private var lastRawRgb: Rgb = Rgb(0, 0, 0)
    private var lastRoi = RoiRect(0, 0, 0, 0)
    private var referenceRgb: Rgb? = null
    private var frameCounter = 0
    @Volatile private var pendingPickWhite = false
    @Volatile private var pendingCapture = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) camera.start()
        else Toast.makeText(requireContext(), "需要相机权限，请到设置开启", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCaptureBinding.inflate(i, c, false); return b.root
    }

    /** 点白基础增益 → 叠加色温微调 → 叠加 RGB 每通道微调。 */
    private fun effectiveGains(): Gains {
        val withTemp = WhiteBalanceEngine.applyTempAdjust(baseGains, tempAdjust)
        return WhiteBalanceEngine.applyRgbAdjust(withTemp, rAdj, gAdj, bAdj)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = MeasurementRepository(requireContext().applicationContext)
        camera = CameraController(requireContext(), viewLifecycleOwner, b.previewView)
        camera.onFrame = ::onFrame

        b.tempSeek.setOnSeekBarChangeListener(simpleSeek { p -> tempAdjust = (p - 100) / 100.0; applyWb() })
        b.rSeek.setOnSeekBarChangeListener(simpleSeek { p -> rAdj = 0.5 + p / 200.0; applyWb() })
        b.gSeek.setOnSeekBarChangeListener(simpleSeek { p -> gAdj = 0.5 + p / 200.0; applyWb() })
        b.bSeek.setOnSeekBarChangeListener(simpleSeek { p -> bAdj = 0.5 + p / 200.0; applyWb() })

        b.btnPickWhite.setOnClickListener { pendingPickWhite = true }
        b.btnLock.setOnClickListener {
            locked = !locked
            if (locked) camera.lockExposureAndFocus() else camera.unlockExposureAndFocus()
            b.btnLock.text = if (locked) "已锁定" else "锁定"
        }
        b.btnSetRef.setOnClickListener {
            referenceRgb = lastNormRgb
            Toast.makeText(requireContext(), "已设为参比 I0", Toast.LENGTH_SHORT).show()
        }
        b.btnRecord.setOnClickListener { saveCurrent() }
        b.btnCapture.setOnClickListener { pendingCapture = true }
        b.btnHistory.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HistoryFragment())
                .addToBackStack(null).commit()
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) camera.start()
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun roiForFrame(w: Int, h: Int): RoiRect {
        val size = (minOf(w, h) * 0.2).toInt()
        return RoiRect((w - size) / 2, (h - size) / 2, size, size)
    }

    private fun onFrame(px: IntArray, w: Int, h: Int) {
        frameCounter++
        if (frameCounter % 6 != 0) return   // 节流：每 6 帧算一次
        // onFrame 在后台分析线程执行；视图/Activity 已销毁则直接退出，避免 requireActivity() 抛异常。
        val act = activity ?: return
        if (!isAdded) return
        val roi = roiForFrame(w, h)
        val res = RoiSampler.sample(px, w, h, roi)
        lastRawRgb = res.mean; lastRoi = roi

        if (pendingPickWhite) {
            baseGains = WhiteBalanceEngine.gainsFromWhite(res.mean)
            pendingPickWhite = false
            act.runOnUiThread { applyWb() }
        }
        if (pendingCapture) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            pendingCapture = false
            act.runOnUiThread { onFrameCaptured(bmp) }
        }

        val gains = effectiveGains()
        val norm = WhiteBalanceEngine.normalize(res.mean, gains)
        lastNormRgb = norm
        val hsv = ColorAnalyzer.toHsv(norm)
        val lab = ColorAnalyzer.toLab(norm)
        val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(norm, it) }
        val overTip = if (res.overexposedRatio > 0.2) "  ⚠过曝" else ""
        act.runOnUiThread {
            if (_b == null) return@runOnUiThread
            b.readout.text = buildString {
                append("RGB ${norm.r},${norm.g},${norm.b}$overTip\n")
                append("HSV ${"%.0f".format(hsv.h)},${"%.2f".format(hsv.s)},${"%.2f".format(hsv.v)}\n")
                append("Lab ${"%.1f".format(lab.l)},${"%.1f".format(lab.a)},${"%.1f".format(lab.b)}\n")
                append(if (abs != null)
                    "吸光度 R${"%.3f".format(abs.aR)} G${"%.3f".format(abs.aG)} B${"%.3f".format(abs.aB)}"
                else "吸光度：未标定（先设参比）")
            }
        }
    }

    private fun applyWb() {
        if (_b == null) return   // 可能从已 post 的后台回调进入，视图已销毁则跳过
        val gains = effectiveGains()
        val ok = camera.setManualWhiteBalance(gains)
        b.wbInfo.text = "白平衡：色温≈${WhiteBalanceEngine.displayKelvin(tempAdjust)}K  " +
            "增益 ${"%.2f".format(gains.r)}/${"%.2f".format(gains.g)}/${"%.2f".format(gains.b)}" +
            if (!ok) "（软件模式）" else ""
    }

    private fun onFrameCaptured(bmp: Bitmap) {
        lifecycleScope.launch {
            val uri = repo.saveImageToAlbum(bmp, "CG_${System.currentTimeMillis()}")
            if (uri == null) {
                Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, PickFragment.newInstance(uri.toString()))
                .addToBackStack(null).commit()
        }
    }

    private fun saveCurrent() {
        RecordDialog.show(requireContext()) { name, note, degTime ->
            val gains = effectiveGains()
            val hsv = ColorAnalyzer.toHsv(lastNormRgb)
            val lab = ColorAnalyzer.toLab(lastNormRgb)
            val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(lastNormRgb, it) }
            val m = Measurement(
                timestamp = System.currentTimeMillis(), sampleName = name, note = note,
                degradationTime = degTime, source = "live",
                roiX = lastRoi.x, roiY = lastRoi.y, roiW = lastRoi.w, roiH = lastRoi.h,
                rawR = lastRawRgb.r, rawG = lastRawRgb.g, rawB = lastRawRgb.b,
                normR = lastNormRgb.r, normG = lastNormRgb.g, normB = lastNormRgb.b,
                hsvH = hsv.h, hsvS = hsv.s, hsvV = hsv.v,
                labL = lab.l, labA = lab.a, labB = lab.b,
                absR = abs?.aR, absG = abs?.aG, absB = abs?.aB,
                gainR = gains.r, gainG = gains.g, gainB = gains.b, tempAdjust = tempAdjust
            )
            lifecycleScope.launch {
                repo.insert(m)
                Toast.makeText(requireContext(), "已记录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); camera.stop(); _b = null
    }
}
