package com.example.colorgrabber.wb

import com.example.colorgrabber.color.Rgb

/**
 * 一次测量会话的白平衡参数：点白基础增益 + 色温微调 + RGB 每通道微调。
 * 取景页（实时）和测量页（静态照片）共用，保证两处算法一致。
 */
class WbState {
    var baseGains = Gains(1.0, 1.0, 1.0)   // 点白得到
    var tempAdjust = 0.0
    var rAdj = 1.0
    var gAdj = 1.0
    var bAdj = 1.0

    /** 点白基础增益 → 叠加色温微调 → 叠加 RGB 每通道微调。 */
    fun effectiveGains(): Gains {
        val withTemp = WhiteBalanceEngine.applyTempAdjust(baseGains, tempAdjust)
        return WhiteBalanceEngine.applyRgbAdjust(withTemp, rAdj, gAdj, bAdj)
    }

    /** 以一块白色区域的原始 RGB 重新标定基础增益。 */
    fun pickWhite(rawWhite: Rgb) {
        baseGains = WhiteBalanceEngine.gainsFromWhite(rawWhite)
    }
}
