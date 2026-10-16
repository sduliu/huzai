package com.kudo.android

import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.*
import com.kudo.android.media.communityAnimations
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.RichContent
import org.junit.*
import org.junit.Assert.*
import okio.Buffer
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ArticleImageSizingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Test fun articleAvifActuallyChangesFramesAndStopsOnBackgroundAndRemoval() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previous = SingletonImageLoader.get(context)
        val animation = AtomicReference<Animatable?>()
        val loader = ImageLoader.Builder(context).components {
            communityAnimations()
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://article.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch() = SourceFetchResult(ImageSource(Buffer().write(
                            instrumentation.context.assets.open("avatars/three-frame.avif").use { it.readBytes() }), options.fileSystem), "image/avif", DataSource.MEMORY)
                    }
                }
            })
        }.eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                animation.set(result.image.asDrawable(context.resources) as? Animatable)
            }
        }).build()
        SingletonImageLoader.setUnsafe(loader)
        var shown by mutableStateOf(true)
        try {
            compose.setContent { KudoTheme(DisplaySettings()) { Column(Modifier.padding(16.dp)) {
                if (shown) RichContent("""<img class="animated" src="https://article.test/animated.avif" width="153" height="153">""", {}, {})
            } } }
            compose.waitUntil(5000) { animation.get()?.isRunning == true }
            val colors = mutableSetOf<Int>()
            repeat(18) {
                SystemClock.sleep(80)
                val pixels = compose.onNodeWithTag("article-image-article-0").captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, pixels.height / 2]
                if (color.alpha > 0.9f) {
                    val channels = listOf(color.red, color.green, color.blue)
                    colors += channels.indices.maxBy { channels[it] }
                }
            }
            assertEquals("正文必须真正播放AVIF三帧", setOf(0, 1, 2), colors)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "article-avif-inline.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.runOnMainSync { assertFalse(animation.get()!!.isRunning) }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(4000) { animation.get()?.isRunning == true }
            compose.runOnIdle { shown = false }
            compose.waitUntil(4000) { animation.get()?.isRunning == false }
        } finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.runOnMainSync { animation.get()?.stop() }
            SingletonImageLoader.setUnsafe(previous)
            loader.shutdown()
        }
    }
    @Test fun smallAnimatedAttachmentUsesItsHtmlDisplayWidthInsteadOfFillingThePhone() {
        var density = 1f
        var opened = ""
        compose.setContent { KudoTheme(DisplaySettings()) {
            density = LocalDensity.current.density
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                RichContent("""<p>小图<img class="animated" src="https://article.test/small.avif" width="153" height="153"></p>""", { opened = it }, {})
            }
        } }
        val node = compose.onNodeWithTag("article-image-article-0")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals("小图不能铺满屏幕", 153f, bounds.width / density, 1f)
        assertEquals(153f, bounds.height / density, 1f)
        node.performClick()
        compose.runOnIdle { assertEquals("https://article.test/small.avif", opened) }
    }
}
