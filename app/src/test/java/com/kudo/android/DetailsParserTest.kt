package com.kudo.android

import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Test

class DetailsParserTest {
    @Test fun updateLogRemainsOneContainerInsteadOfFlattenedParagraphs() {
        val blocks = parseArticle("""<details><summary>v1.7.0更新日志</summary><p>加入catppuccin全家桶配色！</p><img src="/theme.png" width="565" height="500"></details>""")
        assertEquals("折叠标题、段落和图片必须保持同一个容器", 1, blocks.size)
        val details = blocks.single() as ArticleBlock.Details
        assertEquals("v1.7.0更新日志", details.summary)
        assertFalse(details.open)
        assertEquals(listOf("https://linux.do/theme.png"), blocks.articleImages().map { it.url })
    }

    @Test fun nestedDetailsRetainDefaultStateAndStablePhotoOrderAcrossQuotes() {
        val blocks = parseArticle("""<details open><summary><b>外层</b></summary><p>正文</p><blockquote><details><summary>内层</summary><img src="/repeat.png"></details><img src="/repeat.png"></blockquote></details><img src="/last.png">""")
        val outer = blocks.first() as ArticleBlock.Details
        assertTrue(outer.open)
        assertEquals("<b>外层</b>", outer.summary)
        val inner = (outer.blocks.last() as ArticleBlock.Quote).blocks.first() as ArticleBlock.Details
        assertFalse(inner.open)
        assertEquals(listOf("repeat.png", "repeat.png", "last.png"), blocks.articleImages().map { it.url.substringAfterLast('/') })
    }

    @Test fun missingSummaryFallsBackWithoutDroppingBodyOrExecutingScripts() {
        val block = parseArticle("<details><p>内容</p><script>bad()</script></details>").single() as ArticleBlock.Details
        assertEquals("详细内容", block.summary)
        assertEquals(listOf(ArticleBlock.TextBlock("内容", headingText = "内容")), block.blocks)
    }
}
