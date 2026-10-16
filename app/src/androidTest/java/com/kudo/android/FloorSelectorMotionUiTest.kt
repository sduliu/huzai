package com.kudo.android

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FloorSelectorMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continuousMovementTracksThePointerAtFourEightAndSixteenMillisecondInput() {
        var highest by mutableIntStateOf(2)
        var raw by mutableStateOf("1")
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK)) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().background(Color.Black).layerBackdrop(backdrop))
                FloorNavigationBar(raw, { raw = it }, highest, false, null, {}, backdrop, GlassMode.LIQUID,
                    Modifier.fillMaxWidth().padding(16.dp))
            }
        } }
        val slider = compose.onNodeWithTag("floor-slider", useUnmergedTree = true)
        val thumb = compose.onNodeWithTag("liquid-slider-thumb", useUnmergedTree = true)
        for (count in listOf(2, 3, 5, 100000)) for (period in listOf(4L, 8L, 16L)) {
            compose.runOnIdle { highest = count; raw = "1" }
            compose.waitForIdle()
            val bounds = slider.fetchSemanticsNode().boundsInRoot
            val resting = thumb.fetchSemanticsNode().boundsInRoot
            val startX = resting.center.x - bounds.left
            compose.mainClock.autoAdvance = false
            try {
                slider.performTouchInput { down(Offset(startX, centerY)) }
                for (direction in listOf(1, -1)) for (step in 1..12) {
                    val progress = .8f * if (direction == 1) step / 12f else 1f - step / 12f
                    val pointerX = startX + bounds.width * progress
                    slider.performTouchInput {
                        val previous = requireNotNull(currentPosition()).x
                        val samples = (16L / period).toInt()
                        repeat(samples) { sample ->
                            moveTo(Offset(previous + (pointerX - previous) * (sample + 1) / samples, centerY), delayMillis = period)
                        }
                    }
                    frame()
                    val expected = (bounds.left + bounds.width * progress)
                        .coerceIn(bounds.left + resting.width / 4f, bounds.right - resting.width / 4f)
                    // At the ends the semantic bounds are clipped by the bar; compare the optical
                    // centre only in the interior, while the other test covers endpoint finiteness.
                    if (progress > .1f && progress < .9f) assertEquals(
                        "$count 楼 / ${period}ms / 方向 $direction / 帧 $step 必须持续跟手",
                        expected, thumb.fetchSemanticsNode().boundsInRoot.center.x, bounds.width * .025f)
                }
            } finally {
                slider.performTouchInput { up() }
                compose.mainClock.autoAdvance = true
            }
        }
    }

    @Test fun inputAndTrackClicksFinishAtTheSelectedFloorAndSingleFloorRemainsFinite() {
        var highest by mutableIntStateOf(5)
        var raw by mutableStateOf("3")
        compose.setContent { KudoTheme(DisplaySettings()) {
            FloorNavigationBar(raw, { raw = it }, highest, false, null, {}, rememberLayerBackdrop(), GlassMode.TRANSLUCENT)
        } }
        val slider = compose.onNodeWithTag("floor-slider", useUnmergedTree = true)
        val thumb = compose.onNodeWithTag("liquid-slider-thumb", useUnmergedTree = true)
        slider.performTouchInput { click(Offset(width * .61f, centerY)) }
        compose.runOnIdle { assertEquals("3", raw) }
        val bounds = slider.fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.center.x, thumb.fetchSemanticsNode().boundsInRoot.center.x, 2f)
        compose.onNodeWithTag("floor-input", useUnmergedTree = true).performTextReplacement("4")
        assertEquals(bounds.left + bounds.width * .75f, thumb.fetchSemanticsNode().boundsInRoot.center.x, 2f)
        compose.runOnIdle { highest = 1; raw = "1" }
        slider.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals("1", raw) }
        assertTrue(thumb.fetchSemanticsNode().boundsInRoot.center.x.isFinite())
    }
    @Test fun aSmallMoveWithinOneFloorStillMovesTheRenderedSlider() {
        var raw by mutableStateOf("3")
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK)) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().background(Color.Black).layerBackdrop(backdrop))
                FloorNavigationBar(raw, { raw = it }, 5, false, null, {}, backdrop, GlassMode.LIQUID,
                    Modifier.fillMaxWidth().padding(16.dp))
            }
        } }
        val slider = compose.onNodeWithTag("floor-slider", useUnmergedTree = true)
        val bounds = slider.fetchSemanticsNode().boundsInRoot
        val movement = bounds.width / 4f * .24f
        compose.mainClock.autoAdvance = false
        try {
            slider.performTouchInput { down(center) }
            repeat(60) { frame() }
            val before = yellowEdge(slider)
            slider.performTouchInput { moveBy(Offset(movement, 0f), delayMillis = 4) }
            frame()
            compose.runOnIdle { assertEquals("楼层号还没有跨过取整边界", "3", raw) }
            val after = yellowEdge(slider)
            assertTrue("小于半楼的移动也必须在下一帧显示：before=$before after=$after movement=$movement",
                after - before > movement * .4f)
        } finally {
            slider.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
    }

    private fun yellowEdge(slider: SemanticsNodeInteraction): Int {
        val pixels = slider.captureToImage().toPixelMap()
        return (0 until pixels.width).lastOrNull { x ->
            (pixels.height / 2 - 2..pixels.height / 2 + 2).any { y ->
                val color = pixels[x, y]
                color.red > .85f && color.green in .4f.. .85f && color.blue < .3f
            }
        } ?: error("黄色轨道应可见")
    }

    private fun frame() { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
}
