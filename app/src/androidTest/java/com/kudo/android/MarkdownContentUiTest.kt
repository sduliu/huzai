package com.kudo.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.DisplaySettings
import com.kudo.android.data.ThemeMode
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MarkdownContentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realCalloutLineBreakKeepsBodyAndOnlyExplicitMarkersFold() {
        compose.setContent { KudoTheme(DisplaySettings()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("markdown-fixture")) {
                RichContent("""<blockquote><p>[!note]<br>真实换行<strong>正文</strong></p><pre><code>示例代码</code></pre></blockquote>
                    <blockquote><p>[!caution]- 可折叠警告<br>折叠后的正文</p></blockquote>""", {}, {})
            }
        } }
        compose.onNodeWithText("Note").assertIsDisplayed()
        compose.onNodeWithText("真实换行正文").assertIsDisplayed()
        compose.onNodeWithText("示例代码").assertIsDisplayed()
        compose.onNodeWithTag("article-callout-root-0").assert(hasAnyDescendant(hasText("真实换行正文")))
        compose.onNodeWithText("折叠后的正文").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开提示").performClick()
        compose.onNodeWithText("折叠后的正文").assertIsDisplayed()
        capture("callout-real-linebreak")
        compose.onNodeWithContentDescription("收起提示").performClick()
        compose.onNodeWithText("折叠后的正文").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("展开提示").assertCountEquals(1)
    }

    @Test fun mixedThreeLevelListsKeepNumberingIndentationAndChecklistStateOnNarrowDarkScreen() {
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Column(Modifier.fillMaxSize().widthIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(16.dp).testTag("markdown-fixture")) {
                RichContent("""<ol start="3"><li>一级<strong>粗体</strong><ul><li>二级项目<ol><li>三级项目</li></ol></li></ul></li>
                    <li><span class="chcklst-box checked fa fa-square-check-o"></span>完成事项</li>
                    <li><span class="chcklst-box fa fa-square-o"></span>待办事项</li></ol>""", {}, {})
            }
        } }
        val first = compose.onNodeWithText("一级粗体").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("二级项目").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText("三级项目").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(second.left > first.left && third.left > second.left)
        assertTrue(second.top > first.bottom && third.top > second.bottom)
        compose.onNodeWithText("3.").assertIsDisplayed()
        compose.onNodeWithText("1.").assertIsDisplayed()
        compose.onNodeWithTag("article-list-item-root-0-item1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已完成"))
        compose.onNodeWithTag("article-list-item-root-0-item2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未完成"))
        compose.onNodeWithText("待办事项").assertIsDisplayed()
        capture("lists-dark-large")
    }

    @Test fun bbcodeColorsKeepBoldAndClickableLinksAcrossThemes() {
        var dark by mutableStateOf(false)
        val opened = mutableListOf<String>()
        compose.setContent { KudoTheme(DisplaySettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, fontScale = if (dark) 1.3f else 1f)) {
            Column(Modifier.fillMaxSize().padding(16.dp).testTag("markdown-fixture")) {
                RichContent("""<p><span style="color:red">红色<strong>粗体</strong></span> <span style="color:#ff9900">橙色文字</span></p>
                    <p><span style="color:red"><a href="https://example.org/color">彩色链接</a></span></p>""", {}, { opened += it })
            }
        } }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark = isDark }
            val text = compose.onNodeWithText("红色粗体 橙色文字").fetchSemanticsNode().config[SemanticsProperties.Text].single()
            assertTrue(text.spanStyles.any { it.item.color == Color.Red })
            assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
            val pixels = compose.onNodeWithTag("markdown-fixture").captureToImage().toPixelMap()
            var red = 0
            var orange = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.red > .9f && pixel.green < .1f && pixel.blue < .1f) red++
                if (pixel.red > .9f && pixel.green in .5f.. .7f && pixel.blue < .1f) orange++
            }
            assertTrue("红字像素不足：$red", red > 30)
            assertTrue("橙字像素不足：$orange", orange > 30)
            capture(if (isDark) "colors-dark-large" else "colors-light")
            clickText("彩色链接", "彩")
        }
        compose.runOnIdle { assertEquals(listOf("https://example.org/color", "https://example.org/color"), opened) }
    }

    @Test fun footnoteDialogOutgrowsShortLazyItemAndCloseBackAndLinkKeepReadingPosition() {
        lateinit var list: LazyListState
        var blocked = false
        val opened = mutableListOf<String>()
        val label = "正文脚注[1]重复[1]缺失[9]"
        compose.setContent { KudoTheme(DisplaySettings()) { ArticleFootnoteHost {
            list = rememberLazyListState(initialFirstVisibleItemIndex = 18, initialFirstVisibleItemScrollOffset = 13)
            val readingBlocked = LocalReadingBlocked.current
            SideEffect { blocked = readingBlocked }
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("markdown-fixture"), state = list, contentPadding = PaddingValues(16.dp)) {
                items(50) { index ->
                    if (index == 20) RichContent("""<p>正文脚注<sup class="footnote-ref"><a href="#fn1">[1]</a></sup>重复<sup class="footnote-ref"><a href="#fn1">[1]</a></sup>缺失<sup class="footnote-ref"><a href="#fn9">[9]</a></sup></p>
                        <hr class="footnotes-sep"><ol class="footnotes-list"><li id="fn1" class="footnote-item"><p>这是就地脚注 <strong>粗体内容</strong></p><p>脚注第二段保留富文本与阅读上下文。</p><p><a href="https://example.org/footnote">脚注链接</a> <a href="#fnref1" class="footnote-backref">↩︎</a></p></li></ol>""", {}, { opened += it })
                    else androidx.compose.material3.Text("第 $index 行", Modifier.height(44.dp))
                }
            }
        } } }
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        val sourceHeight = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.height
        clickText(label, "[1]")
        compose.onNodeWithText("脚注 1").assertIsDisplayed()
        compose.onNodeWithText("这是就地脚注 粗体内容").assertIsDisplayed()
        compose.onNodeWithText(label).assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("liquid-dialog").fetchSemanticsNode().boundsInRoot.height > sourceHeight * 3)
        compose.runOnIdle { assertTrue(blocked); assertTrue(opened.isEmpty()) }
        capture("footnote-light", "liquid-dialog")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset); assertFalse(blocked) }
        clickText(label, "重复[1]", 3)
        compose.onNodeWithText("脚注 1").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
        clickText(label, "[9]")
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
        clickText(label, "[1]")
        clickText("脚注链接", "脚")
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("https://example.org/footnote"), opened); assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
    }

    @Test fun sameNumberInDifferentPostsIsIsolatedAndLeavingSourceClosesDialog() {
        var showSecond by mutableStateOf(true)
        var updated by mutableStateOf(false)
        val opened = mutableListOf<String>()
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) { ArticleFootnoteHost {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("markdown-fixture")) {
                RichContent(footnoteHtml("第一帖", "第一帖的脚注"), {}, {})
                if (showSecond) RichContent(footnoteHtml("第二帖", "第二帖的脚注"), {},
                    if (updated) ({ opened += "新:$it" }) else ({ opened += "旧:$it" }))
            }
        } } }
        clickText("第一帖[1]", "[1]")
        compose.onNodeWithText("第一帖的脚注").assertIsDisplayed()
        compose.onNodeWithText("第二帖的脚注").assertDoesNotExist()
        compose.onNodeWithText("关闭").performClick()
        clickText("第二帖[1]", "[1]")
        compose.onNodeWithText("第二帖的脚注").assertIsDisplayed()
        compose.onNodeWithText("第一帖的脚注").assertDoesNotExist()
        capture("footnote-dark-large", "liquid-dialog")
        compose.runOnIdle { updated = true }
        clickText("脚注链接", "脚")
        compose.runOnIdle { assertEquals(listOf("新:https://example.org/footnote"), opened) }
        clickText("第二帖[1]", "[1]")
        compose.runOnIdle { showSecond = false }
        compose.onNodeWithTag("liquid-dialog").assertDoesNotExist()
    }

    private fun footnoteHtml(label: String, body: String) = """<p>$label<sup class="footnote-ref"><a href="#fn1">[1]</a></sup></p><ol class="footnotes-list"><li id="fn1"><p>$body</p><p><a href="https://example.org/footnote">脚注链接</a></p></li></ol>"""

    private fun clickText(label: String, target: String, offset: Int = 1) {
        val node = compose.onNodeWithText(label)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        node.performTouchInput { click(layouts.single().getBoundingBox(label.indexOf(target) + offset).center) }
    }

    private fun capture(name: String, tag: String = "markdown-fixture") {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "markdown-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
