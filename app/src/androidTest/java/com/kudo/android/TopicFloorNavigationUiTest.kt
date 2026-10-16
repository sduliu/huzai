package com.kudo.android

import android.graphics.Bitmap
import android.os.SystemClock
import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TopicFloorNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "fixture", "离线作者", "", avatarDetailsLoaded = true)
    private fun post(number: Int, parent: Int? = null) = Post(number, number, author, "<p>正文第 $number 楼</p>", "", 0, false, null, parent, true)
    private val posts = listOf(post(1), post(2), post(8), post(20), post(80, 8))
    private val detail = TopicDetail(5, "真实楼层索引", 1, posts, posts.map(Post::id), false, highestPostNumber = 100)

    private fun openFloorEditor() {
        compose.onNodeWithTag("topic-floor-index").performClick()
        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("floor-input", useUnmergedTree = true).fetchSemanticsNodes()
                .any { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        }
    }

    private fun floorInput() = compose.onNodeWithTag("floor-input", useUnmergedTree = true)
    private fun floorConfirm() = compose.onNodeWithTag("floor-confirm", useUnmergedTree = true)
    private fun floorSlider() = compose.onNodeWithTag("floor-slider", useUnmergedTree = true)

    @Test fun aShortTopicShowsTheLastFloorAtTheBottomAndResumesVisibleFloorWhenScrollingBack() {
        val loaded = (1..7).map(::post)
        val state = TopicState(detail.copy(posts = loaded, stream = loaded.map(Post::id), highestPostNumber = 7), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, readingEnabled = false)
        } }
        compose.onNodeWithTag("topic-posts").performScrollToIndex(loaded.size + 1)
        compose.onNodeWithTag("post-7").assertIsDisplayed()
        compose.onNodeWithText("已读完全部回复").assertIsDisplayed()
        compose.onNodeWithText("7 / 7").assertIsDisplayed()
        capture("floor-short-topic-bottom.png")
        compose.onNodeWithTag("topic-posts").performScrollToIndex(2)
        compose.onNodeWithText("2 / 7").assertIsDisplayed()
    }

    @Test fun aPendingOrFailedPageDoesNotCountItsBottomAsTheTopicEnd() {
        val loaded = (1..7).map(::post)
        var state by mutableStateOf(TopicState(detail.copy(posts = loaded, stream = (1..30).toList(), highestPostNumber = 30),
            false, moreError = IllegalStateException("分页失败")))
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, readingEnabled = false)
        } }
        fun assertVisibleFloor() {
            compose.onNodeWithTag("topic-posts").performScrollToIndex(loaded.size + 1)
            val viewport = compose.onNodeWithTag("topic-posts").fetchSemanticsNode().boundsInRoot
            val firstVisible = loaded.first { post ->
                compose.onAllNodesWithTag("post-${post.number}").fetchSemanticsNodes().any {
                    it.boundsInRoot.bottom > viewport.top && it.boundsInRoot.top < viewport.bottom
                }
            }.number
            assertTrue("fixture must leave earlier floors visible at the bottom", firstVisible < 7)
            compose.onNodeWithText("$firstVisible / 30").assertIsDisplayed()
        }
        assertVisibleFloor()
        compose.runOnIdle { state = state.copy(detail = state.detail!!.copy(stream = loaded.map(Post::id))) }
        assertVisibleFloor()
        compose.runOnIdle { state = state.copy(moreError = null, loadingMore = true) }
        assertVisibleFloor()
        compose.runOnIdle { state = state.copy(loadingMore = false) }
        compose.onNodeWithTag("topic-posts").performScrollToIndex(loaded.size + 1)
        compose.onNodeWithText("7 / 30").assertIsDisplayed()
    }

    @Test fun anUnavailableFinalFloorKeepsTheLastAccessibleNumberAtTheBottom() {
        val loaded = (1..6).map(::post)
        val state = TopicState(detail.copy(posts = loaded, stream = loaded.map(Post::id), highestPostNumber = 7), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, readingEnabled = false)
        } }
        compose.onNodeWithTag("topic-posts").performScrollToIndex(loaded.size + 1)
        compose.onNodeWithTag("post-6").assertIsDisplayed()
        compose.onNodeWithText("6 / 7").assertIsDisplayed()
    }

    @Test fun sliderOnlyPreviewsAndConfirmedNestedFloorTargetsTheMainTimeline() {
        var state by mutableStateOf(TopicState(detail, false))
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, jumpTo = {
                requests++; state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1)
            })
        } }
        compose.onNodeWithText("5 个楼层").assertDoesNotExist()
        compose.onNodeWithTag("topic-floor-index").assertIsDisplayed().performClick()
        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("floor-navigation-bar", useUnmergedTree = true).fetchSemanticsNodes()
                .any { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        }
        compose.onNodeWithTag("topic-reply").assertDoesNotExist()
        compose.onNodeWithTag("topic-actions").assertDoesNotExist()
        compose.onNodeWithTag("floor-navigation-bar", useUnmergedTree = true).assertIsDisplayed()
        val bar = compose.onNodeWithTag("floor-navigation-bar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val slider = floorSlider().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("slider must receive most of the available width", slider.width > bar.width * .55f)
        floorSlider().performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        floorInput().assertTextEquals("60")
        val pixels = floorSlider().captureToImage().toPixelMap()
        var yellow = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val color = pixels[x, y]
            if (color.red > .9f && color.green in .5f.. .8f && color.blue < .2f) yellow++
        }
        assertTrue("the filled slider track must use LogoYellow", yellow > 20)
        capture("floor-inline-light.png")
        floorSlider().performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * 59f / 99f, center.y), centerRight - androidx.compose.ui.geometry.Offset(1f, 0f), 600)
        }
        floorInput().assertTextEquals("100")
        floorSlider().performTouchInput { click(centerLeft + androidx.compose.ui.geometry.Offset(1f, 0f)) }
        floorInput().assertTextEquals("1")
        compose.runOnIdle { assertEquals(0, requests) }
        floorInput().performTextReplacement("101")
        floorConfirm().assertIsNotEnabled()
        floorInput().performTextReplacement("80")
        floorConfirm().assertIsEnabled().performClick()
        compose.onNodeWithTag("floor-input").assertDoesNotExist()
        compose.onNodeWithTag("topic-reply").assertIsDisplayed()
        compose.onNodeWithTag("topic-actions").assertIsDisplayed()
        compose.onNodeWithTag("thread-posts").assertDoesNotExist()
        compose.onNodeWithTag("post-80").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun scrollingUsesActualNumbersAndRepeatedJumpRepositionsTheList() {
        val scrollPosts = posts + listOf(post(30), post(40), post(100))
        var state by mutableStateOf(TopicState(detail.copy(posts = scrollPosts, stream = scrollPosts.map(Post::id)), false))
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                jumpTo = { state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1) })
        } }
        repeat(2) {
            compose.onNodeWithTag("topic-posts").performScrollToIndex(4)
            compose.onNodeWithText("20 / 100").assertIsDisplayed()
            if (it == 0) capture("floor-progress-light.png")
            openFloorEditor()
            floorInput().performTextReplacement("8")
            floorConfirm().performClick()
            compose.onNodeWithText("8 / 100").assertIsDisplayed()
            compose.onNodeWithTag("post-8").assertIsDisplayed()
        }
    }

    @Test fun aFarFloorJumpAlignsTheRequestedPostInsteadOfStoppingAtTheLoadedWindow() {
        val loaded = (1..20).map(::post) + post(80, 20) + (81..200).map(::post)
        var state by mutableStateOf(TopicState(detail.copy(posts = loaded, stream = loaded.map(Post::id)), false))
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                jumpTo = { state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1) })
        } }
        val viewport = compose.onNodeWithTag("topic-posts").fetchSemanticsNode().boundsInRoot
        openFloorEditor()
        floorInput().performTextReplacement("80")
        floorConfirm().performClick()
        compose.waitForIdle()
        val targetTop = compose.onNodeWithTag("post-80").fetchSemanticsNode().boundsInRoot.top
        assertTrue("far jump must leave the requested post visible: target=" + targetTop + " viewportBottom=" + viewport.bottom,
            targetTop < viewport.bottom)
        compose.onNodeWithText("80 / 100").assertIsDisplayed()
    }

    @Test fun backwardContinuationKeepsTheVisibleFloorAndConnectsToItsNeighbors() {
        var state by mutableStateOf(TopicState(detail.copy(posts = (98..117).map(::post), stream = (1..120).toList(), highestPostNumber = 120),
            false, targetNumber = 108, targetRevision = 1))
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, previous = {
                requests++
                state = state.copy(loadingPrevious = true)
            })
        } }
        compose.onNodeWithTag("post-108").assertIsDisplayed()
        compose.onNodeWithTag("post-20").assertDoesNotExist()
        compose.onNodeWithTag("topic-posts").performScrollToIndex(1)
        compose.waitUntil { requests == 1 }
        val before = compose.onNodeWithTag("post-98").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(loadingPrevious = false, detail = state.detail!!.copy(posts = (78..117).map(::post))) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("post-98").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("前插分页不能跳走正在看的楼层", before.top, after.top, 3f)
        compose.onNodeWithTag("topic-posts").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 200f), durationMillis = 600) }
        compose.onNodeWithTag("post-97").assertIsDisplayed()
        capture("floor-backward-continuous.png")
    }

    @Test fun backwardContinuationPrefetchesBeforeTheFirstLoadedFloorBecomesVisible() {
        var state by mutableStateOf(TopicState(detail.copy(posts = (98..117).map(::post), stream = (1..120).toList(), highestPostNumber = 120),
            false, targetNumber = 108, targetRevision = 1))
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, previous = {
                requests++; state = state.copy(loadingPrevious = true)
            })
        } }
        compose.onNodeWithTag("post-108").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("topic-posts").performScrollToIndex(4)
        compose.onNodeWithTag("post-100").assertIsDisplayed()
        compose.onNodeWithTag("post-98").assertIsNotDisplayed()
        compose.waitUntil(4000) { requests == 1 }
        compose.runOnIdle { assertEquals(1, requests) }
        val before = compose.onNodeWithTag("post-100").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(loadingPrevious = false, detail = state.detail!!.copy(posts = (78..117).map(::post))) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("post-100").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 3f)
        compose.runOnIdle { assertEquals("prepending moves the threshold away and must not load another page", 1, requests) }
        capture("floor-early-previous-prefetch.png")
    }

    @Test fun pendingReplyContextDoesNotBlockTimelineLocationOrBackwardPagination() {
        var state by mutableStateOf(TopicState(detail.copy(posts = (98..117).map(::post), stream = (1..120).toList(), highestPostNumber = 120),
            false, contextLoading = true, targetNumber = 108, targetRevision = 1))
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, previous = {
                requests++; state = state.copy(loadingPrevious = true)
            })
        } }
        compose.onNodeWithTag("post-108").assertIsDisplayed()
        compose.onNodeWithTag("topic-posts").performScrollToIndex(1)
        compose.waitUntil(4000) { requests == 1 }
        compose.runOnIdle { assertTrue(state.contextLoading); assertTrue(state.loadingPrevious) }
    }

    @Test fun backwardPageKeepsTheLatestReadingPositionIfTheReaderMovesDuringLoading() {
        var state by mutableStateOf(TopicState(detail.copy(posts = (98..117).map(::post), stream = (1..120).toList(), highestPostNumber = 120),
            false, targetNumber = 108, targetRevision = 1))
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, previous = {
                requests++; state = state.copy(loadingPrevious = true)
            })
        } }
        compose.onNodeWithTag("post-108").assertIsDisplayed()
        compose.onNodeWithTag("topic-posts").performScrollToIndex(1)
        compose.waitUntil(4000) { requests == 1 }
        compose.onNodeWithTag("topic-posts").performScrollToIndex(8)
        val before = compose.onNodeWithTag("post-104").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(loadingPrevious = false, detail = state.detail!!.copy(posts = (78..117).map(::post))) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("post-104").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("cannot restore the old request-start floor after the reader moved", before.top, after.top, 3f)
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun narrowDarkLargeTextKeepsAllControlsAndFailureKeepsTheForm() {
        var state by mutableStateOf(TopicState(detail.copy(highestPostNumber = 120000), false))
        var mode by mutableStateOf(GlassMode.LIQUID)
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Box(Modifier.width(280.dp).fillMaxHeight()) {
                TopicScreen(state, mode, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                    jumpTo = { state = state.copy(jumping = true) })
            }
        } }
        GlassMode.entries.forEach { glass ->
            compose.runOnIdle { mode = glass }
            val reply = compose.onNodeWithTag("topic-reply").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val index = compose.onNodeWithTag("topic-floor-index").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("topic-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(reply.right < index.left); assertTrue(index.right < actions.left)
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("1 / 120000").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertFalse(results.single().hasVisualOverflow)
            capture("floor-${glass.name.lowercase()}-narrow-dark-large.png")
        }
        openFloorEditor()
        floorInput().performTextReplacement("120000")
        floorInput().assertTextEquals("120000")
        floorConfirm().performClick().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(jumping = false, jumpError = "此楼层暂不可访问") }
        capture("floor-inline-narrow-dark-error.png")
        compose.onNodeWithText("此楼层暂不可访问").assertIsDisplayed()
        floorInput().assertTextEquals("120000")
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("floor-input").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("floor-input").assertDoesNotExist()
    }

    @Test fun theFloorPanelAndPendingJumpPauseReadingWithoutMarkingSkippedFloors() {
        var state by mutableStateOf(TopicState(detail, false, viewerId = 9))
        var active by mutableStateOf(false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                readingEnabled = active, onPostsRead = { state = state.copy(readNumbers = state.readNumbers + it) },
                jumpTo = { state = state.copy(jumping = true) })
        } }
        openFloorEditor()
        compose.runOnIdle { active = true }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { assertTrue(state.readNumbers.isEmpty()) }
        floorInput().performTextReplacement("20")
        floorConfirm().performClick()
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle {
            assertTrue(state.readNumbers.isEmpty())
            state = state.copy(jumping = false, targetNumber = 20, targetRevision = 1)
        }
        compose.waitUntil(4000) { 20 in state.readNumbers }
        // At the end of a short list, earlier bodies can remain genuinely visible beside the target.
        compose.runOnIdle {
            // Child 80 is now also an original timeline item and may be genuinely visible.
            assertTrue(state.readNumbers.all { it in setOf(2, 8, 20, 80) })
            assertFalse(1 in state.readNumbers)
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
