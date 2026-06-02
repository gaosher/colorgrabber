package com.example.colorgrabber.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sampleName: String = "",
    val note: String = "",
    val degradationTime: String = "",   // 降解时间点，可空字符串
    val source: String,                 // "live" 或 "pick"
    val imageUri: String? = null,
    val roiX: Int, val roiY: Int, val roiW: Int, val roiH: Int,
    val rawR: Int, val rawG: Int, val rawB: Int,    // 原始平均 RGB
    val normR: Int, val normG: Int, val normB: Int, // 白归一化后 RGB
    val hsvH: Double, val hsvS: Double, val hsvV: Double,
    val labL: Double, val labA: Double, val labB: Double,
    val absR: Double?, val absG: Double?, val absB: Double?, // 无参比为 null
    val gainR: Double, val gainG: Double, val gainB: Double,
    val tempAdjust: Double,
    val isReference: Boolean = false    // 是否为参比 I0
)
