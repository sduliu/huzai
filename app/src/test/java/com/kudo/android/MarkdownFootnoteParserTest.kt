package com.kudo.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.BaselineShift
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownFootnoteParserTest {
    @Test fun actualSiteFootnoteReferencesStaySuperscriptAndNeverBecomeWebUrls() {
        val article = parseInlineArticle("""<p>正文<sup class="footnote-ref"><a href="#fn1" id="fnref1">[1]</a></sup></p>""", Color.Blue)
        assertEquals("正文[1]", article.text.text)
        assertTrue(article.text.spanStyles.any { it.item.baselineShift == BaselineShift.Superscript && it.start == 2 })
        assertTrue(article.text.getLinkAnnotations(0, article.text.length).none { it.item is LinkAnnotation.Url })
    }

    @Test fun definitionsAndTheirSeparatorDoNotDuplicateTheArticleBody() {
        val blocks = parseArticle("""<p>正文</p><hr class="footnotes-sep"><ol class="footnotes-list"><li id="fn1" class="footnote-item"><p>脚注内容 <a href="#fnref1" class="footnote-backref">↩︎</a></p></li></ol>""")
        assertEquals(1, blocks.size)
        assertEquals("正文", parseInlineArticle((blocks.single() as ArticleBlock.TextBlock).html, Color.Blue).text.text)
    }

    @Test fun bothSiteFootnoteFormsKeepRichContentWithoutBacklinks() {
        val notes = articleFootnotes("""<ol class="footnotes-list">
            <li id="fn1" class="footnote-item"><p>就地脚注 <strong>粗体</strong> <a href="#fnref1" class="footnote-backref">↩︎</a></p></li>
            <li id="fn2" class="footnote-item"><p>编号脚注 <a href="https://linux.do/">链接</a> <a href="#fnref2" class="footnote-backref">↩︎</a></p></li>
            </ol>""")
        assertEquals(listOf("fn1", "fn2"), notes.keys.toList())
        assertEquals("2", notes.getValue("fn2").label)
        assertTrue(notes.getValue("fn1").html.contains("<strong>粗体</strong>"))
        assertTrue(notes.getValue("fn2").html.contains("https://linux.do/"))
        assertFalse(notes.values.any { it.html.contains("footnote-backref") || it.html.contains("↩︎") })
    }

    @Test fun repeatedReferencesAreClickableButMissingDefinitionsNeverRouteToWeb() {
        val clicks = mutableListOf<String>()
        val article = parseInlineArticle("""正文<sup class="footnote-ref"><a href="#fn1">[1]</a></sup>再引<sup class="footnote-ref"><a href="#fn1">[1]</a></sup>缺失<sup class="footnote-ref"><a href="#fn9">[9]</a></sup><a href="https://linux.do/t/topic/1">话题</a>""", Color.Blue) { clicks += it }
        val links = article.text.getLinkAnnotations(0, article.text.length)
        assertEquals(3, links.count { it.item is LinkAnnotation.Clickable })
        links.mapNotNull { it.item as? LinkAnnotation.Clickable }.forEach { it.linkInteractionListener?.onClick(it) }
        assertEquals(listOf("fn1", "fn1", "fn9"), clicks)
        assertEquals("https://linux.do/t/topic/1", (links.single { it.item is LinkAnnotation.Url }.item as LinkAnnotation.Url).url)
        val otherPost = articleFootnotes("""<section class="footnotes"><ol><li id="fn1"><p>另一个帖子</p></li></ol></section>""")
        assertEquals("<p>另一个帖子</p>", otherPost.getValue("fn1").html)
        assertTrue(articleFootnotes("<p>没有脚注</p>").isEmpty())
    }
}
