package com.kudo.android.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow
import kotlin.math.roundToInt

private const val TOAST_TEXT_SIZE_SP = 16f
private const val TOAST_HORIZONTAL_PADDING_DP = 22f
private const val TOAST_VERTICAL_PADDING_DP = 12f
private const val TOAST_CORNER_RADIUS_DP = 24f
private const val TOAST_MAX_WIDTH_DP = 320f
private const val TOAST_TOP_OFFSET_DP = 96f
private val toastHandler = Handler(Looper.getMainLooper())
private var activeToast: Toast? = null

private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun createKudoToastView(context: Context, text: String): TextView {
    val view = TextView(context).apply {
        this.text = text
        setTextColor(Ink.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TOAST_TEXT_SIZE_SP)
        gravity = Gravity.CENTER
        includeFontPadding = true
        setPadding(
            context.dp(TOAST_HORIZONTAL_PADDING_DP), context.dp(TOAST_VERTICAL_PADDING_DP),
            context.dp(TOAST_HORIZONTAL_PADDING_DP), context.dp(TOAST_VERTICAL_PADDING_DP),
        )
        maxWidth = minOf(context.dp(TOAST_MAX_WIDTH_DP),
            (context.resources.displayMetrics.widthPixels - context.dp(32f)).coerceAtLeast(1))
        background = GradientDrawable().apply {
            setColor(LogoYellow.toArgb())
            cornerRadius = context.dp(TOAST_CORNER_RADIUS_DP).toFloat()
        }
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    return view
}

@Suppress("DEPRECATION") // A custom view is needed for the app's foreground Toast colors and geometry.
internal fun createKudoToast(context: Context, text: String): Toast = Toast(context).apply {
        duration = Toast.LENGTH_SHORT
        view = createKudoToastView(context, text)
        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, context.dp(TOAST_TOP_OFFSET_DP))
}

/** All short app messages share one foreground system Toast; the newest replaces the previous one. */
internal fun showKudoToast(context: Context, text: String) {
    if (text.isBlank()) return
    val appContext = context.applicationContext
    fun show() {
        activeToast?.cancel()
        activeToast = createKudoToast(appContext, text).also { it.show() }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) show() else toastHandler.post { show() }
}
