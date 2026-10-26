package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
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

    // 批H-1：compactRowsKeepOneLine/disablingCompactMode/preferencesDefaultOnPersist 三测移除——
    // 精简置顶特性已被 1771f08 删除（DisplaySettings.compactPinnedTopics、CompactPinnedTopicRow、
    // 设置开关「置顶贴精简显示」、topic-pinned-* testTag 均不存在），测试目标本身已移除，无对齐对象。
    // 保留 missingCategoryUsesFallbackThenUpdatesWhenMetadataArrives（与精简置顶无关的独立用例）。

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
