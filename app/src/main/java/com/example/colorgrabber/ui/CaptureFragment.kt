package com.example.colorgrabber.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
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
import com.example.colorgrabber.camera.ReadingSmoother
import com.example.colorgrabber.camera.RoiLayout
import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import com.example.colorgrabber.color.ColorAnalyzer
import com.example.colorgrabber.color.Rgb
import com.example.colorgrabber.data.Measurement
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentCaptureBinding
import com.example.colorgrabber.wb.ReferenceWhiteTracker
import com.example.colorgrabber.wb.WbState
import com.example.colorgrabber.wb.WhiteIssue
import com.example.colorgrabber.wb.WhitePointQuality
import com.example.colorgrabber.wb.WhiteBalanceEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    @Volatile private var frameCounter = 0
    /** 到达该帧号后取当前取色框为白色（-1 表示无待办）；留出几帧让锁定生效。 */
    @Volatile private var pickWhiteAtFrame = -1
    @Volatile private var pendingCapture = false
    /** 参考白模式：每帧用样品框旁的参考白框实时重算增益。 */
    @Volatile private var refMode = false
    private val refTracker = ReferenceWhiteTracker()
    /** 参考白框中心相对样品框中心的偏移（单位：分析帧短边，屏幕方向），用户可拖动。 */
    @Volatile private var refOffsetX = RoiLayout.DEFAULT_REF_OFFSET_X
    @Volatile private var refOffsetY = RoiLayout.DEFAULT_REF_OFFSET_Y
    /** 取色框边长占分析帧短边的比例，用户可调。 */
    @Volatile private var sampleFraction = RoiLayout.DEFAULT_SAMPLE_FRACTION
    /** 最近一帧的尺寸与旋转，用于把帧上的比例换算成预览上的像素。 */
    @Volatile private var frameW = 0
    @Volatile private var frameH = 0
    @Volatile private var frameRot = 0
    private val sampleSmoother = ReadingSmoother()
    private val refSmoother = ReadingSmoother()

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

        // 视图重建（如从测量页返回）时相机会重新启动，之前的锁定已失效
        if (locked) {
            locked = false
            Toast.makeText(requireContext(), "相机已重新启动，锁定已解除，请重新点白", Toast.LENGTH_LONG).show()
        }

        refTracker.reset()
        resetSmoothing()
        loadPrefs()
        setupSampleSizeSeek()
        setupRefBoxDrag()
        applyRefModeUi()
        b.previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutBoxes() }

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
            b.tempVal.text = "${WhiteBalanceEngine.displayKelvin(wb.tempAdjust)}K"; updateWbText()
        })
        b.rSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.rAdj = 0.5 + p / 200.0; b.rVal.text = "×%.2f".format(wb.rAdj); updateWbText()
        })
        b.gSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.gAdj = 0.5 + p / 200.0; b.gVal.text = "×%.2f".format(wb.gAdj); updateWbText()
        })
        b.bSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.bAdj = 0.5 + p / 200.0; b.bVal.text = "×%.2f".format(wb.bAdj); updateWbText()
        })

        b.btnPickWhite.setOnClickListener { pickWhite() }
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
        menu.findItem(R.id.action_ref_white)?.isChecked = refMode
        menu.findItem(R.id.action_ref_reset)?.isVisible = refMode
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_lock -> {
            setLocked(!locked)
            Toast.makeText(requireContext(),
                if (locked) "已锁定曝光/白平衡/对焦" else "已解锁，光线或构图变化后请重新点白",
                Toast.LENGTH_SHORT).show()
            true
        }
        R.id.action_ref_white -> {
            refMode = !refMode
            refTracker.reset()
            resetSmoothing()
            pickWhiteAtFrame = -1
            applyRefModeUi()
            requireActivity().invalidateMenu()
            Toast.makeText(requireContext(),
                if (refMode) "参考白模式：把右侧小框对准白背景，每帧自动校准，无需点白或锁定"
                else "已退出参考白模式，保留当前增益",
                Toast.LENGTH_LONG).show()
            true
        }
        R.id.action_ref_reset -> {
            setRefOffset(RoiLayout.DEFAULT_REF_OFFSET_X, RoiLayout.DEFAULT_REF_OFFSET_Y)
            savePrefs()
            Toast.makeText(requireContext(), "参考白框已恢复默认位置", Toast.LENGTH_SHORT).show()
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

    private fun setLocked(lock: Boolean) {
        locked = lock
        camera.setLocked(lock)
        requireActivity().invalidateMenu()
    }

    /**
     * 点白：先锁定曝光/白平衡/对焦，等锁定生效后再取白。
     * 否则移到样品上时自动曝光会重新调整亮度，点白得到的增益就不再适用。
     */
    private fun pickWhite() {
        locked = true
        requireActivity().invalidateMenu()
        val submitted = camera.setLocked(true) {
            pickWhiteAtFrame = frameCounter + LOCK_SETTLE_FRAMES
        }
        if (!submitted) pickWhiteAtFrame = frameCounter
    }

    /** 参考白模式下显示参考白框、停用「点白校准」。 */
    private fun applyRefModeUi() {
        b.refBox.visibility = if (refMode) View.VISIBLE else View.GONE
        b.btnPickWhite.isEnabled = !refMode
        if (!refMode) updateWbText()
    }

    /** 从偏好里读出取色框大小与参考白框位置。 */
    private fun loadPrefs() {
        val p = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sampleFraction = RoiLayout.clampFraction(
            p.getFloat(KEY_SAMPLE_FRACTION, RoiLayout.DEFAULT_SAMPLE_FRACTION.toFloat()).toDouble())
        refOffsetX = p.getFloat(KEY_REF_X, RoiLayout.DEFAULT_REF_OFFSET_X.toFloat()).toDouble()
        refOffsetY = p.getFloat(KEY_REF_Y, RoiLayout.DEFAULT_REF_OFFSET_Y.toFloat()).toDouble()
    }

    private fun savePrefs() {
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_SAMPLE_FRACTION, sampleFraction.toFloat())
            .putFloat(KEY_REF_X, refOffsetX.toFloat())
            .putFloat(KEY_REF_Y, refOffsetY.toFloat())
            .apply()
    }

    private fun resetSmoothing() {
        sampleSmoother.reset(); refSmoother.reset()
    }

    /** 取色框大小滑块：3%～20%（分析帧短边），松手时保存。 */
    private fun setupSampleSizeSeek() {
        val toProgress = { f: Double -> (f * 100).roundToInt() - (RoiLayout.MIN_SAMPLE_FRACTION * 100).roundToInt() }
        b.sizeSeek.max = toProgress(RoiLayout.MAX_SAMPLE_FRACTION)
        b.sizeSeek.progress = toProgress(sampleFraction)
        b.sizeVal.text = "${(sampleFraction * 100).roundToInt()}%"
        b.sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                sampleFraction = RoiLayout.clampFraction(RoiLayout.MIN_SAMPLE_FRACTION + p / 100.0)
                b.sizeVal.text = "${(sampleFraction * 100).roundToInt()}%"
                refTracker.reset()
                resetSmoothing()
                layoutBoxes()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { savePrefs() }
        })
    }

    /** 更新偏移并移动参考白框；位置变了，参考白需要重新确认。 */
    private fun setRefOffset(dx: Double, dy: Double) {
        refOffsetX = dx; refOffsetY = dy
        refTracker.reset()
        refSmoother.reset()
        layoutBoxes()
    }

    /**
     * 分析帧短边在预览上对应的像素数。PreviewView 默认 FILL_CENTER（铺满并居中裁剪），
     * 缩放比例取宽、高两个方向中较大者；尚未收到帧时先按预览宽度近似。
     */
    private fun shortSidePx(): Float {
        val vw = b.previewView.width.toFloat(); val vh = b.previewView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return 0f
        val fw = frameW; val fh = frameH
        if (fw <= 0 || fh <= 0) return vw
        val rotated = frameRot % 180 != 0
        val uprightW = if (rotated) fh else fw
        val uprightH = if (rotated) fw else fh
        val scale = maxOf(vw / uprightW, vh / uprightH)
        return minOf(fw, fh) * scale
    }

    /** 按当前比例摆放取色框与参考白框（两者在布局里都居中于预览，参考白用 translation 平移）。 */
    private fun layoutBoxes() {
        if (_b == null) return
        val k = shortSidePx()
        if (k <= 0f) return
        val size = maxOf(1, (sampleFraction * k).roundToInt())
        for (v in listOf(b.roiBox, b.refBox)) {
            val lp = v.layoutParams
            if (lp.width != size || lp.height != size) {
                lp.width = size; lp.height = size; v.layoutParams = lp
            }
        }
        b.refBox.translationX = (refOffsetX * k).toFloat()
        b.refBox.translationY = (refOffsetY * k).toFloat()
    }

    /** 参考白框可拖动，范围限制在预览区域内；松手时保存位置。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupRefBoxDrag() {
        var downX = 0f; var downY = 0f; var startTx = 0f; var startTy = 0f
        b.refBox.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startTx = v.translationX; startTy = v.translationY
                }
                MotionEvent.ACTION_MOVE -> {
                    // refBox 在布局里居中于预览，translation 的允许范围是预览与框的尺寸差的一半
                    val pv = b.previewView
                    val maxTx = (pv.width - v.width) / 2f
                    val maxTy = (pv.height - v.height) / 2f
                    val tx = (startTx + e.rawX - downX).coerceIn(-maxTx, maxTx)
                    val ty = (startTy + e.rawY - downY).coerceIn(-maxTy, maxTy)
                    val k = shortSidePx().toDouble()
                    if (k > 0) setRefOffset(tx / k, ty / k)
                }
                MotionEvent.ACTION_UP -> {
                    savePrefs()
                    if (RoiLayout.isFar(refOffsetX, refOffsetY)) {
                        Toast.makeText(requireContext(),
                            "参考白框离样品较远，可能受补光不均、画面边缘变暗影响，建议放在试管旁边",
                            Toast.LENGTH_LONG).show()
                    }
                    v.performClick()
                }
            }
            true
        }
    }

    private fun onFrame(px: IntArray, w: Int, h: Int, rotation: Int) {
        frameCounter++
        if (frameCounter % 6 != 0) return   // 节流：每 6 帧算一次
        val act = activity ?: return
        if (!isAdded) return
        if (w != frameW || h != frameH || rotation != frameRot) {
            frameW = w; frameH = h; frameRot = rotation
            act.runOnUiThread { layoutBoxes() }
        }
        val roi = RoiLayout.sampleFor(w, h, sampleFraction)
        val res = RoiSampler.sample(px, w, h, roi, rejectSigma = RoiSampler.DEFAULT_REJECT_SIGMA)
        // 显示与记录用平滑后的读数；点白、质量检查仍用本帧结果
        val raw = sampleSmoother.add(res.mean)
        lastRawRgb = raw; lastRoi = roi

        if (refMode) {
            val dx = refOffsetX; val dy = refOffsetY
            val refRoi = RoiLayout.referenceFor(roi, w, h, rotation, dx, dy)
            val ref = RoiSampler.sample(px, w, h, refRoi, rejectSigma = RoiSampler.DEFAULT_REJECT_SIGMA)
            val refSmoothed = ref.copy(mean = refSmoother.add(ref.mean))
            refTracker.update(refSmoothed)?.let { wb.baseGains = it }
            val status = refStatusText(refTracker.status) +
                if (RoiLayout.isFar(dx, dy)) "（离样品较远）" else ""
            act.runOnUiThread {
                if (_b == null || !refMode) return@runOnUiThread
                val g = wb.effectiveGains()
                b.valWb.text = "$status · 增益 " +
                    "${"%.2f".format(g.r)}/${"%.2f".format(g.g)}/${"%.2f".format(g.b)}"
            }
        }

        val pickAt = pickWhiteAtFrame
        if (pickAt in 0..frameCounter) {
            wb.pickWhite(res.mean)
            pickWhiteAtFrame = -1
            val issues = WhitePointQuality.check(res)
            act.runOnUiThread {
                if (_b == null) return@runOnUiThread
                updateWbText()
                b.root.showWhiteCheck(issues, "已点白校准，并锁定曝光/白平衡")
            }
        }
        if (pendingCapture) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            pendingCapture = false
            act.runOnUiThread { onFrameCaptured(bmp) }
        }

        val gains = wb.effectiveGains()
        val norm = WhiteBalanceEngine.normalize(raw, gains)
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

    private fun refStatusText(s: ReferenceWhiteTracker.Status): String = when (s) {
        is ReferenceWhiteTracker.Status.Ready -> "参考白 ✓"
        is ReferenceWhiteTracker.Status.Settling -> "参考白稳定中 ${s.good}/${s.required}"
        is ReferenceWhiteTracker.Status.Bad -> "参考白⚠" + s.issues.joinToString("、") {
            when (it) {
                WhiteIssue.OVEREXPOSED -> "过曝"
                WhiteIssue.TOO_DARK -> "偏暗"
                WhiteIssue.NON_UNIFORM -> "不均匀"
            }
        }
    }

    /** 白平衡增益只在软件里施加（onFrame 中 normalize），这里仅刷新显示。 */
    private fun updateWbText() {
        if (_b == null) return
        val gains = wb.effectiveGains()
        b.valWb.text = "色温≈${WhiteBalanceEngine.displayKelvin(wb.tempAdjust)}K · 增益 " +
            "${"%.2f".format(gains.r)}/${"%.2f".format(gains.g)}/${"%.2f".format(gains.b)}"
    }

    private fun onFrameCaptured(bmp: Bitmap) {
        // 把取景页的白平衡和取色框带到测量页，读数与拍摄时保持一致
        val gains = wb.effectiveGains()
        val roi = RoiLayout.sampleFor(bmp.width, bmp.height, sampleFraction)
        lifecycleScope.launch {
            val uri = repo.saveImageToAlbum(bmp, "CG_${System.currentTimeMillis()}")
            if (uri == null) {
                Toast.makeText(requireContext(), "保存图片失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, PickFragment.newInstance(uri.toString(), roi, gains))
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

    companion object {
        /** 锁定请求提交后再等的帧数，确保取白用的帧已处于锁定状态。 */
        private const val LOCK_SETTLE_FRAMES = 8
        private const val PREFS = "capture"
        // 1.5 起偏移改以分析帧短边为单位，换用新键名，旧版保存的位置不再沿用
        private const val KEY_REF_X = "ref_pos_x"
        private const val KEY_REF_Y = "ref_pos_y"
        private const val KEY_SAMPLE_FRACTION = "sample_fraction"
    }

    override fun onDestroyView() {
        super.onDestroyView(); camera.stop(); _b = null
    }
}
