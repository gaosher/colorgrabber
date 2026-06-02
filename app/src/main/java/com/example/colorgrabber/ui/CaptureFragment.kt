package com.example.colorgrabber.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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
import com.example.colorgrabber.wb.WbState
import com.example.colorgrabber.wb.WhiteBalanceEngine
import kotlinx.coroutines.launch

class CaptureFragment : Fragment(), MenuProvider {
    private var _b: FragmentCaptureBinding? = null
    private val b get() = _b!!
    private lateinit var camera: CameraController
    private lateinit var repo: MeasurementRepository

    private val wb = WbState()
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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, PickFragment.newImport(uri.toString()))
                .addToBackStack(null).commit()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCaptureBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = "取色 · 取景"; setDisplayHomeAsUpEnabled(false)
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        repo = MeasurementRepository(requireContext().applicationContext)
        camera = CameraController(requireContext(), viewLifecycleOwner, b.previewView)
        camera.onFrame = ::onFrame

        b.wbHeader.setOnClickListener {
            val show = b.wbPanel.visibility != View.VISIBLE
            b.wbPanel.visibility = if (show) View.VISIBLE else View.GONE
            b.wbChevron.text = if (show) "▴" else "▾"
        }

        b.tempSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.tempAdjust = (p - 100) / 100.0
            b.tempVal.text = "${WhiteBalanceEngine.displayKelvin(wb.tempAdjust)}K"; applyWb()
        })
        b.rSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.rAdj = 0.5 + p / 200.0; b.rVal.text = "×%.2f".format(wb.rAdj); applyWb()
        })
        b.gSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.gAdj = 0.5 + p / 200.0; b.gVal.text = "×%.2f".format(wb.gAdj); applyWb()
        })
        b.bSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.bAdj = 0.5 + p / 200.0; b.bVal.text = "×%.2f".format(wb.bAdj); applyWb()
        })

        b.btnPickWhite.setOnClickListener { pendingPickWhite = true }
        b.btnSetRef.setOnClickListener {
            referenceRgb = lastNormRgb
            Toast.makeText(requireContext(), "已设为参比 I0", Toast.LENGTH_SHORT).show()
        }
        b.btnRecord.setOnClickListener { saveCurrent() }
        b.btnCapture.setOnClickListener { pendingCapture = true }
        b.btnImport.setOnClickListener { pickImageLauncher.launch("image/*") }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) camera.start()
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_capture, menu)
        menu.findItem(R.id.action_lock)?.isChecked = locked
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_lock -> {
            locked = !locked
            item.isChecked = locked
            if (locked) camera.lockExposureAndFocus() else camera.unlockExposureAndFocus()
            Toast.makeText(requireContext(), if (locked) "已锁定曝光对焦" else "已解锁", Toast.LENGTH_SHORT).show()
            true
        }
        R.id.action_history -> {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HistoryFragment())
                .addToBackStack(null).commit()
            true
        }
        else -> false
    }

    private fun roiForFrame(w: Int, h: Int): RoiRect {
        val size = (minOf(w, h) * 0.2).toInt()
        return RoiRect((w - size) / 2, (h - size) / 2, size, size)
    }

    private fun onFrame(px: IntArray, w: Int, h: Int) {
        frameCounter++
        if (frameCounter % 6 != 0) return   // 节流：每 6 帧算一次
        val act = activity ?: return
        if (!isAdded) return
        val roi = roiForFrame(w, h)
        val res = RoiSampler.sample(px, w, h, roi)
        lastRawRgb = res.mean; lastRoi = roi

        if (pendingPickWhite) {
            wb.pickWhite(res.mean)
            pendingPickWhite = false
            act.runOnUiThread { applyWb() }
        }
        if (pendingCapture) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            pendingCapture = false
            act.runOnUiThread { onFrameCaptured(bmp) }
        }

        val gains = wb.effectiveGains()
        val norm = WhiteBalanceEngine.normalize(res.mean, gains)
        lastNormRgb = norm
        val hsv = ColorAnalyzer.toHsv(norm)
        val lab = ColorAnalyzer.toLab(norm)
        val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(norm, it) }
        val overTip = if (res.overexposedRatio > 0.2) "  ⚠过曝" else ""
        act.runOnUiThread {
            if (_b == null) return@runOnUiThread
            b.swatch.setSwatchColor(norm)
            b.rgbText.text = "RGB ${norm.r},${norm.g},${norm.b}$overTip"
            b.hexText.text = hexOf(norm)
            b.valHsv.text = "${"%.0f".format(hsv.h)} · ${"%.2f".format(hsv.s)} · ${"%.2f".format(hsv.v)}"
            b.valLab.text = "${"%.1f".format(lab.l)} · ${"%.1f".format(lab.a)} · ${"%.1f".format(lab.b)}"
            b.valAbs.text = if (abs != null)
                "R ${"%.3f".format(abs.aR)} · G ${"%.3f".format(abs.aG)} · B ${"%.3f".format(abs.aB)}"
            else "未标定（先设参比）"
        }
    }

    private fun applyWb() {
        if (_b == null) return
        val gains = wb.effectiveGains()
        val ok = camera.setManualWhiteBalance(gains)
        b.valWb.text = "色温≈${WhiteBalanceEngine.displayKelvin(wb.tempAdjust)}K · 增益 " +
            "${"%.2f".format(gains.r)}/${"%.2f".format(gains.g)}/${"%.2f".format(gains.b)}" +
            if (!ok) "（软件）" else ""
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
            val gains = wb.effectiveGains()
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
                gainR = gains.r, gainG = gains.g, gainB = gains.b, tempAdjust = wb.tempAdjust
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
