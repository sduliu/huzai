package com.kudo.android.ui.topic

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.util.lerp

internal data class GalleryImageFrame(
    val content: Rect, val container: Rect, val visible: Rect, val radius: Float = 0f,
) {
    fun interpolate(to: GalleryImageFrame, progress: Float) = GalleryImageFrame(
        lerp(content, to.content, progress), lerp(container, to.container, progress),
        lerp(visible, to.visible, progress), lerp(radius, to.radius, progress),
    )
}

internal fun GalleryAnchorSnapshot.imageFrame(windowOrigin: Offset, ratio: Float?): GalleryImageFrame {
    val rect = bounds.translate(-windowOrigin)
    val aspect = ratio?.takeIf { it.isFinite() && it > 0 } ?: (rect.width / rect.height)
    val intrinsic = Size(aspect, 1f)
    val scale = contentScale.computeScaleFactor(intrinsic, rect.size)
    val size = Size(intrinsic.width * scale.scaleX, intrinsic.height * scale.scaleY)
    val content = Rect(rect.center - Offset(size.width / 2, size.height / 2), size)
    return GalleryImageFrame(content, rect, visibleBounds.translate(-windowOrigin), cornerRadius)
}

internal fun GalleryTransformState.imageFrame(): GalleryImageFrame {
    val viewport = Rect(Offset.Zero, viewport)
    return GalleryImageFrame(displayedBounds(), viewport, viewport)
}

internal data class GalleryFlight(
    val from: GalleryImageFrame, val to: GalleryImageFrame, val painter: Painter?,
    val fromScrim: Float, val toScrim: Float, val closing: Boolean,
)

internal fun galleryMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

@Composable
internal fun GalleryTransitionLayer(flight: GalleryFlight, progress: Float) {
    val painter = flight.painter ?: return
    val frame = flight.from.interpolate(flight.to, progress)
    Canvas(Modifier.fillMaxSize().testTag("gallery-transition")) {
        if (frame.content.isEmpty || frame.container.isEmpty || frame.visible.isEmpty) return@Canvas
        val shape = Path().apply { addRoundRect(RoundRect(frame.container, CornerRadius(frame.radius))) }
        clipRect(frame.visible.left, frame.visible.top, frame.visible.right, frame.visible.bottom) {
            clipPath(shape) {
                translate(frame.content.left, frame.content.top) { with(painter) { draw(frame.content.size) } }
            }
        }
    }
}
