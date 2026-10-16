package com.kudo.android

import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.toArgb
import com.kudo.android.ui.components.createKudoToastView
import com.kudo.android.ui.components.createKudoToast
import com.kudo.android.ui.components.showKudoToast
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import java.io.File

@RunWith(AndroidJUnit4::class)
class KudoToastUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun toastUsesSharedCoolapkStyle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = createKudoToastView(context, "已为你加载20条新内容")
        val density = context.resources.displayMetrics.density
        val drawable = view.background as GradientDrawable

        assertEquals("已为你加载20条新内容", view.text.toString())
        assertEquals(Ink.toArgb(), view.currentTextColor)
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics), view.textSize, 0.01f)
        assertEquals((22f * density).roundToInt(), view.paddingLeft)
        assertEquals((12f * density).roundToInt(), view.paddingTop)
        assertEquals((24f * density).roundToInt().toFloat(), drawable.cornerRadius, 0.01f)
        assertTrue(drawable.color?.defaultColor == LogoYellow.toArgb())
        lateinit var toast: android.widget.Toast
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            toast = createKudoToast(context, "提示")
        }
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, toast.gravity)
        assertEquals(0, toast.xOffset)
        assertEquals((96f * density).roundToInt(), toast.yOffset)
    }

    @Test fun foregroundToastActuallyDisplaysAtTheTopAndKeepsAppInstalled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.runOnIdle { showKudoToast(compose.activity, "已为你加载20条新内容") }
        val deadline = android.os.SystemClock.uptimeMillis() + 2000
        var screenshot: Bitmap? = null
        var yellowRows = emptyList<Int>()
        while (yellowRows.isEmpty() && android.os.SystemClock.uptimeMillis() < deadline) {
            screenshot?.recycle()
            screenshot = instrumentation.uiAutomation.takeScreenshot()
            yellowRows = (0 until screenshot.height / 3).filter { y ->
                (screenshot.width / 4 until screenshot.width * 3 / 4).count { x ->
                    screenshot.getPixel(x, y) == LogoYellow.toArgb()
                } > screenshot.width / 5
            }
            if (yellowRows.isEmpty()) android.os.SystemClock.sleep(50)
        }
        assertTrue("前台系统 Toast 必须在屏幕上方实际显示黄色胶囊", yellowRows.isNotEmpty())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(folder, "yellow-toast.png").outputStream().use { screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot?.recycle()
    }
}
