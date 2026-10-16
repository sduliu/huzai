package com.kudo.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.*
import org.junit.Assert.*

class ReplyTimelineUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "author", "作者", "", avatarDetailsLoaded = true)
    private fun post(n: Int, parent: Int? = null) = Post(n, n, author, "<p>原楼层正文$n</p>", "", 0, false, null, parent, true)
    private val posts = listOf(post(1), post(2), post(3, 2), post(4, 3))
    private val detail = TopicDetail(5, "保留原楼层", 1, posts, posts.map(Post::id), false)
    @Composable private fun Screen(target: Int? = null, reply: (Post?) -> Unit = {}) {
        KudoTheme(DisplaySettings()) {
            TopicScreen(TopicState(detail, false, targetNumber = target), GlassMode.TRANSLUCENT,
                {}, {}, {}, {}, {}, {}, reply, {}, {}, {}, {})
        }
    }
    @Test fun nestedRepliesStillExistInTheMainTimelineWithTheirOwnOperations() {
        var replied = 0
        compose.setContent { Screen(reply = { replied = it!!.number }) }
        compose.onNodeWithTag("topic-posts").performScrollToNode(hasTestTag("post-3"))
        compose.onNodeWithTag("post-3").assertIsDisplayed()
        compose.onNode(hasText("回复") and hasClickAction() and hasAnyAncestor(hasTestTag("post-3"))).performClick()
        compose.runOnIdle { assertEquals(3, replied) }
        compose.onNodeWithTag("topic-posts").performScrollToNode(hasTestTag("post-4"))
        compose.onNodeWithTag("post-4").assertIsDisplayed()
    }
    @Test fun inlinePreviewNavigatesToTheOriginalFloorWithoutOpeningAnotherDiscussion() {
        compose.setContent { Screen() }
        compose.onNodeWithTag("reply-preview-post-3").performScrollTo().performClick()
        compose.onNodeWithTag("thread-posts").assertDoesNotExist()
        compose.onNodeWithTag("post-3").assertIsDisplayed()
    }
    @Test fun expandedDiscussionOnlyPreviewsContentAndCanNavigateToItsOriginalFloor() {
        compose.setContent { Screen() }
        compose.onNodeWithText("查看 2 条回复").performScrollTo().performClick()
        compose.onNodeWithTag("thread-posts").assertIsDisplayed()
        compose.onNode(hasContentDescription("更多操作") and hasAnyAncestor(hasTestTag("thread-posts"))).assertDoesNotExist()
        compose.onNode(hasContentDescription("Boost 此帖子") and hasAnyAncestor(hasTestTag("thread-posts"))).assertDoesNotExist()
        compose.onNodeWithTag("thread-jump-3").performScrollTo().performClick()
        compose.onNodeWithTag("thread-posts").assertDoesNotExist()
        compose.onNodeWithTag("post-3").assertIsDisplayed()
    }
    @Test fun deepLinkTargetsTheOriginalChildFloorInTheMainTimeline() {
        compose.setContent { Screen(target = 4) }
        compose.onNodeWithTag("thread-posts").assertDoesNotExist()
        compose.onNodeWithTag("post-4").assertIsDisplayed()
    }
}
