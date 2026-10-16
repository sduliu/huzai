package com.kudo.android

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.account.AboutScreen
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsModalUiTest {
    @get:Rule val compose = createComposeRule()

    private val member = Member(1, "fixture", "弹窗测试", "")
    private lateinit var inputMode: InputModeManager

    @Test fun sheetSelectionIsUniqueAndPersistsOnce() {
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.LIGHT))
        var updates = 0
        show({ settings }, { settings = it; updates++ })
        compose.onNodeWithText("界面材质效果").performTouchInput { click() }
        compose.onNodeWithTag("liquid-action-sheet").assertIsDisplayed()
        compose.onAllNodesWithText("字体大小").assertCountEquals(0)
        compose.onAllNodesWithText("界面材质效果").assertCountEquals(0)
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithTag("modal-choice-${GlassMode.LIQUID.ordinal}").assertIsSelected()
        capture("settings-sheet-light.png")
        compose.onNodeWithText("半透明").performTouchInput { click() }
        compose.runOnIdle { assertEquals(GlassMode.TRANSLUCENT, settings.glass); assertEquals(1, updates) }
        compose.onNodeWithTag("liquid-action-sheet").assertDoesNotExist()
        compose.onNodeWithText("界面材质效果").performClick()
        compose.onNodeWithTag("modal-choice-${GlassMode.TRANSLUCENT.ordinal}").assertIsSelected()
        closeSheet()
    }

    @Test fun cancelScrimAndBackDoNotChangeSettingsOrNavigate() {
        val settings = DisplaySettings(theme = ThemeMode.LIGHT)
        var updates = 0
        var backs = 0
        show({ settings }, { updates++ }, back = { backs++ })
        compose.onNodeWithText("主题风格").performClick()
        closeSheet()
        compose.onNodeWithTag("liquid-action-sheet").assertDoesNotExist()
        compose.onNodeWithText("主题风格").performClick()
        compose.onNodeWithTag("modal-scrim").performTouchInput { click(Offset(width / 2f, height * 0.1f)) }
        compose.onNodeWithTag("liquid-action-sheet").assertDoesNotExist()
        compose.onNodeWithText("主题风格").performClick()
        compose.onNodeWithTag("liquid-action-sheet").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("liquid-action-sheet").assertDoesNotExist()
        compose.onNodeWithText("主题风格").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, updates); assertEquals(0, backs) }
    }

    @Test fun dialogBlankAreaDoesNotDismissAndBackgroundCannotOpenLinks() {
        var links = 0
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) { AboutScreen(openLink = { links++ }) {} } }
        openSetting("隐私说明")
        compose.onNodeWithTag("liquid-dialog").assertIsDisplayed()
        compose.onAllNodesWithText("社区指南").assertCountEquals(0)
        compose.onNodeWithTag("liquid-dialog").performTouchInput { click(Offset(width / 2f, 10f)) }
        compose.onNodeWithTag("liquid-dialog").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, links) }
        capture("settings-dialog-light.png")
        compose.onNodeWithText("知道了").performTouchInput { click() }
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardFocusStaysInsideOpenSheet() {
        show({ DisplaySettings(theme = ThemeMode.LIGHT) }, {})
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        compose.onNodeWithText("主题风格").performClick()
        compose.onNodeWithTag("liquid-action-sheet").assertIsDisplayed()
        repeat(8) {
            compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("liquid-action-sheet")))
                .performKeyInput { pressKey(Key.Tab) }
        }
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("liquid-action-sheet")))
        closeSheet()
    }

    @Test fun logoutRequiresConfirmationAndDoesNotRepeatDuringExit() {
        var logouts = 0
        show({ DisplaySettings(theme = ThemeMode.LIGHT) }, {}, user = member, logout = { logouts++ })
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("退出登录"))
        compose.onNodeWithText("退出登录").performClick()
        compose.onNodeWithTag("liquid-dialog").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, logouts) }
        compose.onNodeWithText("退出登录").performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("退出").performClick()
        compose.onNodeWithText("退出").performClick()
        compose.runOnIdle { assertEquals(1, logouts) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun darkLargeTextIsReadableInEveryMaterial() {
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f))
        show({ settings }, { settings = it })
        for (mode in GlassMode.entries) {
            compose.runOnIdle { settings = settings.copy(glass = mode) }
            compose.onNodeWithText("字体大小").performClick()
            compose.onNodeWithText("大号 · 130%").assertIsDisplayed()
            compose.onNodeWithText("取消").assertDoesNotExist()
            capture("settings-sheet-${mode.name.lowercase()}-dark-large.png")
            closeSheet()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("字体大小"))
        }
    }

    @Test fun shortWindowKeepsDialogContentScrollableAndActionsVisible() {
        var links = 0
        compose.setContent {
            val settings = DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)
            KudoTheme(settings) {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    AboutScreen(openLink = { links++ }) {}
                }
            }
        }
        openSetting("开源致谢")
        compose.onNodeWithText("知道了").assertIsDisplayed()
        compose.onNodeWithText("FluxDO 接口参考").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, links) }
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
    }

    private fun closeSheet() {
        compose.onNodeWithTag("modal-scrim").performTouchInput { click(Offset(2f, 2f)) }
    }

    private fun openSetting(label: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
    }

    private fun show(settings: () -> DisplaySettings, update: (DisplaySettings) -> Unit,
                     user: Member? = null, logout: () -> Unit = {}, back: () -> Unit = {}, openLink: (String) -> Unit = {}) {
        compose.setContent {
            inputMode = LocalInputModeManager.current
            KudoTheme(settings()) { SettingsScreen(settings(), update, user, logout, back, openLink) }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
