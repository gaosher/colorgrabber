package com.example.colorgrabber.color

/** 0..255 整数 RGB */
data class Rgb(val r: Int, val g: Int, val b: Int)

/** h: 0..360, s/v: 0..1 */
data class Hsv(val h: Double, val s: Double, val v: Double)

/** CIE L*a*b* (D65) */
data class Lab(val l: Double, val a: Double, val b: Double)

/** 三通道吸光度 */
data class Absorbance(val aR: Double, val aG: Double, val aB: Double)
