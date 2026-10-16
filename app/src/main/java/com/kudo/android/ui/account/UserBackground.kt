package com.kudo.android.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.kudo.android.ui.components.AvatarImage
import com.kudo.android.ui.components.mediaUrl

@Composable
internal fun UserBackground(url: String, fallback: Color, modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Boolean) -> Unit) {
    val source = remember(url) { mediaUrl(url) }
    var loaded by remember(source) { mutableStateOf(false) }
    BoxWithConstraints(modifier.background(fallback)) {
        val pixels = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        if (source != null) AvatarImage(source, pixels, true, Modifier.matchParentSize().testTag("user-background-image"),
            onLoaded = { loaded = it }, placeholder = {})
        if (loaded) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.68f)))))
        content(loaded)
    }
}
