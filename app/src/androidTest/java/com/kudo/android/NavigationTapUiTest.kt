package com.kudo.android

import android.os.SystemClock
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.components.GlassNavigation
import com.kudo.android.ui.components.liquid.DampedDragAnimation
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationTapUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun highFrequencyTargetsReachTheLensBeforeTheFingerStops() {
        lateinit var animation: DampedDragAnimation
        compose.setContent {
            val scope = rememberCoroutineScope()
            animation = remember { DampedDragAnimation(scope, 0f, 0f..4f, .001f, 1f, 78f / 56f,
                {}, {}, { _, _ -> }) }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { animation.press() }
            for (frame in 1..24) {
                // Four input samples per display frame model 250Hz input on a 60Hz display.
                compose.runOnIdle {
                    repeat(4) { sample -> animation.updateValue((4 * (frame - 1) + sample + 1) / 24f) }
                }
                compose.mainClock.advanceTimeByFrame()
                SystemClock.sleep(16)
                compose.runOnIdle {
                    assertEquals("持续输入第 $frame 帧仍应跟随最新触点", frame / 6f, animation.value, .06f)
                }
            }
        } finally {
            compose.runOnIdle { animation.animateToValue(0f) }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun movingPointerKeepsLensAlignedAtDifferentInputRates() {
        var selected by mutableIntStateOf(0)
        var actions = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                GlassNavigation(selected, { selected = it }, { actions++ }, backdrop, GlassMode.LIQUID,
                    Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth().testTag("tracking-navigation"))
            }
        } }
        compose.waitForIdle()
        val navigation = compose.onNodeWithTag("tracking-navigation")
        val navigationBounds = navigation.fetchSemanticsNode().boundsInRoot
        val resting = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
        val tabWidth = resting.width
        val firstX = resting.center.x - navigationBounds.left
        compose.mainClock.autoAdvance = false
        var pointerDown = false
        fun frame() { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
        try {
            for (samplePeriod in listOf(4L, 8L, 16L)) {
                navigation.performTouchInput { down(Offset(firstX, centerY)) }
                pointerDown = true
                for (direction in listOf(1, -1)) {
                    for (step in 1..24) {
                        val progress = if (direction == 1) step / 24f else 1f - step / 24f
                        val targetX = firstX + tabWidth * 4f * progress
                        navigation.performTouchInput {
                            val startX = requireNotNull(currentPosition()).x
                            val samples = (16L / samplePeriod).toInt()
                            repeat(samples) { sample ->
                                moveTo(Offset(startX + (targetX - startX) * (sample + 1) / samples, centerY),
                                    delayMillis = samplePeriod)
                            }
                        }
                        frame()
                        val lens = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
                        assertEquals("${samplePeriod}ms 输入移动中镜片应跟手：方向 $direction / 帧 $step",
                            navigationBounds.left + targetX, lens.center.x, tabWidth * .15f)
                        compose.runOnIdle { assertEquals("松手前不能切页", 0, selected); assertEquals(0, actions) }
                    }
                }
                navigation.performTouchInput { cancel() }
                pointerDown = false
                repeat(60) { frame() }
            }
        } finally {
            if (pointerDown) navigation.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun pressingAnotherTabStillUsesAnElasticTransition() {
        var selected by mutableIntStateOf(0)
        compose.setContent { KudoTheme(DisplaySettings()) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                GlassNavigation(selected, { selected = it }, {}, backdrop, GlassMode.LIQUID,
                    Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth().testTag("press-navigation"))
            }
        } }
        compose.waitForIdle()
        val navigation = compose.onNodeWithTag("press-navigation")
        val navigationBounds = navigation.fetchSemanticsNode().boundsInRoot
        val initial = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
        val targetX = initial.center.x + initial.width * 4f
        compose.mainClock.autoAdvance = false
        try {
            navigation.performTouchInput { down(Offset(targetX - navigationBounds.left, centerY)) }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
            val pressed = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
            assertTrue("普通按下另一标签应有过渡，不能瞬移到目标", pressed.center.x < targetX - initial.width * .1f)
            compose.runOnIdle { assertEquals(0, selected) }
            navigation.performTouchInput { up() }
            repeat(100) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
            compose.runOnIdle { assertEquals(4, selected) }
            val settled = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
            assertEquals(targetX, settled.center.x, 1.5f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun releaseWhilePositionIsMovingClearsVelocityAndScale() {
        lateinit var animation: DampedDragAnimation
        compose.setContent {
            val scope = rememberCoroutineScope()
            animation = remember { DampedDragAnimation(scope, 0f, 0f..4f, .001f, 1f, 78f / 56f,
                {}, {}, { _, _ -> }) }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { animation.press(); animation.updateValue(4f) }
        repeat(6) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
        compose.runOnIdle { animation.release() }
        repeat(120) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
        compose.runOnIdle {
            assertEquals("松手后位置动画不能留下形变速度", 0f, animation.velocity, .001f)
            assertEquals(0f, animation.pressProgress, .001f)
            assertEquals(1f, animation.scaleX, .001f)
            assertEquals(1f, animation.scaleY, .001f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun directAndInterruptedTapsAlwaysRestoreTheRestingLens() {
        var selected by mutableIntStateOf(0)
        compose.setContent { KudoTheme(DisplaySettings()) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                GlassNavigation(selected, { selected = it }, {}, backdrop, GlassMode.LIQUID,
                    Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth().testTag("tap-navigation"))
            }
        } }
        compose.waitForIdle()
        val initial = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("tap-navigation")
        compose.mainClock.autoAdvance = false
        fun frames(count: Int) { repeat(count) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) } }
        navigation.performTouchInput { down(Offset(width * .1f, centerY)) }
        for (step in 1..20) {
            navigation.performTouchInput { moveTo(Offset(width * (.1f + .04f * step), centerY), delayMillis = 80) }
            frames(5)
            if (step == 10) capture("navigation-fixed-cross-publish.png")
        }
        navigation.performTouchInput { cancel() }
        frames(100)
        fun tap(index: Int, hold: Int) {
            navigation.performTouchInput { down(Offset(width * ((index + .5f) / 5f), centerY)) }
            frames(hold)
            navigation.performTouchInput { up() }
        }
        for ((index, hold) in listOf(4 to 1, 1 to 3, 3 to 10, 0 to 1, 4 to 2, 0 to 12, 4 to 1)) {
            tap(index, hold)
            frames(2)
        }
        frames(100)
        compose.waitForIdle()
        val lens = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot
        assertEquals(4, selected)
        assertEquals("点击停下后宽度不能残留速度拉伸", initial.width, lens.width, 1.5f)
        assertEquals("点击停下后高度不能残留速度压缩", initial.height, lens.height, 1.5f)
        assertEquals("上下留白恢复一致", lens.top - panel.top, panel.bottom - lens.bottom, 1.5f)
        assertEquals("末端留白与上下相同", panel.right - lens.right, lens.top - panel.top, 1.5f)
        capture("navigation-fixed-taps-resting.png")
        compose.mainClock.autoAdvance = true
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
