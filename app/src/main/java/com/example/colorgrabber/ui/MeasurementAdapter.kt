package com.example.colorgrabber.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.colorgrabber.R
import com.example.colorgrabber.data.Measurement

class MeasurementAdapter(
    private val onDelete: (Measurement) -> Unit,
    private val onOpen: (Measurement) -> Unit
) : RecyclerView.Adapter<MeasurementAdapter.VH>() {
    private val items = mutableListOf<Measurement>()

    fun submit(list: List<Measurement>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.title)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
        val btnDelete: Button = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_measurement, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.title.text = m.sampleName.ifEmpty { "样品#${m.id}" }
        h.subtitle.text = "RGB ${m.normR},${m.normG},${m.normB}  " +
            (if (m.absB != null) "A_B ${"%.3f".format(m.absB)}" else "未标定")
        if (m.imageUri != null) h.thumb.setImageURI(Uri.parse(m.imageUri))
        else h.thumb.setImageDrawable(null)
        h.btnDelete.setOnClickListener { onDelete(m) }
        h.itemView.setOnClickListener { onOpen(m) }
    }
}
