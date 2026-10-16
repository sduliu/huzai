package com.kudo.android

import androidx.compose.ui.graphics.Color
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownListParserTest {
    @Test fun orderedAndNestedListsRetainIndependentItemsAndNumbering() {
        val list = parseArticle("""<ol start="3"><li>第一项<ul><li>子项<ol><li>三级</li></ol></li></ul></li><li value="8">第二项</li><li>第三项</li></ol>""").single() as ArticleBlock.ListBlock
        assertEquals(listOf("3.", "8.", "9."), list.items.map { it.marker })
        assertEquals("第一项", plain(list.items[0].blocks[0]))
        val nested = list.items[0].blocks[1] as ArticleBlock.ListBlock
        assertEquals("子项", plain(nested.items[0].blocks[0]))
        assertEquals("三级", plain(((nested.items[0].blocks[1] as ArticleBlock.ListBlock).items[0].blocks[0])))
    }

    @Test fun actualSiteChecklistPreservesUncheckedCheckedAndPermanentState() {
        val list = parseArticle("""<ul><li><span class="chcklst-box fa fa-square-o" data-chk-src="4:0"></span> 待办项</li><li><span class="chcklst-box checked fa fa-square-check-o" data-chk-src="5:0"></span> 已完成</li><li><span class="chcklst-box checked permanent fa fa-square-check"></span> 永久完成</li></ul>""").single() as ArticleBlock.ListBlock
        assertEquals(listOf(false, true, true), list.items.map { it.checked })
        assertEquals(listOf("待办项", "已完成", "永久完成"), list.items.map { plain(it.blocks.single()) })
    }

    @Test fun childCheckboxCannotChangeParentAndListImagesKeepTheirGalleryOrder() {
        val list = parseArticle("""<ul><li><p>父项</p><ul><li><input type="checkbox" checked>子任务<img src="/child.png"></li></ul><details><summary>更多</summary><img src="/detail.png"></details></li></ul>""").single() as ArticleBlock.ListBlock
        assertNull(list.items[0].checked)
        val child = list.items[0].blocks[1] as ArticleBlock.ListBlock
        assertEquals(true, child.items[0].checked)
        assertEquals(listOf("https://linux.do/child.png", "https://linux.do/detail.png"), listOf(list).articleImages().map { it.url })
    }

    private fun plain(block: ArticleBlock) = parseInlineArticle((block as ArticleBlock.TextBlock).html, Color.Blue).text.text
}
