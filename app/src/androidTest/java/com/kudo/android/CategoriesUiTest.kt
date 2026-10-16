package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.CategoriesScreen
import com.kudo.android.ui.components.CategoryIcons
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CategoriesUiTest {
    @get:Rule val compose = createComposeRule()
    private val sample = listOf(
        Category(4, "开发调优", "develop", "32c3c3", "此版块包含开发、测试、调试、部署、优化、安全等方面的内容。", 1, "code"),
        Category(98, "国产替代", "domestic", "D12C25", "汇聚中国智造，推动技术自主。", 1, "seedling"),
        Category(14, "资源荟萃", "resource", "12A89D", "包括软件分享、开源仓库、视频课程、书籍等分享。", 1, "square-share-nodes"),
        Category(94, "网盘资源", "cloud", "16b176", "网盘资源专用类别，主帖不限时编辑。", 1, "hard-drive"),
        Category(42, "文档共建", "wiki", "9cb6c4", "佬友化身翰林学士，一起来编书了。", 1, "book"),
        Category(27, "非我莫属", "jobs", "a8c6fe", "招聘与求职信息", 1, "briefcase"),
    )

    @Test fun categoryGlyphsRenderAndCardsKeepUniqueNavigationBelowStatusBar() {
        val categories = sample + CategoryIcons.supportedNames.filter { name -> sample.none { it.icon == name } }
            .mapIndexed { index, name -> Category(200 + index, name, name, "32c3c3", "分类说明", 1, name) } +
            Category(999, "未配置图标", "fallback", "32c3c3", "保留默认图标", 1)
        val clicks = mutableListOf<Int>()
        var statusTop = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                CategoriesScreen(categories, false, null, {}, {}) { clicks.add(it.id) }
            }
        }
        compose.onNodeWithText("发现社区").assertDoesNotExist()
        compose.onNodeWithText("找到你的同好").assertDoesNotExist()
        compose.onNodeWithContentDescription("刷新分类").assertDoesNotExist()
        assertTrue(compose.onNodeWithText("开发调优").fetchSemanticsNode().boundsInRoot.top >= statusTop)
        compose.onNodeWithText(sample.first().description).assertIsDisplayed()
        capture("categories-icons-light.png")
        val list = compose.onNode(hasScrollAction())
        for ((index, category) in categories.withIndex()) {
            list.performScrollToIndex(index)
            compose.onAllNodesWithText(category.name).assertCountEquals(1)
            compose.onNodeWithText(category.name).assertIsDisplayed().performClick()
        }
        assertEquals(categories.map { it.id }, clicks)
        assertSame(Icons.Rounded.Tag, CategoryIcons.get("unknown-future-icon"))
        assertSame(Icons.Rounded.Tag, CategoryIcons.get(""))
    }

    @Test fun removingHeaderKeepsErrorRecoveryAndNarrowLargeTextReadable() {
        var categories by mutableStateOf(emptyList<Category>())
        var error by mutableStateOf<Throwable?>(ApiFailure(FailureKind.NETWORK, "网络连接失败"))
        var retries = 0
        var logins = 0
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.width(280.dp)) {
                        CategoriesScreen(categories, false, error, {
                            retries++
                            error = null
                            categories = sample
                        }, { logins++ }) {}
                    }
                }
            }
        }
        compose.onNodeWithText("重新加载").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("开发调优").assertIsDisplayed()
        capture("categories-icons-dark-large.png")
        compose.onNode(hasScrollAction()).performScrollToIndex(sample.lastIndex)
        compose.onNodeWithText("非我莫属").assertIsDisplayed().assertHasClickAction()
        compose.runOnIdle { categories = emptyList(); error = ApiFailure(FailureKind.VERIFICATION, "需要网页验证") }
        compose.onNodeWithText("完成网页验证").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, logins) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
