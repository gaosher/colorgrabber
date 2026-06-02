package com.example.colorgrabber.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class HistoryFragment : Fragment() {
    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private lateinit var repo: MeasurementRepository
    private lateinit var adapter: MeasurementAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = MeasurementRepository(requireContext().applicationContext)
        adapter = MeasurementAdapter(onDelete = { m ->
            lifecycleScope.launch { repo.delete(m, alsoDeleteImage = false) }
        })
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeAll().collectLatest { adapter.submit(it) }
        }
        b.btnExport.setOnClickListener { exportCsv() }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            val csv = repo.exportCsv()
            val dir = File(requireContext().cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "colorgrabber_export.csv")
            file.writeText(csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                requireContext(), requireContext().packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "导出 CSV"))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
