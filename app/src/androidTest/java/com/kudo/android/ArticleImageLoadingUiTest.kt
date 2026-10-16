package com.kudo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.*
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.RichContent
import kotlinx.coroutines.CompletableDeferred
import okio.Buffer
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(coil3.annotation.DelicateCoilApi::class)
class ArticleImageLoadingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private val gate = CompletableDeferred<Unit>()
    private val requests = AtomicInteger()
    private var failFirst = false
    private var animated: Drawable? = null

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff46c598.toInt()) }
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        loader = ImageLoader.Builder(context).memoryCache(null).diskCache(null).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (data.toString() != "https://image-loading.test/picture.png") return null
                    return object : Fetcher {
                        override suspend fun fetch(): FetchResult {
                            val attempt = requests.incrementAndGet()
                            gate.await()
                            if (failFirst && attempt == 1) throw IOException("fixture offline")
                            animated?.let { return ImageFetchResult(it.asImage(shareable = false), false, DataSource.MEMORY) }
                            return SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY)
                        }
                    }
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
    }
    @After fun cleanup() { gate.complete(Unit); SingletonImageLoader.setUnsafe(previous); loader.shutdown() }
    private fun show() = compose.setContent { KudoTheme(DisplaySettings()) { Column {
        RichContent("""<img src="https://image-loading.test/picture.png" width="800" height="600">""", {}, {})
    } } }
    @Test fun loadingStaysVisibleUntilTheImageIsDecoded() {
        show()
        compose.waitUntil(5000) { requests.get() == 1 }
        compose.onNodeWithTag("article-image-article-0-loading").assertIsDisplayed()
        gate.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("article-image-article-0-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("加载失败，点击重试").assertDoesNotExist()
    }
    @Test fun failureStopsLoadingAndRetryActuallyFetchesAgain() {
        failFirst = true
        gate.complete(Unit)
        show()
        compose.onNodeWithText("加载失败，点击重试").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { requests.get() == 2 && compose.onAllNodesWithText("加载失败，点击重试").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("article-image-article-0-loading").assertDoesNotExist()
    }
    @Test fun newlyLoadedAnimationIsNotStoppedWhileItIsVisible() {
        val stops = AtomicInteger()
        val drawable = object : Drawable(), Animatable {
            private var running = false
            override fun draw(canvas: Canvas) { canvas.drawColor(android.graphics.Color.GREEN) }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
            override fun getIntrinsicWidth() = 80
            override fun getIntrinsicHeight() = 60
            override fun start() { running = true }
            override fun stop() { stops.incrementAndGet(); running = false }
            override fun isRunning() = running
        }
        animated = drawable
        var shown by mutableStateOf(true)
        try {
            compose.setContent { KudoTheme(DisplaySettings()) { Column {
                if (shown) RichContent("""<img src="https://image-loading.test/picture.png" width="800" height="600">""", {}, {})
            } } }
            compose.waitUntil(5000) { requests.get() == 1 }
            gate.complete(Unit)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("article-image-article-0-loading").fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle {
                Assert.assertEquals("前台新动图不能被旧加载状态提前停止", 0, stops.get())
                Assert.assertTrue(drawable.isRunning)
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { Assert.assertFalse(drawable.isRunning) }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(4000) { drawable.isRunning }
            compose.runOnIdle { shown = false }
            compose.waitUntil(4000) { !drawable.isRunning }
        } finally { compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }
    }
}
