package com.kudo.android

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.components.GlassNavigation
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.theme.LogoYellow
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NavigationMaterialUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restingSelectionHasVisibleGrayWithoutDimmingBrandIcons() {
        var mode by mutableStateOf(GlassMode.LIQUID)
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                    GlassNavigation(0, {}, {}, backdrop, mode,
                        Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        for (material in GlassMode.entries) {
            compose.runOnIdle { mode = material }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val gray = pixels[bounds.center.x.toInt(), (bounds.top + bounds.height * 0.08f).toInt()]
            val brightness = (gray.red + gray.green + gray.blue) / 3f
            val spread = maxOf(gray.red, gray.green, gray.blue) - minOf(gray.red, gray.green, gray.blue)
            capture("navigation-gray-${material.name.lowercase()}.png")
            // A neutral but nearly white selection passed the old hue-only check.
            assertTrue("白底选中面需有接近参考图的灰度对比：$material / $gray", brightness in 0.85f..0.92f)
            assertTrue("选中灰面不能染上品牌色：$gray", spread < 0.02f)
            var brandPixels = 0
            for (y in bounds.top.toInt() until bounds.center.y.toInt()) {
                for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    val color = pixels[x, y]
                    if (kotlin.math.abs(color.red - LogoYellow.red) < 0.02f &&
                        kotlin.math.abs(color.green - LogoYellow.green) < 0.02f &&
                        kotlin.math.abs(color.blue - LogoYellow.blue) < 0.02f) brandPixels++
                }
            }
            assertTrue("灰底不能压暗前景的 LogoYellow：$material / $brandPixels", brandPixels > 30)
        }
    }

    @Test fun draggedLensKeepsBothRefractedRimsVisible() {
        assumeTrue("完整折射需要 Android 13 及以上", Build.VERSION.SDK_INT >= 33)
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        drawRect(Brush.horizontalGradient(listOf(Color(0xFF16BBAA), Color(0xFF147EDE))))
                    }
                    GlassNavigation(0, {}, {}, backdrop, GlassMode.LIQUID,
                        Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        val navigation = compose.onNodeWithTag("navigation")
        compose.mainClock.autoAdvance = false
        navigation.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.3f, centerY), delayMillis = 300)
        }
        repeat(60) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
        compose.waitForIdle()
        val lens = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(directory, "navigation-rims-root.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val pixels = image.toPixelMap()
        val centerX = lens.center.x.toInt()
        val band = (lens.height * 0.17f).toInt()
        fun rimProfile(top: Boolean): List<Float> {
            val start = if (top) lens.top.toInt() + 2 else lens.bottom.toInt() - band
            return (start until start + band - 2).map { pixels[centerX, it].red }
        }
        val upper = rimProfile(true)
        val lower = rimProfile(false)
        val backgroundRed = pixels[centerX, (lens.top - with(compose.density) { 18.dp.toPx() }).toInt()].red
        File(directory, "navigation-rims-bounds.txt").writeText(
            "lens=$lens\npanel=$panel\nupper=${upper.min()}..${upper.max()}\nlower=${lower.min()}..${lower.max()}\n")
        navigation.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        for ((edge, values) in listOf("上缘" to upper, "下缘" to lower)) {
            assertTrue("$edge 需要可辨识的内凹折射带：$values", values.max() - values.min() > 0.22f)
            // Both folds must reach the darker external backdrop, not just contain a yellow glyph fragment.
            assertTrue("$edge 应折回底栏外的背景颜色：$values", values.min() < backgroundRed + 0.12f)
        }
    }

    @Test fun restingSelectionKeepsUniformInsetAtBothEnds() {
        var selected by mutableIntStateOf(0)
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.LIGHT))
        var mode by mutableStateOf(GlassMode.LIQUID)
        var narrow by mutableStateOf(false)
        compose.setContent {
            KudoTheme(settings) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                    GlassNavigation(selected, { selected = it }, {}, backdrop, mode,
                        Modifier.align(Alignment.BottomCenter).padding(20.dp)
                            .then(if (narrow) Modifier.width(280.dp) else Modifier.fillMaxWidth()).testTag("navigation"))
                }
            }
        }
        for (small in listOf(false, true)) for (material in GlassMode.entries) for (end in listOf(0, 4)) {
            compose.runOnIdle {
                narrow = small; mode = material; selected = end
                settings = DisplaySettings(theme = if (small) ThemeMode.DARK else ThemeMode.LIGHT, fontScale = if (small) 1.3f else 1f)
            }
            compose.waitForIdle()
            val panel = compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot
            val lens = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
            val top = lens.top - panel.top
            val bottom = panel.bottom - lens.bottom
            val outside = if (end == 0) lens.left - panel.left else panel.right - lens.right
            assertTrue("静止灰面必须完整留在底栏内：$material / $end / $panel / $lens", minOf(top, bottom, outside) > 0f)
            assertEquals("上下留白应相同", top, bottom, 1.5f)
            assertEquals("首尾外侧留白应与上下相同", top, outside, 1.5f)
        }
        capture("navigation-insets-dark-large.png")
    }

    @Test fun glassBarKeepsBackgroundVisibleAtRestAndWhilePressed() {
        var mode by mutableStateOf(GlassMode.LIQUID)
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Black))
                    GlassNavigation(0, {}, {}, backdrop, mode,
                        Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        for (material in listOf(GlassMode.LIQUID, GlassMode.BLUR)) {
            compose.runOnIdle { mode = material }
            compose.waitForIdle()
            val resting = panelWhitening()
            capture("navigation-transparency-${material.name.lowercase()}-resting.png")
            assertTrue("黑色背景下底栏不应覆盖过强白面：$material / $resting", resting < 0.30f)
            val navigation = compose.onNodeWithTag("navigation")
            val restingHeight = compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot.height
            compose.mainClock.autoAdvance = false
            navigation.performTouchInput {
                down(Offset(width * 0.1f, centerY))
                moveTo(Offset(width * 0.3f, centerY), delayMillis = 300)
            }
            compose.mainClock.advanceTimeBy(650)
            compose.waitForIdle()
            assertTrue("透色采样必须在导航真实放大后进行",
                compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot.height > restingHeight)
            val pressed = panelWhitening()
            capture("navigation-transparency-${material.name.lowercase()}-pressed.png")
            // The official touch highlight adds light; it must not become an opaque white panel.
            assertTrue("按下后仍需看见背景：$material / $pressed", pressed < 0.45f)
            navigation.performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(900)
            compose.waitForIdle()
            assertEquals("取消后恢复原材质", resting, panelWhitening(), 0.03f)
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun stoppedDragLensKeepsItsProtrusion() {
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                    GlassNavigation(0, {}, {}, backdrop, GlassMode.LIQUID,
                        Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        val navigation = compose.onNodeWithTag("navigation")
        compose.mainClock.autoAdvance = false
        navigation.performTouchInput { down(Offset(width * 0.1f, centerY)) }
        for (fraction in listOf(0.3f, 0.7f, 0.9f, 0.1f)) {
            navigation.performTouchInput { moveTo(Offset(width * fraction, centerY), delayMillis = 300) }
            // The upstream velocity tracker uses wall time, so settling needs real frames.
            repeat(60) {
                compose.mainClock.advanceTimeByFrame()
                SystemClock.sleep(16)
            }
            compose.waitForIdle()
            val lens = compose.onNodeWithTag("navigation-selection").fetchSemanticsNode().boundsInRoot
            val panel = compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot
            assertTrue("手指停下后镜片不能被速度形变压进底栏：$fraction / $lens / $panel", lens.height > panel.height)
            assertTrue("停下后仍只略微凸出：$fraction / $lens / $panel", lens.height < panel.height * 1.15f)
        }
        navigation.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
    }

    private fun panelWhitening(): Float {
        val bounds = compose.onNodeWithTag("navigation-surface").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = (bounds.left + bounds.width * 0.4f).toInt()
        val y = bounds.center.y.toInt()
        val color = pixels[x, y]
        return (color.red + color.green + color.blue) / 3f
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
