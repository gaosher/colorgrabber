package com.example.colorgrabber.wb

import com.example.colorgrabber.camera.RoiResult

/** 点白选区可能存在的问题。 */
enum class WhiteIssue(val message: String) {
    OVEREXPOSED("白点过曝，增益会偏小：请降低补光或换稍暗的白色区域"),
    TOO_DARK("白点偏暗，可能在阴影里：请选更亮的白背景或增加补光"),
    NON_UNIFORM("选区不均匀，可能框到了反光、阴影或边缘：请只框平整的白背景"),
}

/** 检查点白选区是否适合作为白点。只做提示，不阻止校准。 */
object WhitePointQuality {
    /** 过曝像素占比超过该值即提示（容忍少量噪点）。 */
    const val MAX_OVEREXPOSED_RATIO = 0.01
    /** 最亮通道的平均值低于该值视为偏暗（暖光下蓝通道本就偏低，所以看最亮通道）。 */
    const val MIN_BRIGHTNESS = 150
    /** 标准差超过该值视为不均匀（正常传感器噪声约 3～8）。 */
    const val MAX_STD_DEV = 15.0

    fun check(res: RoiResult): List<WhiteIssue> = buildList {
        if (res.overexposedRatio > MAX_OVEREXPOSED_RATIO) add(WhiteIssue.OVEREXPOSED)
        if (maxOf(res.mean.r, res.mean.g, res.mean.b) < MIN_BRIGHTNESS) add(WhiteIssue.TOO_DARK)
        if (res.stdDev > MAX_STD_DEV) add(WhiteIssue.NON_UNIFORM)
    }
}
