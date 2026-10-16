package com.kudo.android

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class TopicFloorKeyboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun unchangedCloseHidesTheKeyboardWithoutNavigating() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val author = Member(1, "fixture", "离线作者", "")
        val posts = (1..8).map { Post(it, it, author, "<p>第 $it 楼</p>", "", 0, false, null, null, false) }
        val state = TopicState(TopicDetail(5, "关闭键盘", 1, posts, posts.map(Post::id), false, highestPostNumber = 8), false)
        var ime = false
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            val visible = WindowInsets.isImeVisible
            SideEffect { ime = visible }
            TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                readingEnabled = false, jumpTo = { requests++ })
        } }
        compose.onNodeWithTag("topic-floor-index").performClick()
        compose.onNodeWithTag("floor-input", useUnmergedTree = true).performClick().assertIsFocused()
        compose.waitUntil(5_000) { ime }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "floor-inline-close-keyboard.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        compose.onNodeWithContentDescription("关闭楼层选择", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { !ime && compose.onAllNodesWithTag("floor-input", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("topic-reply").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun floorInputAndConfirmationStayAboveKeyboardAndRestoreTheBottomBar() {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val author = Member(1, "fixture", "离线作者", "")
        val posts = (1..10).map { Post(it, it, author, "<p>正文第 $it 楼</p>", "", 0, false, null, null, false) }
        var state by mutableStateOf(TopicState(TopicDetail(5, "键盘避让", 1, posts, posts.map(Post::id), false,
            highestPostNumber = 10), false))
        var ime = false
        var imeBottom = 0
        var requested: Int? = null
        compose.setContent { KudoTheme(DisplaySettings()) {
            val density = LocalDensity.current
            val visible = WindowInsets.isImeVisible
            val bottom = WindowInsets.ime.getBottom(density)
            SideEffect { ime = visible; imeBottom = bottom }
            TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, readingEnabled = false,
                jumpTo = { requested = it; state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1) })
        } }
        val expandedBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithTag("topic-floor-index").performClick()
        val input = compose.onNodeWithTag("floor-input", useUnmergedTree = true)
        input.performClick().assertIsFocused()
        compose.waitUntil(5_000) { ime }
        input.performTextReplacement("8")
        val confirm = compose.onNodeWithTag("floor-confirm", useUnmergedTree = true).assertIsDisplayed()
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "floor-inline-keyboard.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("confirmation must remain above the numeric keyboard",
            confirm.fetchSemanticsNode().boundsInRoot.bottom <= minOf(rootBottom, expandedBottom - imeBottom) + 2f)
        confirm.performClick()
        compose.waitUntil(5_000) { !ime }
        compose.onNodeWithTag("topic-floor-index").assertIsDisplayed()
        compose.onNodeWithTag("topic-reply").assertIsDisplayed()
        compose.onNodeWithTag("topic-actions").assertIsDisplayed()
        compose.onNodeWithTag("post-8").assertIsDisplayed()
        compose.runOnIdle { assertEquals(8, requested) }
    }
}
