package com.example.colorgrabber.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.colorgrabber.R
import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import com.example.colorgrabber.color.ColorAnalyzer
import com.example.colorgrabber.color.Rgb
import com.example.colorgrabber.data.Measurement
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentPickBinding
import com.example.colorgrabber.wb.Gains
import com.example.colorgrabber.wb.WbState
import com.example.colorgrabber.wb.WhiteBalanceEngine
import kotlinx.coroutines.launch

class PickFragment : Fragment(), MenuProvider {
    private var _b: FragmentPickBinding? = null
    private val b get() = _b!!
    private lateinit var repo: MeasurementRepository
    private var bitmap: Bitmap? = null
    private var imageUri: String? = null
    private var imported = false
    private var savedAlbumUri: String? = null
    private var curRoi = RoiRect(0, 0, 0, 0)   // 位图像素坐标

    private val wb = WbState()
    private var referenceRgb: Rgb? = null
    private var lastRawRgb: Rgb = Rgb(0, 0, 0)
    private var lastNormRgb: Rgb = Rgb(0, 0, 0)

    companion object {
        private const val ARG_URI = "uri"
        private const val ARG_IMPORTED = "imported"
        private const val ARG_ROI = "roi"          // [x,y,w,h]
        private const val ARG_GAIN = "gain"        // [r,g,b]

        /** 取景页拍照后进入：沿用取景页的取色框与白平衡增益。 */
        fun newInstance(uri: String, roi: RoiRect, gains: Gains) = create(
            uri, false, intArrayOf(roi.x, roi.y, roi.w, roi.h),
            doubleArrayOf(gains.r, gains.g, gains.b)
        )
        fun newImport(uri: String) = create(uri, true, null, null)

        /** 从历史记录重新打开（需有图片）：还原取色框与增益。 */
        fun newFromRecord(m: Measurement) = create(
            m.imageUri!!, m.source == "import",
            intArrayOf(m.roiX, m.roiY, m.roiW, m.roiH),
            doubleArrayOf(m.gainR, m.gainG, m.gainB)
        )

        private fun create(uri: String, imported: Boolean, roi: IntArray?, gain: DoubleArray?) =
            PickFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uri)
                    putBoolean(ARG_IMPORTED, imported)
                    if (roi != null) putIntArray(ARG_ROI, roi)
                    if (gain != null) putDoubleArray(ARG_GAIN, gain)
                }
            }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPickBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = "取色 · 测量"; setDisplayHomeAsUpEnabled(true)
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        repo = MeasurementRepository(requireContext().applicationContext)
        val args = requireArguments()
        imageUri = args.getString(ARG_URI)
        imported = args.getBoolean(ARG_IMPORTED, false)
        args.getDoubleArray(ARG_GAIN)?.let { wb.baseGains = Gains(it[0], it[1], it[2]) }

        bitmap = imageUri?.let { decodeSrgb(Uri.parse(it)) }
        if (bitmap == null) {
            Toast.makeText(requireContext(), "无法打开图片", Toast.LENGTH_SHORT).show()
        }
        b.viewer.setBitmap(bitmap)
        args.getIntArray(ARG_ROI)?.let { b.viewer.setInitialRoi(RoiRect(it[0], it[1], it[2], it[3])) }
        b.viewer.onRoiChanged = { r -> curRoi = r; showReadout() }

        b.wbHeader.setOnClickListener {
            val show = b.wbPanel.visibility != View.VISIBLE
            b.wbPanel.visibility = if (show) View.VISIBLE else View.GONE
            b.wbChevron.text = if (show) "▴" else "▾"
        }

        b.tempSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.tempAdjust = (p - 100) / 100.0
            b.tempVal.text = "${WhiteBalanceEngine.displayKelvin(wb.tempAdjust)}K"; showReadout()
        })
        b.rSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.rAdj = 0.5 + p / 200.0; b.rVal.text = "×%.2f".format(wb.rAdj); showReadout()
        })
        b.gSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.gAdj = 0.5 + p / 200.0; b.gVal.text = "×%.2f".format(wb.gAdj); showReadout()
        })
        b.bSeek.setOnSeekBarChangeListener(simpleSeek { p ->
            wb.bAdj = 0.5 + p / 200.0; b.bVal.text = "×%.2f".format(wb.bAdj); showReadout()
        })

        b.btnPickWhite.setOnClickListener {
            val res = sampleCurrent()
            if (res == null) Toast.makeText(requireContext(), "请先拖动方框到白背景", Toast.LENGTH_SHORT).show()
            else { wb.pickWhite(res.mean); Toast.makeText(requireContext(), "已点白校准", Toast.LENGTH_SHORT).show(); showReadout() }
        }
        b.btnSetRef.setOnClickListener {
            if (sampleCurrent() == null) return@setOnClickListener
            referenceRgb = lastNormRgb
            Toast.makeText(requireContext(), "已设为参比 I0", Toast.LENGTH_SHORT).show()
            showReadout()
        }
        b.btnRecordPick.setOnClickListener { recordPick() }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_pick, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { parentFragmentManager.popBackStack(); true }
        R.id.action_history -> {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HistoryFragment())
                .addToBackStack(null).commit()
            true
        }
        else -> false
    }

    /**
     * 显式按 sRGB 解码：带 Display P3 / Adobe RGB 等配置文件的图片统一转换到 sRGB，
     * 与 Lab 计算的 sRGB 假设一致；普通 sRGB 图片不做任何转换。
     */
    private fun decodeSrgb(uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
        }
        return requireContext().contentResolver.openInputStream(uri)?.use { s ->
            BitmapFactory.decodeStream(s, null, opts)
        }
    }

    private fun sampleCurrent() = bitmap?.let { bmp ->
        if (curRoi.w <= 0 || curRoi.h <= 0) return@let null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        RoiSampler.sample(px, bmp.width, bmp.height, curRoi)
    }

    private fun showReadout() {
        val res = sampleCurrent() ?: return
        lastRawRgb = res.mean
        val gains = wb.effectiveGains()
        val norm = WhiteBalanceEngine.normalize(res.mean, gains)
        lastNormRgb = norm
        b.viewer.setGains(gains.r, gains.g, gains.b)   // 整张照片随增益实时变色
        val hsv = ColorAnalyzer.toHsv(norm)
        val lab = ColorAnalyzer.toLab(norm)
        val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(norm, it) }
        val overTip = if (res.overexposedRatio > 0.2) "  ⚠过曝" else ""
        b.swatch.setSwatchColor(norm)
        b.rgbText.text = "RGB ${norm.r},${norm.g},${norm.b}$overTip"
        b.hexText.text = hexOf(norm)
        b.valHsv.text = "${"%.0f".format(hsv.h)} · ${"%.2f".format(hsv.s)} · ${"%.2f".format(hsv.v)}"
        b.valLab.text = "${"%.1f".format(lab.l)} · ${"%.1f".format(lab.a)} · ${"%.1f".format(lab.b)}"
        b.valAbs.text = if (abs != null)
            "R ${"%.3f".format(abs.aR)} · G ${"%.3f".format(abs.aG)} · B ${"%.3f".format(abs.aB)}"
        else "未标定（先设参比）"
        b.valWb.text = "色温≈${WhiteBalanceEngine.displayKelvin(wb.tempAdjust)}K · 增益 " +
            "${"%.2f".format(gains.r)}/${"%.2f".format(gains.g)}/${"%.2f".format(gains.b)}"
    }

    private fun recordPick() {
        val res = sampleCurrent()
        if (res == null) { Toast.makeText(requireContext(), "请先拖动方框选区", Toast.LENGTH_SHORT).show(); return }
        val gains = wb.effectiveGains()
        val norm = WhiteBalanceEngine.normalize(res.mean, gains)
        val bmp = bitmap
        RecordDialog.show(requireContext()) { name, note, degTime ->
            lifecycleScope.launch {
                // 外部导入的图：记录时存一份进相册，保证历史里的图不会失效
                val uriForRecord = when {
                    !imported -> imageUri
                    savedAlbumUri != null -> savedAlbumUri
                    bmp != null -> repo.saveImageToAlbum(bmp, "CG_import_${System.currentTimeMillis()}")
                        ?.toString()?.also { savedAlbumUri = it }
                    else -> null
                }
                val hsv = ColorAnalyzer.toHsv(norm); val lab = ColorAnalyzer.toLab(norm)
                val abs = referenceRgb?.let { ColorAnalyzer.toAbsorbance(norm, it) }
                val m = Measurement(
                    timestamp = System.currentTimeMillis(), sampleName = name, note = note,
                    degradationTime = degTime, source = if (imported) "import" else "pick",
                    imageUri = uriForRecord,
                    roiX = curRoi.x, roiY = curRoi.y, roiW = curRoi.w, roiH = curRoi.h,
                    rawR = res.mean.r, rawG = res.mean.g, rawB = res.mean.b,
                    normR = norm.r, normG = norm.g, normB = norm.b,
                    hsvH = hsv.h, hsvS = hsv.s, hsvV = hsv.v,
                    labL = lab.l, labA = lab.a, labB = lab.b,
                    absR = abs?.aR, absG = abs?.aG, absB = abs?.aB,
                    gainR = gains.r, gainG = gains.g, gainB = gains.b, tempAdjust = wb.tempAdjust
                )
                repo.insert(m)
                Toast.makeText(requireContext(), "已记录此点", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
