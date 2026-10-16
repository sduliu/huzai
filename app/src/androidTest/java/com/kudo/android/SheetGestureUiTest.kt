package com.kudo.android

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.components.CommunityButton
import com.kudo.android.ui.components.CommunitySheet
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetGestureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sheetTracksFingerReturnsFromSmallDragAndDismissesWithoutChangingSettings() {
        var updates = 0
        compose.setContent { KudoTheme(DisplaySettings()) { SettingsScreen(DisplaySettings(), { updates++ }, null, {}, {}, {}) } }
        compose.onNodeWithText("主题风格").performClick()
        val handle = compose.onNodeWithTag("sheet-drag-handle").assertIsDisplayed()
        val sheet = compose.onNodeWithTag("liquid-action-sheet")
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        handle.performTouchInput { down(center); moveBy(Offset(0f, 100f)); advanceEventTime(300) }
        assertTrue(sheet.fetchSemanticsNode().boundsInRoot.top > top + 10f)
        handle.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 2f)
        handle.performTouchInput { swipe(center, center + Offset(0f, 550f), 600) }
        sheet.assertDoesNotExist()
        compose.onNodeWithText("主题风格").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, updates) }
    }

    @Test fun scrollingContentConsumesDragBeforeTheSheetAndOverflowAtTopDismisses() {
        val visibility = MutableTransitionState(true)
        compose.setContent { KudoTheme(DisplaySettings()) {
            CommunitySheet(visibility, "长列表", { visibility.targetState = false }) {
                repeat(50) { Text("条目 $it", Modifier.fillMaxWidth().height(48.dp)) }
            }
        } }
        val sheet = compose.onNodeWithTag("liquid-action-sheet")
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("条目 30").performScrollTo()
        compose.onNode(hasScrollAction()).performTouchInput { swipe(center, center + Offset(0f, 160f), 600) }
        sheet.assertIsDisplayed()
        assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithText("条目 0").assertIsNotDisplayed().performScrollTo()
        compose.onNode(hasScrollAction()).performTouchInput { swipe(center, center + Offset(0f, 600f), 600) }
        sheet.assertDoesNotExist()
    }

    @Test fun shortWindowKeepsCancelReachableAndBusySheetCannotBeDismissed() {
        val visibility = MutableTransitionState(true)
        var busy by mutableStateOf(true)
        compose.setContent { KudoTheme(DisplaySettings(fontScale = 1.3f)) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                CommunitySheet(visibility, "选择内容", { visibility.targetState = false }, dismissEnabled = !busy, showActions = true,
                    actions = { CommunityButton("取消", { visibility.targetState = false }, Modifier.fillMaxWidth(), enabled = !busy) }) {
                    repeat(30) { Text("选项 $it", Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
                }
            }
        } }
        val sheet = compose.onNodeWithTag("liquid-action-sheet")
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("取消").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("sheet-drag-handle").performTouchInput { swipe(center, center + Offset(0f, 450f), 600) }
        compose.onNodeWithTag("modal-scrim").performTouchInput { click(Offset(2f, 2f)) }
        sheet.assertIsDisplayed()
        assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("取消").assertIsEnabled().performClick()
        sheet.assertDoesNotExist()
    }
}
