package com.kudo.android

import android.graphics.drawable.Animatable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kudo.android.media.communityAnimations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class AvatarAnimationDecoderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun animatedAvifRendersAllFramesInsteadOfTheStillCover() = runBlocking {
        val drawable = load("three-frame.avif")
        assertTrue("动画 AVIF 不能只显示静态封面", drawable is Animatable)
        assertFrames("three-frame.avif", drawable)
    }

    @Test fun apngIsNotFlattenedByTheApplicationImageLoader() = runBlocking {
        val drawable = load("three-frame.png")
        assertTrue("APNG 不能只显示静态首帧", drawable is Animatable)
    }

    @Test fun applicationImageLoaderSurvivesActivityRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = SingletonImageLoader.get(context)
        compose.activityRule.scenario.recreate()
        assertSame("重建页面必须复用应用图片加载器", loader, SingletonImageLoader.get(context))
        assertTrue("重建后仍应解码 APNG 动画", load("three-frame.png") is Animatable)
    }

    @Test fun apngGifAndWebPActuallyRenderAllThreeFrames() = runBlocking {
        for (name in listOf("three-frame.png", "three-frame.gif", "three-frame.webp")) assertFrames(name, load(name))
    }

    @Test fun legacyWebPDecoderRendersAnimationAndStaticPngRemainsStatic() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = ImageLoader.Builder(context).components { communityAnimations(legacyWebP = true) }.build()
        try {
            assertFrames("legacy-webp", load("three-frame.webp", loader))
            assertFalse(load("static.png", loader) is Animatable)
        } finally { loader.shutdown() }
    }

    private suspend fun load(name: String, loader: ImageLoader? = null): Drawable {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = instrumentation.context.assets.open("avatars/$name").use { it.readBytes() }
        val result = (loader ?: SingletonImageLoader.get(context)).execute(
            ImageRequest.Builder(context).data(ByteBuffer.wrap(bytes)).size(32).build())
        assertTrue("$name 应当成功解码：$result", result is SuccessResult)
        return (result as SuccessResult).image.asDrawable(context.resources)
    }

    private fun assertFrames(name: String, drawable: Drawable) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue("$name 应当可播放", drawable is Animatable)
        val animation = drawable as Animatable
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())
        val colors = mutableSetOf<Int>()
        instrumentation.runOnMainSync {
            drawable.setBounds(0, 0, 32, 32)
            drawable.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) = Unit
                override fun scheduleDrawable(who: Drawable, what: Runnable, at: Long) { handler.postAtTime(what, who, at) }
                override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what, who) }
            }
            animation.start()
        }
        try {
            for (attempt in 0 until 25) {
                Thread.sleep(100)
                instrumentation.runOnMainSync {
                    bitmap.eraseColor(Color.TRANSPARENT)
                    drawable.draw(Canvas(bitmap))
                    val pixel = bitmap.getPixel(16, 16)
                    if (Color.alpha(pixel) > 200) {
                        val channels = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                        colors += channels.indices.maxBy { channels[it] }
                    }
                }
                if (colors.size == 3) break
            }
            assertEquals("$name 必须实际渲染红、绿、蓝三帧", setOf(0, 1, 2), colors)
        } finally {
            instrumentation.runOnMainSync {
                animation.stop()
                drawable.callback = null
                handler.removeCallbacksAndMessages(drawable)
            }
            // APNG4Android releases its render listener on the decoder's worker thread.
            for (attempt in 0 until 50) {
                if (!animation.isRunning) break
                Thread.sleep(20)
            }
            bitmap.recycle()
            assertFalse("$name 停止后必须退出解码循环", animation.isRunning)
        }
    }
}
