package com.example.colorgrabber.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.colorgrabber.camera.RoiRect
import com.example.colorgrabber.camera.RoiSampler
import com.example.colorgrabber.color.ColorAnalyzer
import com.example.colorgrabber.data.Measurement
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentPickBinding
import kotlinx.coroutines.launch

class PickFragment : Fragment() {
    private var _b: FragmentPickBinding? = null
    private val b get() = _b!!
    private lateinit var repo: MeasurementRepository
    private var bitmap: Bitmap? = null
    private var imageUri: String? = null
    private var curRoi = RoiRect(0, 0, 0, 0)   // 位图像素坐标

    companion object {
        private const val ARG_URI = "uri"
        fun newInstance(uri: String) = PickFragment().apply {
            arguments = Bundle().apply { putString(ARG_URI, uri) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPickBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = MeasurementRepository(requireContext().applicationContext)
        imageUri = requireArguments().getString(ARG_URI)
        bitmap = imageUri?.let {
            requireContext().contentResolver.openInputStream(Uri.parse(it))?.use { s ->
                BitmapFactory.decodeStream(s)
            }
        }
        b.image.setImageBitmap(bitmap)
        b.overlay.onBoxMoved = { left, top, size -> onBoxMoved(left, top, size) }
        b.btnRecordPick.setOnClickListener { recordPick() }
    }

    /** overlay（全屏 view）坐标 → 位图像素坐标（ImageView 用 fitCenter）。 */
    private fun onBoxMoved(left: Float, top: Float, size: Float) {
        val bmp = bitmap ?: return
        val vw = b.image.width.toFloat(); val vh = b.image.height.toFloat()
        val scale = minOf(vw / bmp.width, vh / bmp.height)
        val dispW = bmp.width * scale; val dispH = bmp.height * scale
        val offX = (vw - dispW) / 2; val offY = (vh - dispH) / 2
        val bx = ((left - offX) / scale).toInt().coerceIn(0, bmp.width - 1)
        val by = ((top - offY) / scale).toInt().coerceIn(0, bmp.height - 1)
        val bs = (size / scale).toInt().coerceAtLeast(1)
        curRoi = RoiRect(bx, by, bs.coerceAtMost(bmp.width - bx), bs.coerceAtMost(bmp.height - by))
        showReadout()
    }

    private fun sampleCurrent() = bitmap?.let { bmp ->
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        RoiSampler.sample(px, bmp.width, bmp.height, curRoi)
    }

    private fun showReadout() {
        val res = sampleCurrent() ?: return
        val lab = ColorAnalyzer.toLab(res.mean)
        b.readout.text = "RGB ${res.mean.r},${res.mean.g},${res.mean.b}  " +
            "Lab ${"%.1f".format(lab.l)},${"%.1f".format(lab.a)},${"%.1f".format(lab.b)}"
    }

    private fun recordPick() {
        val res = sampleCurrent() ?: return
        RecordDialog.show(requireContext()) { name, note, degTime ->
            val hsv = ColorAnalyzer.toHsv(res.mean); val lab = ColorAnalyzer.toLab(res.mean)
            val m = Measurement(
                timestamp = System.currentTimeMillis(), sampleName = name, note = note,
                degradationTime = degTime, source = "pick", imageUri = imageUri,
                roiX = curRoi.x, roiY = curRoi.y, roiW = curRoi.w, roiH = curRoi.h,
                rawR = res.mean.r, rawG = res.mean.g, rawB = res.mean.b,
                normR = res.mean.r, normG = res.mean.g, normB = res.mean.b,
                hsvH = hsv.h, hsvS = hsv.s, hsvV = hsv.v,
                labL = lab.l, labA = lab.a, labB = lab.b,
                absR = null, absG = null, absB = null,
                gainR = 1.0, gainG = 1.0, gainB = 1.0, tempAdjust = 0.0
            )
            lifecycleScope.launch {
                repo.insert(m)
                Toast.makeText(requireContext(), "已记录此点", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
