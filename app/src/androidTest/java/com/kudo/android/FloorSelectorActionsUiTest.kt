package com.kudo.android

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FloorSelectorActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "fixture", "离线作者", "", avatarDetailsLoaded = true)
    private val posts = (1..8).map { Post(it, it, author, "<p>第 $it 楼</p>", "", 0, false, null, null, false) }
    private val state = TopicState(TopicDetail(5, "楼层动作", 1, posts, posts.map(Post::id), false, highestPostNumber = 8), false)

    @Test fun unchangedFloorClosesWithoutRequestingANavigation() {
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                readingEnabled = false, jumpTo = { requests++ })
        } }
        compose.onNodeWithTag("topic-floor-index").performClick()
        compose.onNodeWithContentDescription("关闭楼层选择", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag("floor-input", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("topic-reply").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun changingBackToTheOpeningFloorRestoresClose() {
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                readingEnabled = false, jumpTo = { requests++ })
        } }
        compose.onNodeWithTag("topic-floor-index").performClick()
        val input = compose.onNodeWithTag("floor-input", useUnmergedTree = true)
        val original = input.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        input.performTextReplacement("6")
        compose.onNodeWithContentDescription("确定楼层", useUnmergedTree = true).assertIsDisplayed()
        input.performTextReplacement(original)
        compose.onNodeWithContentDescription("关闭楼层选择", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(0, requests) }
    }

}
