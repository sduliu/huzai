package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.*
import org.junit.Assert.*

class GalleryPanUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = GalleryTransformState(0.05f)
    private fun show() {
        compose.setContent { KudoTheme(DisplaySettings()) {
            GalleryImagePage(ArticleBlock.Image("file:///missing-fixture.png", "长图", 0.05f), 0, state, true, true, {}, {})
        } }
        compose.waitForIdle()
    }
    @Test fun releasedLongImageKeepsMovingAndTouchDownStopsTheFling() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("gallery-page-0").performTouchInput {
            swipeWithVelocity(center, center - Offset(0f, 300f), 2000f, 200)
        }
        val released = state.offset.y
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertTrue("松手后应继续向上滚动", state.offset.y < released - 1f) }
        compose.onNodeWithTag("gallery-page-0").performTouchInput { down(center) }
        val stopped = state.offset
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals("重新触摸应立即停止惯性", stopped, state.offset) }
        compose.onNodeWithTag("gallery-page-0").performTouchInput { cancel() }
    }
    @Test fun zoomedWideImageFlingsHorizontallyAndStopsAtItsEdge() {
        show()
        compose.runOnIdle {
            state.decodedRatio(5f)
            state.resetZoom()
            state.accessibleZoom()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("gallery-page-0").performTouchInput {
            swipeWithVelocity(center, center - Offset(160f, 0f), 1500f, 180)
        }
        val released = state.offset.x
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertTrue("放大的宽图松手后应继续横向移动", state.offset.x < released - 1f) }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle {
            val bounds = state.displayedBounds()
            assertTrue(bounds.left <= 0.5f && bounds.right >= state.viewport.width - 0.5f)
            assertEquals(0f, state.offset.y, 0.01f)
        }
    }
}
