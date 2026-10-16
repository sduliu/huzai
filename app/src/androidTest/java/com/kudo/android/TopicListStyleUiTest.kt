package com.kudo.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.feed.FeedScreen
import com.kudo.android.ui.feed.FeedState
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopicListStyleUiTest {
    @get:Rule val compose = createComposeRule()
    private val member = Member(1, "author", "作者", "", avatarDetailsLoaded = true)
    private val topic = Topic(1, "传统列表的完整帖子标题", "信息流摘要内容", member, 4,
        listOf("纯水"), 23, 99, 8, "", "2026-10-09T00:00:00Z")

    @Test fun bothStylesShowServerBlueDotOrUnreadCountAndHideMissingState() {
        var state by mutableStateOf(topic.copy(unseen = true))
        var style by mutableStateOf(TopicListStyle.FEED)
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            FeedScreen(FeedState(topics = listOf(state), loading = false), null, {}, {}, {}, {}, {}, {}, {}, {}, listStyle = style)
        } }
        for (value in TopicListStyle.entries) {
            compose.runOnIdle { style = value; state = topic.copy(unseen = true) }
            compose.onNodeWithContentDescription("未看过的话题", useUnmergedTree = true).assertIsDisplayed()
            compose.runOnIdle { state = topic.copy(unseen = true, unreadPosts = 12) }
            compose.onNodeWithContentDescription("12 条新回复", useUnmergedTree = true).assertTextEquals("12")
            compose.onNodeWithContentDescription("未看过的话题", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { state = topic }
            compose.onNodeWithTag("topic-unread-1", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun stylesSwitchWithoutLosingTopicAndAvatarActions() {
        var style by mutableStateOf(TopicListStyle.FEED)
        var opened = 0
        var author = ""
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                FeedScreen(FeedState(topics = listOf(topic), loading = false), null, {}, {}, {},
                    { opened = it }, { author = it }, {}, {}, {}, listStyle = style)
            }
        }
        compose.onNodeWithText(topic.excerpt).assertIsDisplayed()
        compose.runOnIdle { style = TopicListStyle.TITLE }
        compose.onNodeWithText(topic.excerpt).assertDoesNotExist()
        compose.onNodeWithTag("topic-title-1").assertIsDisplayed()
        compose.onNodeWithTag("topic-count-1", useUnmergedTree = true).assertTextEquals("23")
        compose.onNodeWithText(topic.title).performClick()
        compose.onNodeWithContentDescription("作者的头像").performClick()
        compose.runOnIdle { assertEquals(1, opened); assertEquals("author", author); style = TopicListStyle.FEED }
        compose.onNodeWithText(topic.excerpt).assertIsDisplayed()
    }

    @Test fun listStyleSheetSelectsAndCancels() {
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.LIGHT))
        compose.setContent { KudoTheme(settings) { SettingsScreen(settings, { settings = it }, null, {}, {}, {}) } }
        compose.onNodeWithText("主题列表样式").performScrollTo().performClick()
        compose.onNodeWithTag("modal-choice-0").assertIsSelected()
        compose.onNodeWithText("传统标题").performClick()
        compose.runOnIdle { assertEquals(TopicListStyle.TITLE, settings.topicListStyle) }
        compose.onNodeWithText("主题列表样式").performClick()
        compose.onNodeWithTag("modal-choice-1").assertIsSelected()
        compose.onNodeWithTag("sheet-drag-handle").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(TopicListStyle.TITLE, settings.topicListStyle) }
    }

    @Test fun preferenceDefaultsForOldInstallsAndPersistsAcrossInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("kudo_preferences", 0)
        val previous = AppSettings(context).display.value
        val hadKey = preferences.contains("topic_list_style")
        val previousKey = preferences.getString("topic_list_style", null)
        try {
            preferences.edit().remove("topic_list_style").commit()
            assertEquals(TopicListStyle.FEED, AppSettings(context).display.value.topicListStyle)
            val settings = AppSettings(context)
            settings.update(settings.display.value.copy(topicListStyle = TopicListStyle.TITLE))
            assertEquals(TopicListStyle.TITLE, AppSettings(context).display.value.topicListStyle)
            preferences.edit().putString("topic_list_style", "future_unknown_value").commit()
            assertEquals(TopicListStyle.FEED, AppSettings(context).display.value.topicListStyle)
        } finally {
            AppSettings(context).update(previous)
            if (hadKey) preferences.edit().putString("topic_list_style", previousKey).commit()
            else preferences.edit().remove("topic_list_style").commit()
        }
    }
}
