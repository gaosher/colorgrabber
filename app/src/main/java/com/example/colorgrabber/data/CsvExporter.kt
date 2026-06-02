package com.example.colorgrabber.data

object CsvExporter {
    private val HEADERS = listOf(
        "id", "时间戳", "样品名", "备注", "降解时间点", "来源", "图片URI",
        "ROI_x", "ROI_y", "ROI_w", "ROI_h",
        "原始R", "原始G", "原始B", "归一R", "归一G", "归一B",
        "H", "S", "V", "L", "a", "b",
        "吸光度R", "吸光度G", "吸光度B",
        "增益R", "增益G", "增益B", "色温调整", "是否参比"
    )

    fun toCsv(items: List<Measurement>): String {
        val sb = StringBuilder()
        sb.append('﻿')   // UTF-8 BOM，Excel 中文不乱码
        sb.append(HEADERS.joinToString(",") { esc(it) }).append('\n')
        for (m in items) {
            val cells = listOf(
                m.id.toString(), m.timestamp.toString(), m.sampleName, m.note,
                m.degradationTime, m.source, m.imageUri ?: "",
                m.roiX.toString(), m.roiY.toString(), m.roiW.toString(), m.roiH.toString(),
                m.rawR.toString(), m.rawG.toString(), m.rawB.toString(),
                m.normR.toString(), m.normG.toString(), m.normB.toString(),
                fmt(m.hsvH), fmt(m.hsvS), fmt(m.hsvV), fmt(m.labL), fmt(m.labA), fmt(m.labB),
                m.absR?.let { fmt(it) } ?: "", m.absG?.let { fmt(it) } ?: "", m.absB?.let { fmt(it) } ?: "",
                fmt(m.gainR), fmt(m.gainG), fmt(m.gainB), fmt(m.tempAdjust),
                if (m.isReference) "1" else "0"
            )
            sb.append(cells.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun fmt(d: Double): String = String.format("%.4f", d)

    private fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\"" else s
}
