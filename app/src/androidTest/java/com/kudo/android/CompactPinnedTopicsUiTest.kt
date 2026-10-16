package com.kudo.android

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.components.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompactPinnedTopicsUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "fixture", "离线作者", "", avatarDetailsLoaded = true)
    private val category = Category(4, "开发调优", "dev", "0088cc", "", 0, "code")
    private val pinned = Topic(5, "这是一个很长的置顶话题标题，用来验证单行省略与未读状态的排列", "置顶摘要", author,
        4, listOf("标签"), 30, 200, 12, "", "", pinned = true, unseen = true, unreadPosts = 12)
    private val regular = pinned.copy(id = 6, title = "普通话题", excerpt = "普通帖摘要", pinned = false, unseen = false, unreadPosts = 0)

    @Test fun compactRowsKeepOneLineOrderedIconsUnreadStatesAndTopicClicksInBothStyles() {
        var topic by mutableStateOf(pinned)
        var style by mutableStateOf(TopicListStyle.FEED)
        var opened = 0
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Column(Modifier.width(280.dp)) {
                if (style == TopicListStyle.FEED) TopicCard(topic, category, { opened = topic.id }, {})
                else TopicTitleRow(topic, category, { opened = topic.id }, {})
            }
        } }
        for (value in TopicListStyle.entries) {
            compose.runOnIdle { style = value; topic = pinned }
            val row = compose.onNodeWithTag("topic-pinned-5").assertIsDisplayed()
            row.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            compose.onNodeWithText("置顶摘要").assertDoesNotExist()
            compose.onNodeWithContentDescription("离线作者的头像").assertDoesNotExist()
            val pin = compose.onNodeWithContentDescription("置顶", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val icon = compose.onNodeWithContentDescription("分类图标：开发调优", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithTag("topic-pinned-title-5", useUnmergedTree = true)
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val dot = compose.onNodeWithTag("topic-pinned-unseen-5", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val count = compose.onNodeWithTag("topic-unread-5", useUnmergedTree = true).assertTextEquals("12").fetchSemanticsNode().boundsInRoot
            assertTrue(pin.right < icon.left && icon.right < titleBounds.left)
            assertTrue(titleBounds.right < dot.left && dot.right < count.left)
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertTrue(layouts.single().isLineEllipsized(0))
            row.performClick()
            compose.runOnIdle { assertEquals(5, opened); topic = pinned.copy(unseen = false) }
            compose.onNodeWithTag("topic-pinned-unseen-5", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("12 条新回复", useUnmergedTree = true).assertIsDisplayed()
            compose.runOnIdle { topic = pinned.copy(unreadPosts = 0) }
            compose.onNodeWithContentDescription("未看过的话题", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("topic-unread-5", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { topic = pinned.copy(unseen = false, unreadPosts = 0) }
            compose.onNodeWithTag("topic-pinned-unseen-5", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("topic-unread-5", useUnmergedTree = true).assertDoesNotExist()
        }
        capture("compact-pinned-narrow-dark.png")
    }

    @Test fun disablingCompactModeRestoresTheSelectedListStyleAndLeavesOrdinaryTopicsUnchanged() {
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.LIGHT))
        compose.setContent { KudoTheme(settings) {
            FeedScreen(FeedState(topics = listOf(pinned, regular), categories = listOf(category), loading = false), null,
                {}, {}, {}, {}, {}, {}, {}, {}, listStyle = settings.topicListStyle)
        } }
        for (style in TopicListStyle.entries) {
            compose.runOnIdle { settings = settings.copy(topicListStyle = style, compactPinnedTopics = true) }
            compose.onNodeWithTag("topic-pinned-5").assertIsDisplayed()
            val prefix = if (style == TopicListStyle.FEED) "topic-card" else "topic-title"
            compose.onNodeWithTag("$prefix-6").assertIsDisplayed()
            compose.onNodeWithTag("topic-pinned-6").assertDoesNotExist()
            if (style == TopicListStyle.FEED) capture("compact-pinned-feed-light.png")
            compose.runOnIdle { settings = settings.copy(compactPinnedTopics = false) }
            compose.onNodeWithTag("topic-pinned-5").assertDoesNotExist()
            compose.onNodeWithTag("$prefix-5").assertIsDisplayed()
            if (style == TopicListStyle.FEED) compose.onNodeWithText("置顶摘要").assertIsDisplayed()
            else compose.onNodeWithText("置顶摘要").assertDoesNotExist()
        }
    }

    @Test fun preferencesDefaultOnPersistAndRespectExplicitSignatureAndPinnedChoices() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "compact_pinned_fixture_${System.nanoTime()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(preferenceName, mode)
        }
        try {
            val store = AppSettings(context)
            assertTrue(DisplaySettings().compactPinnedTopics)
            assertTrue(store.display.value.compactPinnedTopics)
            assertTrue(store.display.value.showSignatures)
            var settings by mutableStateOf(store.display.value)
            compose.setContent { KudoTheme(settings) {
                SettingsScreen(settings, { settings = it; store.update(it) }, null, {}, {}, {})
            } }
            compose.onNodeWithText("置顶贴精简显示").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("compact-pinned-topics").assertIsOn().performClick().assertIsOff()
            assertFalse(AppSettings(context).display.value.compactPinnedTopics)
            compose.runOnIdle { settings = settings.copy(showSignatures = false); store.update(settings) }
            assertFalse(AppSettings(context).display.value.showSignatures)
            compose.onNodeWithTag("compact-pinned-topics").performClick().assertIsOn()
            assertTrue(AppSettings(context).display.value.compactPinnedTopics)
            assertFalse(AppSettings(context).display.value.showSignatures)
        } finally {
            target.deleteSharedPreferences(preferenceName)
        }
    }

    @Test fun missingCategoryUsesFallbackThenUpdatesWhenMetadataArrives() {
        var metadata by mutableStateOf(TopicPresentationMetadata())
        compose.setContent { KudoTheme(DisplaySettings()) {
            CompositionLocalProvider(LocalTopicPresentation provides metadata) {
                TopicCard(pinned, null, {}, {})
            }
        } }
        compose.onNodeWithContentDescription("分类图标", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { metadata = TopicPresentationMetadata(categories = listOf(category)) }
        compose.onNodeWithContentDescription("分类图标：开发调优", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("分类图标", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
