package com.kudo.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Test

class GalleryTransitionTest {
    @Test fun cropExpandsWithoutChangingImageAspectRatio() {
        val anchor = GalleryAnchorSnapshot(Rect(100f, 600f, 500f, 900f), Rect(100f, 600f, 500f, 900f), ContentScale.Crop, 30f, null)
        val from = anchor.imageFrame(Offset.Zero, 2f)
        assertEquals(Rect(0f, 600f, 600f, 900f), from.content)
        val viewer = GalleryTransformState(2f).apply { resize(Size(1000f, 2000f)) }.imageFrame()
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val frame = from.interpolate(viewer, progress)
            assertEquals(2f, frame.content.width / frame.content.height, 0.001f)
            assertTrue(frame.container.width in 400f..1000f)
            assertTrue(frame.radius in 0f..30f)
        }
        assertEquals(viewer, from.interpolate(viewer, 1f))
    }

    @Test fun partialVisibilityAndWindowOffsetDoNotReplaceTheFullImageBounds() {
        val anchor = GalleryAnchorSnapshot(Rect(180f, -400f, 780f, 1600f), Rect(180f, 200f, 780f, 1200f), ContentScale.Fit, 24f, null)
        val frame = anchor.imageFrame(Offset(100f, 100f), 0.3f)
        assertEquals(80f, frame.content.left, 0.001f)
        assertEquals(-500f, frame.content.top, 0.001f)
        assertEquals(680f, frame.content.right, 0.001f)
        assertEquals(1500f, frame.content.bottom, 0.001f)
        assertEquals(Rect(80f, 100f, 680f, 1100f), frame.visible)
        assertEquals(frame.content.width, frame.container.width, 0.001f)
        assertEquals(frame.content.height, frame.container.height, 0.001f)
    }

    @Test fun returningAfterZoomStartsFromTheDisplayedTransform() {
        val state = GalleryTransformState(1f).apply { resize(Size(1000f, 1000f)) }
        state.transform(Offset(500f, 500f), Offset(120f, -80f), 2f)
        val frame = state.imageFrame()
        assertEquals(Rect(-380f, -580f, 1620f, 1420f), frame.content)
        assertEquals(Rect(0f, 0f, 1000f, 1000f), frame.visible)
    }
}
