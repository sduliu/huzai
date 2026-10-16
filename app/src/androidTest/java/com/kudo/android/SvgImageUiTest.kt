package com.kudo.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import com.kudo.android.data.DisplaySettings
import com.kudo.android.data.ThemeMode
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(coil3.annotation.DelicateCoilApi::class)
class SvgImageUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private val requested = CopyOnWriteArrayList<String>()

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        // Keep production fetchers/decoders, but never copy its terminal EngineInterceptor:
        // it captures the previous loader and would run before our fixture interceptor.
        loader = previous.newBuilder().components {
            add(Interceptor { chain ->
                val url = chain.request.data.toString()
                if (url.startsWith("https://svg.test/")) {
                    requested += url
                    chain.withRequest(chain.request.newBuilder().data(ByteBuffer.wrap(SvgImageDecoderTest.SvgSample.toByteArray())).build()).proceed()
                } else chain.proceed()
            })
            addFetcherFactories { previous.components.fetcherFactories }
            addDecoderFactories { previous.components.decoderFactories }
        }.build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After fun restore() { SingletonImageLoader.setUnsafe(previous); loader.shutdown() }

    @Test fun svgWithoutExtensionRendersInlineAndAsZoomableOriginal() {
        show("https://svg.test/thumbnail.bin", "https://svg.test/original.bin", ThemeMode.LIGHT)
        waitForInline()
        assertShapePixels("article-image-svg-fixture-0")
        capture("svg-inline-light")
        compose.onNodeWithTag("article-image-svg-fixture-0").performClick()
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
        compose.waitUntil(5000) { requested.contains("https://svg.test/original.bin") }
        compose.waitUntil(5000) { shapePixels("gallery-photo-0") }
        compose.onNodeWithText("加载失败，点击重试").assertDoesNotExist()
        capture("svg-gallery-light")
        val zoom = compose.onNodeWithTag("gallery-page-0").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "放大图片" }
        compose.runOnIdle { assertTrue(zoom.action()) }
        compose.onNodeWithTag("gallery-page-0").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已放大 2.0 倍"))
        compose.onNodeWithTag("gallery-page-0").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
        compose.onNodeWithTag("article-image-svg-fixture-0").assertIsDisplayed()
        compose.runOnIdle { assertTrue(requested.contains("https://svg.test/thumbnail.bin")) }
    }

    @Test fun actualTopicFirstImageAppearsInlineAndInGallery() {
        assumeTrue("实际CDN探针需显式启用", InstrumentationRegistry.getArguments().getString("svgLiveProbe") == "true")
        show(SvgImageDecoderTest.TopicSvgUrl, SvgImageDecoderTest.TopicSvgUrl, ThemeMode.DARK)
        waitForInline()
        assertTrue("实际帖图的两侧标志必须渲染", topicShapePixels("article-image-svg-fixture-0"))
        capture("svg-topic-3015506-inline")
        compose.onNodeWithTag("article-image-svg-fixture-0").performClick()
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
        compose.waitUntil(10000) { topicShapePixels("gallery-photo-0") }
        compose.onNodeWithText("图片加载失败").assertDoesNotExist()
        capture("svg-topic-3015506-gallery")
        compose.onNodeWithTag("gallery-page-0").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
    }

    private fun show(url: String, original: String, theme: ThemeMode) = compose.setContent {
        KudoTheme(DisplaySettings(theme = theme)) {
            ImageGalleryHost({ emptyList() }, {}) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("svg-fixture")) {
                    RichContent("""<div class="lightbox-wrapper"><a class="lightbox" href="$original"><img src="$url" alt="SVG图形" width="690" height="241"></a></div>""",
                        {}, {}, galleryKey = "svg-fixture")
                }
            }
        }
    }

    private fun waitForInline() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("article-image-svg-fixture-0-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("article-image-svg-fixture-0").assertIsDisplayed()
        compose.onNodeWithText("加载失败，点击重试").assertDoesNotExist()
    }

    private fun topicShapePixels(tag: String): Boolean {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var purple = 0
        var green = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.red > .2f && color.blue > .4f && color.green < .3f) purple++
            if (color.green > .6f && color.red < .3f && color.blue < .8f) green++
        }
        return purple > 100 && green > 100
    }

    private fun assertShapePixels(tag: String) = assertTrue("SVG的红蓝区域必须实际渲染", shapePixels(tag))

    private fun shapePixels(tag: String): Boolean {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var red = 0
        var blue = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.red > .9f && color.green < .1f && color.blue < .1f) red++
            if (color.blue > .9f && color.red < .1f && color.green < .1f) blue++
        }
        return red > 100 && blue > 100
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
