package com.example.colorgrabber.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private fun m(id: Long, name: String) = Measurement(
        id = id, timestamp = 1000L, sampleName = name, source = "live",
        roiX = 0, roiY = 0, roiW = 10, roiH = 10,
        rawR = 200, rawG = 100, rawB = 50, normR = 210, normG = 105, normB = 55,
        hsvH = 20.0, hsvS = 0.7, hsvV = 0.8, labL = 50.0, labA = 30.0, labB = 40.0,
        absR = null, absG = null, absB = null,
        gainR = 1.05, gainG = 1.0, gainB = 1.1, tempAdjust = 0.0
    )

    @Test fun hasBomHeaderAndOneDataRow() {
        val csv = CsvExporter.toCsv(listOf(m(1, "s1")))
        assertTrue(csv.startsWith("﻿"))        // UTF-8 BOM
        assertTrue(csv.contains("样品名"))
        assertEquals(2, csv.trim().lines().size)    // 表头行 + 1 数据行
    }

    @Test fun escapesCommaAndQuote() {
        val csv = CsvExporter.toCsv(listOf(m(1, "a,b\"c")))
        assertTrue(csv.contains("\"a,b\"\"c\""))
    }

    @Test fun nullAbsorbanceIsEmpty() {
        val csv = CsvExporter.toCsv(listOf(m(1, "s1")))
        val dataLine = csv.trim().lines().last()
        assertTrue(dataLine.contains(",,"))         // 连续空吸光度单元格
    }
}
