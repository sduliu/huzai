package com.kudo.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kudo.android.ui.theme.LogoYellow

@Composable
internal fun CommunityLoadingIndicator(modifier: Modifier = Modifier, strokeWidth: Dp = 2.dp, rotating: Boolean = true) {
    val rotation = if (rotating) {
        val transition = rememberInfiniteTransition(label = "community-loading")
        val angle by transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(1333, easing = LinearEasing)), label = "rotation")
        angle
    } else 0f
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = .5f)
    Canvas(modifier.size(26.dp).progressSemantics()) {
        val stroke = strokeWidth.toPx()
        val outlineStroke = stroke + 1.dp.toPx()
        val radius = (size.minDimension - outlineStroke) / 2f
        if (radius <= 0f) return@Canvas
        val topLeft = center - Offset(radius, radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        drawCircle(outline, radius, style = Stroke(outlineStroke))
        rotate(rotation) {
            // Butt ends keep the three joined arcs at exactly 3:4:3.
            drawArc(Color(0xFF1C1C1C), -90f, 108f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(Color(0xFFF0F0F0), 18f, 144f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(LogoYellow, 162f, 108f, false, topLeft, arcSize, style = Stroke(stroke))
        }
    }
}
