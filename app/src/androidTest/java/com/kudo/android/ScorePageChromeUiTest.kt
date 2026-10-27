package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kudo.android.data.DisplaySettings
import com.kudo.android.hupu.HupuDataTab
import com.kudo.android.hupuui.pages.LeagueDataTabBar
import com.kudo.android.hupuui.pages.ScoreNavRequest
import com.kudo.android.hupuui.pages.ScorePage
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 批X5：ScorePage 顶栏官方化回归。
 * X5-1 居中赛事名标题、X5-2 无赛事 chip 条/管理入口、X5-3 数据 tab 纯文字行语义；
 * X5-4 赛程恒为完整日分组见 BoardScheduleFullUiTest。
 */
class ScorePageChromeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** 顶栏 = 返回箭头 + 居中当前赛事名；无多赛事 chip 条与「管理」入口。 */
    @Test fun topBarShowsLeagueTitleWithoutSportChips() {
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                ScorePage(request = ScoreNavRequest("nba", nonce = 1L), onBack = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("score-topbar")
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("NBA")))
        // 通用「比赛」标题不复存在（精确匹配，避免误伤「暂无比赛」等占位文案）
        compose.onNodeWithText("比赛", substring = false).assertDoesNotExist()
        // 赛事 chip 条右端「管理」入口不复存在
        compose.onNodeWithContentDescription("频道管理").assertDoesNotExist()
    }

    /** 数据 tab 纯文字行：选中态语义 + 点按分派（tab 集合/顺序由接口决定，这里钉交互）。 */
    @Test fun leagueDataTabBarSelectionAndDispatch() {
        val tabs = listOf(
            HupuDataTab("t1", "全球", "oddsSchedule", "u1"),
            HupuDataTab("t2", "赛程", "schedule", "u2"),
            HupuDataTab("t3", "球队榜", "h5", "u3"),
        )
        var picked: String? = null
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                LeagueDataTabBar(tabs, selectedId = "t2") { picked = it.id }
            }
        }
        compose.onNodeWithText("赛程").assertIsSelected()
        compose.onNodeWithText("全球").assertIsNotSelected()
        compose.onNodeWithText("球队榜").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals("t3", picked) }
    }
}
