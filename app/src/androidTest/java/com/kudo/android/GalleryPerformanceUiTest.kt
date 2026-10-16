package com.kudo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.*
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import okio.Buffer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in wall-clock rendering probe. Timings are evidence, not a device-independent FPS assertion. */
@OptIn(coil3.annotation.DelicateCoilApi::class)
class GalleryPerformanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun largeLongImageFrameTimings() = measure(false)
    @Test fun largeWideImageFrameTimings() = measure(true)
    private fun measure(wide: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("galleryPerformance") == "true")
        val context = instrumentation.targetContext
        val previous = SingletonImageLoader.get(context)
        val width = if (wide) 12000 else 1600
        val height = if (wide) 1600 else 12000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (y in 0 until height step 80) for (x in 0 until width step 80) {
            paint.color = if ((x / 80 + y / 80) % 2 == 0) 0xff46c598.toInt() else 0xff202020.toInt()
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + 80).toFloat(), (y + 80).toFloat(), paint)
        }
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        val decoded = AtomicBoolean()
        val loader = ImageLoader.Builder(context).components { add(object : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                if (data.toString() != "https://gallery-performance.test/image.png") return null
                return object : Fetcher { override suspend fun fetch() = SourceFetchResult(
                    ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY) }
            }
        }) }.eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { decoded.set(true) }
        }).build()
        val thread = HandlerThread("gallery-frame-probe").apply { start() }
        val frames = CopyOnWriteArrayList<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 1L) frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        }
        SingletonImageLoader.setUnsafe(loader)
        val state = GalleryTransformState(width.toFloat() / height)
        try {
            compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
            compose.setContent { KudoTheme(DisplaySettings()) {
                GalleryImagePage(ArticleBlock.Image("https://gallery-performance.test/image.png", "测试大图", width.toFloat() / height),
                    0, state, true, true, {}, {})
            } }
            compose.waitUntil(10000) { decoded.get() }
            compose.runOnIdle { if (wide) state.accessibleZoom() }
            val bounds = compose.onNodeWithTag("gallery-page-0").fetchSemanticsNode().boundsInRoot
            instrumentation.runOnMainSync { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
            repeat(6) { pass ->
                val sign = if (pass % 2 == 0) -1 else 1
                val start = bounds.center
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, fraction: Float) = instrumentation.runOnMainSync {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                        start.x + if (wide) sign * bounds.width * 0.35f * fraction else 0f,
                        start.y + if (!wide) sign * bounds.height * 0.3f * fraction else 0f, 0)
                    compose.activity.dispatchTouchEvent(event)
                    event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, 0f)
                repeat(24) { index -> SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE, (index + 1) / 24f) }
                send(MotionEvent.ACTION_UP, 1f)
            }
            SystemClock.sleep(160)
            assertTrue("FrameMetrics must report actual rendered frames", frames.size >= 20)
            val sorted = frames.sorted()
            val refresh = compose.activity.window.decorView.display.refreshRate
            val report = JSONObject().put("device", android.os.Build.MODEL).put("refreshHz", refresh)
                .put("frames", frames.size).put("p50Ms", sorted[sorted.size / 2] / 1e6)
                .put("p95Ms", sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1e6)
                .put("maxMs", sorted.last() / 1e6).put("overBudget", frames.count { it > 1e9 / refresh })
            File(context.filesDir, if (wide) "gallery-wide-frames.json" else "gallery-long-frames.json").writeText(report.toString(2))
        } finally {
            instrumentation.runOnMainSync { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
            SingletonImageLoader.setUnsafe(previous)
            loader.shutdown()
        }
    }
}
