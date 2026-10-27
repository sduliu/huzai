package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.kudo.android.data.DisplaySettings
import com.kudo.android.hupu.HUPU_BOARDS
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuMatchTeam
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupuui.pages.BoardContent
import com.kudo.android.hupuui.pages.BoardUiState
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批X5-4：赛程 tab 恒为完整日分组列表（官方赛程页形态）。
 * 「近期比赛」标题行 / 积分榜 pill / 「查看全部赛程 ›」/ ≤4 卡收起态整组移除；
 * 返回键无就地拦截（系统返回即退页回频道，与批X4-2 直达落点语义等价）。
 */
class BoardScheduleFullUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun team(name: String) = HupuMatchTeam(
        memberId = name, name = name, logo = null,
        baseScore = "0", extraScore = null, bigScore = null,
    )

    private fun match(
        id: String,
        home: String,
        away: String,
        status: String = "COMPLETED",
        statusDesc: String = "已结束",
        scoreCountText: String = "3.5万人评分",
        liveClockText: String? = null,
        winnerMemberId: String? = null,
    ) = HupuMatch(
        matchId = id,
        statusDesc = statusDesc,
        status = status,
        introduction = "NBA常规赛",
        matchName = "NBA常规赛",
        startTimeText = "10月24日 22:00",
        startTimestamp = 0L,
        scoreCountText = scoreCountText,
        home = team(home),
        away = team(away),
        winnerMemberId = winnerMemberId,
        playerScore = null,
        scoreBizType = null,
        scoreBizNo = null,
        liveClockText = liveClockText,
    )

    // 批X6：dayTime 用远未来日——锚定逻辑（今天→最近过往日→首日）恒落首日不滚动，
    // 两日分区均在视口内，断言不受测试设备当前日期影响。
    private fun fakeDays() = listOf(
        HupuMatchDay("2099-10-24", "10月24日 周六", listOf(match("m1", "热火", "黄蜂"))),
        HupuMatchDay("2099-10-25", "10月25日 周日", listOf(match("m2", "湖人", "爵士"))),
    )

    private fun boardState(): BoardUiState =
        BoardUiState(HUPU_BOARDS["nba"]!!, HupuRepository(), kotlinx.coroutines.MainScope())

    /** 赛程区直出完整日分组：无「近期比赛」标题行/积分榜 pill/查看全部赛程/收起。 */
    @Test fun scheduleTabRendersFullDayGroupsWithoutCollapsedChrome() {
        val state = boardState()
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                BoardContent(state, {}, hostSchedule = fakeDays(), topInset = 0.dp)
            }
        }
        compose.waitForIdle()
        // 日分组的比赛行直接可见（hostSchedule 首帧直出，无中转态）
        compose.onNodeWithText("热火").assertIsDisplayed()
        compose.onNodeWithText("湖人").assertIsDisplayed()
        // 收起态/中转形态整组不存在
        compose.onNodeWithText("近期比赛").assertDoesNotExist()
        compose.onNodeWithText("全部赛程").assertDoesNotExist()
        compose.onNodeWithText("查看全部赛程").assertDoesNotExist()
        compose.onNodeWithText("收起").assertDoesNotExist()
        compose.onNodeWithText("积分榜").assertDoesNotExist()
    }

    /** 返回键无就地收起拦截：onBackPressed 直接落到 Activity 结束（=宿主整页退）。 */
    @Test fun backPressExitsPageInsteadOfCollapsing() {
        val state = boardState()
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                BoardContent(state, {}, hostSchedule = fakeDays(), topInset = 0.dp)
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue(compose.activity.isFinishing) }
    }

    /**
     * 批X6-1/2/3：官方三态行渲染——灰条 dateBlock 组头直出、行内纯时间；
     * 已结束=「已结束」+「N万人评分」（scoreCountText 真实字段）；
     * 进行中=左列实时钟（midGameStageInfo 拼接）+右列「进行中」；
     * 未开始=逐队「-」+可订阅行「预约」铃铛位（黑色，不改文案断言）。
     */
    @Test fun matchRowsRenderOfficialThreeStates() {
        // 直接挂日分区组件断言行渲染——不经过 LazyColumn 锚定（与设备日期解耦）。
        val day = HupuMatchDay(
            "2099-10-26", "昨天 周一",
            listOf(
                match("d1", "湖人", "爵士", winnerMemberId = "湖人"),
                match("d2", "勇士", "掘金", status = "INPROGRESS",
                    statusDesc = "进行中", scoreCountText = "",
                    liveClockText = "第四节剩17秒"),
                HupuMatch(
                    matchId = "d3", statusDesc = "未开始", status = "NOTSTARTED",
                    introduction = "NBA常规赛", matchName = "NBA常规赛",
                    startTimeText = "10月27日 08:30", startTimestamp = 0L,
                    scoreCountText = "",
                    home = HupuMatchTeam("篮网", "篮网", null, "-", null, null),
                    away = HupuMatchTeam("凯尔特人", "凯尔特人", null, "-", null, null),
                    winnerMemberId = null, playerScore = null,
                    scoreBizType = null, scoreBizNo = null,
                    subscribe = com.kudo.android.hupu.HupuSubscribe(
                        show = true, subscribed = false,
                        title = "篮网 vs 凯尔特人", desc = "篮网 vs 凯尔特人",
                    ),
                ),
            ),
        )
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                com.kudo.android.hupuui.pages.ScheduleDaySection(day, onOpenMatch = {})
            }
        }
        compose.waitForIdle()
        // X6-1：灰条组头 dateBlock 直出（含相对日文案）
        compose.onNodeWithText("昨天 周一").assertIsDisplayed()
        // X6-3：已结束=状态字+真实评分计数字段
        compose.onNodeWithText("已结束").assertIsDisplayed()
        compose.onNodeWithText("3.5万人评分").assertIsDisplayed()
        // X6-3：进行中=实时钟直出（非编造的「进行中」占位）+右列状态字
        compose.onNodeWithText("第四节剩17秒").assertIsDisplayed()
        compose.onNodeWithText("进行中").assertIsDisplayed()
        // X6-2/3：未开始=逐队「-」比分 + 「预约」订阅位（铃铛为图标，无文本断言）
        compose.onNodeWithText("预约").assertIsDisplayed()
        // 行内不再重复日期（旧「10月24日 22:00」全串消失，只剩纯时间「22:00」）
        compose.onNodeWithText("10月24日 22:00").assertDoesNotExist()
        compose.onNodeWithText("22:00", substring = false).assertIsDisplayed()
    }

    /**
     * 批X6fix-2：真实打开路径回归——ScheduleList（LazyColumn 16dp contentPadding
     * + 今日锚定 + 滚动组合）带多日数据首帧不崩。
     * 批X6-6 缺口点名：matchRowsRenderOfficialThreeStates 直挂 ScheduleDaySection
     * 且未在设备执行——负 padding 出血在 LazyColumn 内首测即炸，真机才暴露。
     */
    @Test fun scheduleListComposesAnchoredAndScrolled() {
        val today = java.time.LocalDate.now()
        val days = listOf(
            HupuMatchDay(
                today.minusDays(1).toString(), "昨天 周一",
                listOf(match("p1", "太阳", "国王")),
            ),
            HupuMatchDay(
                today.toString(), "今天 周二",
                listOf(match("p2", "快船", "雷霆")),
            ),
            HupuMatchDay(
                today.plusDays(1).toString(), "明天 周三",
                listOf(match("p3", "灰熊", "马刺")),
            ),
        )
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                com.kudo.android.hupuui.pages.ScheduleList(
                    days = days, resetKey = "x6fix", onOpenMatch = {},
                )
            }
        }
        compose.waitForIdle()
        // 锚定到今日：组头（dateBlock 直出）与当日行可见——即真机崩溃的首组合路径
        compose.onNodeWithText("今天 周二").assertIsDisplayed()
        compose.onNodeWithText("快船").assertIsDisplayed()
        compose.onNodeWithText("雷霆").assertIsDisplayed()
        // 滚动到首/尾日：出血绘制路径在各区首测均走通
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.waitForIdle()
        compose.onNodeWithText("昨天 周一").assertIsDisplayed()
        compose.onNodeWithText("太阳").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.waitForIdle()
        compose.onNodeWithText("明天 周三").assertIsDisplayed()
        compose.onNodeWithText("马刺").assertIsDisplayed()
    }
}
