package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.components.liquid.LiquidButton
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.TopicScreen
import com.kudo.android.ui.topic.TopicState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SurfaceLiquidUiTest {
    @get:Rule val compose = createComposeRule()

    private val member = Member(1, "fixture", "表面玻璃测试", "")
    private val post = Post(10, 1, member, buildString {
        repeat(12) { append("<p>测试正文 ${it + 1}：检查玻璃后面的真实内容与文字边缘，所有互动使用本地夹具。</p>") }
    }, "2026-10-08T00:00:00Z", 12, false, null, null, true)
    private val detail = TopicDetail(5, "官方表面玻璃操作栏测试", 1, listOf(post), listOf(post.id), false)

    @Test fun topicSurfaceActionsReceiveIndependentPhysicalClicks() {
        var replies = 0
        var likes = 0
        var boosts = 0
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                TopicScreen(TopicState(detail, false), GlassMode.LIQUID,
                    {}, {}, {}, { boosts++ }, { assertEquals(post.id, it.id); likes++ },
                    {}, { assertNull(it); replies++ }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("topic-actions").assertHasNoClickAction()
        compose.onAllNodesWithContentDescription("赞主帖").assertCountEquals(1)
        compose.onNodeWithText("写回复").performTouchInput { click() }
        compose.onNodeWithContentDescription("赞主帖").performTouchInput { click() }
        compose.onNodeWithContentDescription("Boost 主帖").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, replies)
            assertEquals(1, likes)
            assertEquals(1, boosts)
        }
        capture("topic-surface-light.png")
    }

    @Test fun closedBusyAndMissingPostKeepActionPermissions() {
        var state by mutableStateOf(TopicState(detail.copy(closed = true), false, busy = setOf(post.id)))
        var mutations = 0
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, { mutations++ }, { mutations++ },
                    { mutations++ }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("写回复").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithContentDescription("赞主帖").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithContentDescription("Boost 主帖").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, mutations) }
        compose.runOnIdle { state = TopicState(detail.copy(posts = listOf(post.copy(number = 2))), false) }
        compose.onNodeWithContentDescription("赞主帖").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Boost 主帖").assertIsNotEnabled()
        compose.onNodeWithText("写回复").assertIsEnabled()
    }

    @Test fun surfacePressMagnifiesContentAndCancelRestoresIt() {
        var clicks = 0
        showSurface({ true }) { clicks++ }
        val label = compose.onNodeWithText("Surface Liquid Button", useUnmergedTree = true)
        val resting = label.fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Surface Liquid Button")
        compose.mainClock.autoAdvance = false
        button.performTouchInput {
            down(center)
            moveBy(Offset(width * 0.1f, 0f), delayMillis = 200)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val pressed = label.fetchSemanticsNode().boundsInRoot
        assertTrue("按压时真实内容应放大：$resting → $pressed", pressed.height > resting.height)
        capture("surface-button-pressed.png")
        button.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        val returned = label.fetchSemanticsNode().boundsInRoot
        assertEquals("取消后恢复内容比例", resting.height, returned.height, 1f)
        assertEquals("取消后恢复横向位置", resting.center.x, returned.center.x, 1f)
        compose.runOnIdle { assertEquals("取消手势不触发动作", 0, clicks) }
        capture("surface-button-resting.png")
    }

    @Test fun disablingDuringPressClearsEffectBeforeReenabling() {
        var enabled by mutableStateOf(true)
        var clicks = 0
        showSurface({ enabled }) { clicks++ }
        val label = compose.onNodeWithText("Surface Liquid Button", useUnmergedTree = true)
        val resting = label.fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Surface Liquid Button")
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        assertTrue(label.fetchSemanticsNode().boundsInRoot.height > resting.height)
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeByFrame()
        button.assertIsNotEnabled()
        assertEquals(resting.height, label.fetchSemanticsNode().boundsInRoot.height, 1f)
        button.performTouchInput { cancel() }
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(700)
        button.assertIsEnabled()
        assertEquals("重新启用时不能保留旧按压放大", resting.height, label.fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.mainClock.autoAdvance = true
        button.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun allMaterialModesKeepDarkLargeTextAndActionsReadable() {
        var mode by mutableStateOf(GlassMode.LIQUID)
        var replies = 0
        val selectedPost = post.copy(liked = true, bookmarkId = 37)
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                TopicScreen(TopicState(detail.copy(posts = listOf(selectedPost)), false), mode,
                    {}, {}, {}, {}, {}, {}, { replies++ }, {}, {}, {}, {})
            }
        }
        for (material in GlassMode.entries) {
            compose.runOnIdle { mode = material }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("topic-reply").assertIsDisplayed()
            compose.onNodeWithText("1 / 1").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            capture("topic-surface-${material.name.lowercase()}-dark-large.png")
            // Paragraph width includes unused constraint space; inspect every visible character.
            assertEquals("短操作标签应完整保持一行", 1, layout.lineCount)
            assertFalse("不能省略操作标签", layout.isLineEllipsized(0))
            for (index in layout.layoutInput.text.indices) {
                val bounds = layout.getBoundingBox(index)
                assertTrue("第${index + 1}个字不能被裁切：$bounds / ${layout.size}",
                    bounds.left >= -0.5f && bounds.top >= -0.5f &&
                        bounds.right <= layout.size.width + 0.5f && bounds.bottom <= layout.size.height + 0.5f)
            }
            assertTrue("深色前景必须可读", layouts.single().layoutInput.style.color.luminance() > 0.4f)
            compose.onNodeWithContentDescription("赞主帖").assertIsDisplayed()
            compose.onNodeWithContentDescription("Boost 主帖").assertIsDisplayed()
            compose.onNodeWithTag("topic-actions").assertHasNoClickAction()
            compose.onNodeWithTag("topic-reply").performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(GlassMode.entries.size, replies) }
    }

    private fun showSurface(enabled: () -> Boolean, click: () -> Unit) {
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        repeat(24) { index ->
                            Box(Modifier.fillMaxWidth().weight(1f)
                                .background(if (index % 2 == 0) Color(0xFFFFD56D) else Color(0xFF7BD6CA)))
                        }
                    }
                    LiquidButton(click, backdrop, GlassMode.LIQUID, Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                        enabled = enabled(), surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)) {
                        Text("Surface Liquid Button", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Let platform ripples settle; frozen Compose clocks still keep held presses visible.
        android.os.SystemClock.sleep(400)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
