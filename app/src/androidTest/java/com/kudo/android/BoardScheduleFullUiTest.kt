package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

    private fun match(id: String, home: String, away: String) = HupuMatch(
        matchId = id,
        statusDesc = "已结束",
        status = "COMPLETED",
        introduction = "NBA常规赛",
        matchName = "NBA常规赛",
        startTimeText = "10月24日 22:00",
        startTimestamp = 0L,
        scoreCountText = "3.5万人评分",
        home = team(home),
        away = team(away),
        winnerMemberId = null,
        playerScore = null,
        scoreBizType = null,
        scoreBizNo = null,
    )

    private fun fakeDays() = listOf(
        HupuMatchDay("2026-10-24", "10月24日 周六", listOf(match("m1", "热火", "黄蜂"))),
        HupuMatchDay("2026-10-25", "10月25日 周日", listOf(match("m2", "湖人", "爵士"))),
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
}
