package com.kudo.android.ui.topic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun fitGalleryImage(viewport: Size, ratio: Float?): Rect {
    if (viewport.isEmpty()) return Rect.Zero
    val aspect = ratio?.takeIf { it.isFinite() && it > 0 } ?: (viewport.width / viewport.height)
    val width = min(viewport.width, viewport.height * aspect)
    val height = width / aspect
    return Rect(Offset((viewport.width - width) / 2, (viewport.height - height) / 2), Size(width, height))
}

@Stable
internal class GalleryTransformState(initialRatio: Float? = null) {
    var viewport by mutableStateOf(Size.Zero)
        private set
    var ratio by mutableStateOf(initialRatio)
        private set
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var dismissOffset by mutableStateOf(Offset.Zero)
        internal set
    var dragging by mutableStateOf(false)
        internal set
    private var userTransformed = false
    val fitBounds get() = fitGalleryImage(viewport, ratio)
    val longImage get() = ratio?.let { it.isFinite() && it > 0 && it <= 1f / 3f } == true
    val defaultScale get() = if (longImage && fitBounds.width > 0) viewport.width / fitBounds.width else 1f
    val zoomed by derivedStateOf { scale > defaultScale + 0.001f }
    val zoomDescription by derivedStateOf { if (zoomed) "已放大 ${"%.1f".format(scale)} 倍" else "可双指缩放，下拉或点按关闭" }
    val maxScale get() = max(defaultScale * 2f,
        if (fitBounds.width > 0) (viewport.width / fitBounds.width * 2f).coerceIn(5f, 20f) else 5f)
    val dismissFraction get() = if (viewport.height > 0) (dismissOffset.y / viewport.height).coerceIn(0f, 1f) else 0f
    val dismissScale get() = 1f - dismissFraction * 0.45f
    val scrimAlpha get() = (1f - dismissFraction * 1.4f).coerceIn(0f, 1f)

    fun resize(size: Size) {
        val atDefault = abs(scale - defaultScale) < 0.001f
        viewport = size
        if (!userTransformed) resetZoom()
        else { if (atDefault) scale = defaultScale; offset = constrained(offset, scale) }
    }
    fun decodedRatio(value: Float) {
        if (value.isFinite() && value > 0) {
            ratio = value
            if (!userTransformed) resetZoom() else offset = constrained(offset, scale)
        }
    }

    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        if (viewport.isEmpty() || !zoom.isFinite() || zoom <= 0 ||
            !centroid.x.isFinite() || !centroid.y.isFinite() || !pan.x.isFinite() || !pan.y.isFinite()) return
        val next = (scale * zoom).coerceIn(1f, maxScale)
        val pivot = centroid - Offset(viewport.width / 2, viewport.height / 2)
        offset = constrained((offset - pivot) * (next / scale) + pivot + pan, next)
        scale = next
        userTransformed = true
        if (scale <= 1.001f) { scale = 1f; offset = Offset.Zero }
    }

    private fun constrained(value: Offset, zoom: Float): Offset {
        val image = fitBounds
        val horizontal = max(0f, (image.width * zoom - viewport.width) / 2)
        val vertical = max(0f, (image.height * zoom - viewport.height) / 2)
        return Offset(value.x.coerceIn(-horizontal, horizontal), value.y.coerceIn(-vertical, vertical))
    }

    fun resetZoom() {
        scale = defaultScale
        offset = Offset(0f, max(0f, (fitBounds.height * scale - viewport.height) / 2f))
        userTransformed = false
    }
    fun canPanVertically(deltaY: Float): Boolean {
        val bounds = displayedBounds()
        return if (deltaY < 0) bounds.bottom > viewport.height + 0.5f else deltaY > 0 && bounds.top < -0.5f
    }
    fun accessibleZoom() { transform(Offset(viewport.width / 2, viewport.height / 2), Offset.Zero, 2f) }
    suspend fun fling(velocity: Offset, decay: DecayAnimationSpec<Float>) = coroutineScope {
        suspend fun axis(horizontal: Boolean, speed: Float) {
            if (!speed.isFinite() || abs(speed) < 1f) return
            val initial = if (horizontal) offset.x else offset.y
            androidx.compose.animation.core.AnimationState(initial, speed).animateDecay(decay) {
                val requested = if (horizontal) offset.copy(x = value) else offset.copy(y = value)
                val bounded = constrained(requested, scale)
                offset = bounded
                if (abs((if (horizontal) bounded.x else bounded.y) - value) > 0.01f) cancelAnimation()
            }
        }
        launch { axis(true, velocity.x) }
        launch { axis(false, velocity.y) }
    }
    fun dragBy(delta: Offset) { dismissOffset = (dismissOffset + delta).let { Offset(it.x, max(0f, it.y)) } }
    fun shouldDismiss(velocityY: Float, flingVelocity: Float): Boolean =
        dismissFraction >= 0.18f || (dismissFraction >= 0.04f && velocityY >= flingVelocity)

    suspend fun restoreDismiss() {
        Animatable(dismissOffset, Offset.VectorConverter).animateTo(Offset.Zero, spring(dampingRatio = 0.9f, stiffness = 500f)) {
            dismissOffset = value
        }
    }

    fun displayedBounds(): Rect {
        val image = fitBounds
        val center = Offset(viewport.width / 2, viewport.height / 2)
        val zoom = scale * dismissScale
        return Rect((image.topLeft - center) * zoom + center + offset + dismissOffset,
            (image.bottomRight - center) * zoom + center + offset + dismissOffset)
    }
}

