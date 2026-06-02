package com.example.colorgrabber.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.colorgrabber.R
import com.example.colorgrabber.data.MeasurementRepository
import com.example.colorgrabber.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class HistoryFragment : Fragment(), MenuProvider {
    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private lateinit var repo: MeasurementRepository
    private lateinit var adapter: MeasurementAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = "历史记录"; setDisplayHomeAsUpEnabled(true)
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        repo = MeasurementRepository(requireContext().applicationContext)
        adapter = MeasurementAdapter(
            onDelete = { m ->
                lifecycleScope.launch { repo.delete(m, alsoDeleteImage = false) }
            },
            onOpen = { m ->
                if (m.imageUri.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "该记录是实时取色，没有保存图片", Toast.LENGTH_SHORT).show()
                } else {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.container, PickFragment.newFromRecord(m))
                        .addToBackStack(null).commit()
                }
            }
        )
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeAll().collectLatest { adapter.submit(it) }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_history, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { parentFragmentManager.popBackStack(); true }
        R.id.action_export -> { exportCsv(); true }
        else -> false
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
