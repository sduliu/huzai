package com.kudo.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.components.GlassNavigation
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationActionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun defaultsToComposeUnknownFallsBackAndSelectionPersistsWhileCancelDoesNotSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("kudo_preferences", 0)
        val previous = prefs.getString("navigation_action", null)
        try {
            prefs.edit().remove("navigation_action").commit()
            assertEquals(NavigationAction.COMPOSE, AppSettings(context).display.value.navigationAction)
            prefs.edit().putString("navigation_action", "unknown").commit()
            val store = AppSettings(context)
            assertEquals(NavigationAction.COMPOSE, store.display.value.navigationAction)
            var settings by mutableStateOf(store.display.value)
            compose.setContent { KudoTheme(settings) { SettingsScreen(settings, { settings = it; store.update(it) }, null, {}, {}, {}) } }
            compose.onNodeWithText("底栏中间按钮").performScrollTo().performClick()
            compose.onNodeWithTag("modal-choice-0").assertIsSelected()
            compose.onNodeWithTag("modal-choice-1").performClick()
            compose.waitForIdle()
            assertEquals(NavigationAction.SEARCH, AppSettings(context).display.value.navigationAction)
            compose.onNodeWithText("底栏中间按钮").performClick()
            compose.onNodeWithTag("modal-choice-1").assertIsSelected()
            compose.onNodeWithTag("sheet-drag-handle").performTouchInput { swipeDown() }
            assertEquals(NavigationAction.SEARCH, AppSettings(context).display.value.navigationAction)
        } finally { prefs.edit().apply { if (previous == null) remove("navigation_action") else putString("navigation_action", previous) }.commit() }
    }
    @Test fun searchAndComposeHaveOneAccessibleActionAndNeverSelectMiddleTab() {
        var action by mutableStateOf(NavigationAction.COMPOSE)
        var clicks = 0
        val selected = mutableListOf<Int>()
        compose.setContent { KudoTheme(DisplaySettings()) {
            Box(Modifier.fillMaxSize()) {
                GlassNavigation(0, selected::add, { clicks++ }, rememberLayerBackdrop(), GlassMode.LIQUID,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(), action = action)
            }
        } }
        compose.onAllNodesWithContentDescription("发布话题").assertCountEquals(1)
        compose.onNodeWithContentDescription("发布话题").performClick()
        compose.runOnIdle { action = NavigationAction.SEARCH }
        compose.onNodeWithContentDescription("发布话题").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("搜索").assertCountEquals(1)
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.runOnIdle { assertEquals(2, clicks); assertTrue(selected.isEmpty()) }
    }
}
