package com.kudo.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.PostPollView
import org.junit.*
import org.junit.Assert.*

class PostPollsUiTest {
    @get:Rule val compose = createComposeRule()
    private val poll = PostPoll("poll", "regular", "open", "always", listOf(PollOption("a", "睡衣", 95), PollOption("b", "裸睡", 319)), 414)
    @Test fun singleVoteShowsConfirmedResultsAndCanReturnToRetract() {
        var saved by mutableStateOf(emptyList<String>())
        var submitted: List<String>? = null
        compose.setContent { KudoTheme(DisplaySettings()) { Column(Modifier.padding(16.dp)) {
            PostPollView(poll, saved, false, false, false, true, { submitted = it; saved = it }, {}, {})
        } } }
        compose.onNodeWithText("414 投票人").assertIsDisplayed()
        compose.onNodeWithTag("poll-option-b").performClick()
        compose.onNodeWithText("77% · 319").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("b"), submitted) }
        compose.onNodeWithText("返回投票").performClick()
        compose.onNodeWithText("撤回投票").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), submitted) }
    }
    @Test fun multipleSelectionRespectsBoundsAndFailureKeepsSelection() {
        var selected: List<String>? = null
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            PostPollView(poll.copy(type = "multiple", min = 2, max = 2, results = "on_vote"), emptyList(), false, false, false, true, { selected = it }, {}, {})
        } }
        compose.onNodeWithTag("poll-submit").assertIsNotEnabled()
        compose.onNodeWithTag("poll-option-a").performClick()
        compose.onNodeWithTag("poll-submit").assertIsNotEnabled()
        compose.onNodeWithTag("poll-option-b").performClick()
        compose.onNodeWithTag("poll-submit").assertIsEnabled().performClick()
        compose.onNodeWithTag("poll-option-a").assertIsSelected()
        compose.onNodeWithTag("poll-option-b").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf("a", "b"), selected) }
        compose.onNodeWithText("查看结果").assertDoesNotExist()
    }
    @Test fun anonymousSelectionLogsInAndClosedPollCannotWrite() {
        var loggedIn by mutableStateOf(false)
        var closed by mutableStateOf(false)
        var logins = 0; var writes = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            PostPollView(poll.copy(status = if (closed) "closed" else "open"), emptyList(), false, false, false, loggedIn, { writes++ }, { logins++ }, {})
        } }
        compose.onNodeWithTag("poll-option-a").performClick()
        compose.runOnIdle { assertEquals(1, logins); assertEquals(0, writes); closed = true; loggedIn = true }
        compose.onNodeWithText("投票已结束").assertIsDisplayed()
        compose.onNodeWithTag("poll-option-a").assertIsNotEnabled()
    }
}
