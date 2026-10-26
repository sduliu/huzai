package com.kudo.android.hupu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tab 重选信号（点击已选中 Tab 触发页面刷新）。
 * 页面常驻组合（切 Tab 不重组），不能靠参数传刷新事件——用 object 单例的
 * observable state 传信号（与 HupuPrefs.homeTopicsVersion 同款模式）。
 * MainActivity 的 Tab onClick 检测重选并递增；对应页面 LaunchedEffect 观察并刷新。
 */
object HupuUiSignals {
    /** 首页 Tab 重选（已选中时再点） */
    var homeTabTap by mutableIntStateOf(0)
        private set

    /** 评分 Tab 重选（已选中时再点） */
    var scoreTabTap by mutableIntStateOf(0)
        private set

    /** 评分 Tab 被选中（切入评分页）。页面常驻组合，冷启动首屏加载可能已失败；
     *  切入时据此做一次补偿重试，避免看到残留的失败态。 */
    var scoreTabShown by mutableIntStateOf(0)
        private set

    /**
     * 批N-2：详情页作者行「版块名 >」→ 打开对应专区版块流。
     * 版块流盖入页宿主在 ZonePage（底栏 Tab 1）；详情页在首页 Tab 里，
     * 跨页导航借信号总线：发起方置值 → KudoApp 切到专区 Tab → ZonePage 消费并打开。
     */
    var zoneBoardRequest by mutableStateOf<HupuTopicInfo?>(null)

    /**
     * 批Nfix-4：专区盖入详情页打开时隐藏主导航药丸。
     * 详情盖入层（ThreadDetailOverlay）渲染在 GlassNavigation 下层——两者同在底边会重叠；
     * 官方详情是推入页本无底栏导航。ZonePage 在详情可见期间置 true，KudoApp 据此跳过药丸。
     */
    var zoneDetailOpen by mutableStateOf(false)

    fun requestZoneBoard(topic: HupuTopicInfo) {
        zoneBoardRequest = topic
    }

    fun consumeZoneBoard(): HupuTopicInfo? {
        val t = zoneBoardRequest
        zoneBoardRequest = null
        return t
    }

    fun tapHome() {
        homeTabTap++
    }

    fun tapScore() {
        scoreTabTap++
    }

    fun enterScore() {
        scoreTabShown++
    }
}
