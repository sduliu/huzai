package com.kudo.android.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kudo.android.data.DisplaySettings
import com.kudo.android.data.ThemeMode

val LogoYellow = Color(0xFFFFB003)
val LikeRed = Color(0xFFFA6C8D)
val Ink = Color(0xFF202124)
val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }
private val Light = lightColorScheme(
    primary = Color(0xFF805600), onPrimary = Color.White, primaryContainer = LogoYellow, onPrimaryContainer = Ink,
    secondary = Color(0xFF6C6556), background = Color(0xFFF4F4F7), onBackground = Ink,
    surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF2F2F6), onSurfaceVariant = Color(0xFF77777E),
    outline = Color(0xFFC3C3CA), outlineVariant = Color(0xFFEEEEF2), surfaceContainer = Color.White,
)
private val Dark = darkColorScheme(
    primary = LogoYellow, onPrimary = Ink, primaryContainer = Color(0xFF59410E), onPrimaryContainer = Color(0xFFFFD781),
    background = Color(0xFF101113), onBackground = Color(0xFFF1F1F3), surface = Color(0xFF1C1D20),
    onSurface = Color(0xFFF1F1F3), surfaceVariant = Color(0xFF28292E), onSurfaceVariant = Color(0xFFABABB4),
    outline = Color(0xFF55565D), outlineVariant = Color(0xFF303138), surfaceContainer = Color(0xFF1C1D20),
)
private val KudoTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun KudoTheme(settings: DisplaySettings, darkStatusIcons: Boolean? = null, content: @Composable () -> Unit) {
    val dark = when (settings.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val colors = if (dark) Dark else Light
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            it.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = darkStatusIcons ?: !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * settings.fontScale), LocalDisplaySettings provides settings) {
        MaterialTheme(colorScheme = colors, typography = KudoTypography) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}
