package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomePagerUiTest {
    @get:Rule val compose = createComposeRule()
    private fun page(filter: String) = FeedState(filter = filter, loading = false, topics = (1..30).map {
        Topic(it, "$filter 话题 $it", "横滑保留阅读位置的测试内容", null, 1, emptyList(), 0, 0, 0, "", "")
    })
    @Test fun swipingAndClickingShareSelectionAndPreserveEachListPosition() {
        val pages = listOf("latest", "hot", "top").associateWith(::page)
        var state by mutableStateOf(pages.getValue("latest"))
        var categories = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            FeedScreen(state, null, { state = pages.getValue(it) }, {}, {}, {}, {}, {}, {}, { categories++ }, pages = pages)
        } }
        capture("home-pager-latest.png")
        compose.onNodeWithTag("feed-list-latest").performScrollToIndex(10)
        compose.onNodeWithTag("home-pager").performTouchInput { swipeLeft(durationMillis = 600) }
        compose.onNodeWithText("热门").assertIsSelected()
        compose.onNodeWithText("hot 话题 1").assertIsDisplayed()
        capture("home-pager-hot.png")
        compose.onNodeWithTag("home-pager").performTouchInput { swipeRight(durationMillis = 600) }
        compose.onNodeWithText("最新").assertIsSelected()
        compose.onNodeWithText("latest 话题 11").assertIsDisplayed()
        compose.onNodeWithText("排行榜").performClick()
        compose.onNodeWithText("排行榜").assertIsSelected()
        compose.onNodeWithText("类别").performClick()
        compose.onNodeWithText("类别").assertIsSelected()
        compose.onNodeWithText("社区分类").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, categories) }
    }
    @Test fun logoutRemovesPersonalPagesAndCachedPrivateTopics() {
        var user by mutableStateOf<Member?>(Member(1, "one", "用户一", ""))
        var state by mutableStateOf(page("unread").copy(accountId = 1))
        compose.setContent { KudoTheme(DisplaySettings()) {
            FeedScreen(state, user, {}, {}, {}, {}, {}, {}, {}, {}, pages = mapOf("bookmarks" to page("bookmarks").copy(accountId = 1)))
        } }
        compose.onNodeWithText("未读").assertIsSelected()
        compose.runOnIdle { user = null; state = page("latest") }
        compose.onNodeWithText("最新").assertIsSelected()
        compose.onNodeWithText("未读").assertDoesNotExist()
        compose.onNodeWithText("书签").assertDoesNotExist()
        compose.onNodeWithText("unread 话题 1").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