private enum class GalleryGesture { UNDECIDED, PAGER, TRANSFORM, DISMISS, IGNORE }

internal fun Modifier.galleryGestures(
    state: GalleryTransformState, enabled: Boolean, flingVelocity: Float,
    start: () -> Unit, finish: (Boolean) -> Unit, panFinished: (Offset) -> Unit = {}, gestureFinished: () -> Unit = {},
): Modifier = pointerInput(state, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        start()
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var mode = GalleryGesture.UNDECIDED
        var total = Offset.Zero
        var completed = false
        var transformedWithMultiplePointers = false
        try {
            do {
                val event = awaitPointerEvent()
                val pointers = event.changes.count { it.pressed }
                event.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                if (pointers >= 2) {
                    transformedWithMultiplePointers = true
                    mode = GalleryGesture.TRANSFORM
                    state.dismissOffset = Offset.Zero
                    state.dragging = false
                }
                val pan = event.calculatePan()
                if (mode == GalleryGesture.UNDECIDED) {
                    if (event.changes.any { it.isConsumed && it.positionChanged() }) mode = GalleryGesture.IGNORE
                    else {
                        total += pan
                        if (total.getDistance() > viewConfiguration.touchSlop) mode = when {
                            state.zoomed -> GalleryGesture.TRANSFORM
                            abs(total.x) > abs(total.y) -> GalleryGesture.PAGER
                            state.canPanVertically(total.y) -> GalleryGesture.TRANSFORM
                            total.y > 0 -> GalleryGesture.DISMISS
                            else -> GalleryGesture.IGNORE
                        }
                    }
                }
                when (mode) {
                    GalleryGesture.TRANSFORM -> {
                        state.transform(event.calculateCentroid(useCurrent = false), pan, event.calculateZoom())
                        event.changes.forEach { it.consume() }
                    }
                    GalleryGesture.DISMISS -> {
                        state.dragging = true
                        state.dragBy(pan)
                        event.changes.forEach { it.consume() }
                    }
                    GalleryGesture.IGNORE -> if (abs(total.y) > viewConfiguration.touchSlop) event.changes.forEach { it.consume() }
                    else -> Unit
                }
            } while (event.changes.any { it.pressed })
            completed = true
            if (mode == GalleryGesture.DISMISS) finish(state.shouldDismiss(tracker.calculateVelocity().y, flingVelocity))
            else if (mode == GalleryGesture.TRANSFORM && !transformedWithMultiplePointers) {
                val velocity = tracker.calculateVelocity()
                panFinished(Offset(velocity.x, velocity.y))
            }
        } finally {
            if (!completed && mode == GalleryGesture.DISMISS) finish(false)
            state.dragging = false
            gestureFinished()
        }
    }
}
