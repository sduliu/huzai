package com.kudo.android

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.TopicBottomBar
import com.kudo.android.ui.topic.TopicState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FloorBarAnimationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(editing: () -> Boolean) {
        val member = Member(1, "fixture", "作者", "")
        val post = Post(1, 1, member, "正文", "", 0, false, null, null, false)
        val state = TopicState(TopicDetail(5, "动画", 1, listOf(post), listOf(1), false, highestPostNumber = 5), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicBottomBar(state, rememberLayerBackdrop(), GlassMode.TRANSLUCENT, true, {}, {}, {}, {}, {},
                floorEditing = editing(), floorHighest = 5)
        } }
    }

    @Test fun expansionDoesNotJumpWhenTheSideButtonsFinishExiting() {
        var editing by mutableStateOf(false)
        show { editing }
        compose.runOnIdle { editing = true }
        val expanded = width("floor-navigation-bar")
        compose.runOnIdle { editing = false }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { editing = true }
            var previous = 0f
            repeat(60) { frame ->
                compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16)
                val current = width("floor-navigation-bar")
                val remaining = expanded - previous
                if (remaining in 1f..60f) assertTrue(
                    "展开末尾不能因移除间距跳变：frame=$frame previous=$previous current=$current expanded=$expanded",
                    current - previous <= 18f)
                previous = current
            }
            assertEquals(expanded, previous, 1f)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun contractionAndReversingAnUnfinishedExpansionKeepContinuousGeometry() {
        var editing by mutableStateOf(false)
        show { editing }
        val collapsed = width("floor-control-slot")
        compose.runOnIdle { editing = true }
        val expanded = width("floor-control-slot")
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { editing = false }
            var previous = expanded
            repeat(40) { frame ->
                compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16)
                val current = width("floor-control-slot")
                assertTrue("收回宽度应连续缩小", current <= previous + 1f)
                if (previous - collapsed in 1f..60f) assertTrue("收回末尾不能突变：frame=$frame", previous - current <= 18f)
                previous = current
            }
            assertEquals(collapsed, previous, 1f)
            compose.runOnIdle { editing = true }
            repeat(6) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
            val before = width("floor-control-slot")
            assertTrue(before > collapsed && before < expanded)
            compose.runOnIdle { editing = false }
            assertEquals("中途反向不能先跳到端点", before, width("floor-control-slot"), 1f)
            repeat(40) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
            assertEquals(collapsed, width("floor-control-slot"), 1f)
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithTag("topic-reply").assertIsDisplayed()
        compose.onNodeWithTag("topic-actions").assertIsDisplayed()
    }

    private fun width(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.width ?: 0f
}
