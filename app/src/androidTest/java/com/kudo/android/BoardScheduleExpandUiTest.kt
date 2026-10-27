package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kudo.android.data.DisplaySettings
import com.kudo.android.hupu.HUPU_BOARDS
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupuui.pages.BoardContent
import com.kudo.android.hupuui.pages.BoardUiState
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批X4-1：频道「🔥 查看近期N场热门比赛」直达全赛程落点的返回语义。
 * 直达展开（expandByEntry）→ 返回直接退页；就地展开 → 返回先收起；
 * 手动收起/重开后回到就地语义。
 */
class BoardScheduleExpandUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @androidx.compose.runtime.Composable
    private fun Page(state: BoardUiState, onBackDirect: () -> Unit = {}) {
        KudoTheme(DisplaySettings()) {
            BoardContent(state, {}, topInset = 0.dp, onBackDirect = onBackDirect)
        }
    }

    private fun boardState(): BoardUiState =
        BoardUiState(HUPU_BOARDS["nba"]!!, HupuRepository(),
            kotlinx.coroutines.MainScope())

    private fun pressBack() =
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun directExpandedBackExitsPageInsteadOfCollapsing() {
        val state = boardState()
        var exits = 0
        state.showAllMatches = true
        state.expandByEntry = true
        compose.setContent { Page(state) { exits++ } }
        compose.onNodeWithText("全部赛程").assertIsDisplayed()
        pressBack()
        compose.runOnIdle {
            assertEquals(1, exits)
            assertTrue(state.showAllMatches)   // 退页不收起——落点形态由系统返回销毁
        }
    }

    @Test fun manualExpandBackCollapsesInPlace() {
        val state = boardState()
        var exits = 0
        state.showAllMatches = true
        // expandByEntry 默认 false——就地展开返回键先收起
        compose.setContent { Page(state) { exits++ } }
        compose.onNodeWithText("全部赛程").assertIsDisplayed()
        pressBack()
        compose.runOnIdle {
            assertEquals(0, exits)
            assertFalse(state.showAllMatches)
        }
        compose.onNodeWithText("近期比赛").assertIsDisplayed()
    }

    @Test fun collapseClearsEntryFlagAndRestoresInPlaceSemantics() {
        val state = boardState()
        var exits = 0
        state.showAllMatches = true
        state.expandByEntry = true
        compose.setContent { Page(state) { exits++ } }
        // 手动「收起」→ 标记失效、回到中转形态
        compose.onNodeWithText("收起").performClick()
        compose.runOnIdle {
            assertFalse(state.showAllMatches)
            assertFalse(state.expandByEntry)
        }
        // 手动重新展开 → 返回回到就地收起语义，不再整页退出
        compose.onNodeWithText("查看全部赛程").performClick()
        compose.runOnIdle { assertTrue(state.showAllMatches) }
        pressBack()
        compose.runOnIdle {
            assertEquals(0, exits)
            assertFalse(state.showAllMatches)
        }
    }
}
