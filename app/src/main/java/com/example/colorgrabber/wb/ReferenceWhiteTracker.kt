package com.example.colorgrabber.wb

import com.example.colorgrabber.camera.RoiResult

/**
 * 「参考白模式」：每一帧同时采样样品框与旁边的参考白框，用参考白实时重算增益。
 * 两个框在同一帧里，曝光、白平衡、补光强度的变化会同时作用于两者而相互抵消，因此无需锁定相机。
 *
 * 参考白必须连续 [requiredGoodFrames] 次通过 [WhitePointQuality] 检查才开始生效，
 * 避免镜头移动过程中把非白色区域当成白点；不合格时保留上一次的增益。
 */
class ReferenceWhiteTracker(private val requiredGoodFrames: Int = 5) {

    sealed class Status {
        /** 参考白合格，正在累计稳定帧数。 */
        data class Settling(val good: Int, val required: Int) : Status()
        /** 参考白稳定，增益已按本帧更新。 */
        object Ready : Status()
        /** 参考白不合格，增益未更新。 */
        data class Bad(val issues: List<WhiteIssue>) : Status()
    }

    private var goodCount = 0
    var status: Status = Status.Settling(0, requiredGoodFrames)
        private set

    /** 用本帧参考白的采样结果更新状态；稳定时返回新的增益，否则返回 null。 */
    fun update(ref: RoiResult): Gains? {
        val issues = WhitePointQuality.check(ref)
        if (issues.isNotEmpty()) {
            goodCount = 0
            status = Status.Bad(issues)
            return null
        }
        if (goodCount < requiredGoodFrames) goodCount++
        if (goodCount < requiredGoodFrames) {
            status = Status.Settling(goodCount, requiredGoodFrames)
            return null
        }
        status = Status.Ready
        return WhiteBalanceEngine.gainsFromWhite(ref.mean)
    }

    fun reset() {
        goodCount = 0
        status = Status.Settling(0, requiredGoodFrames)
    }
}
