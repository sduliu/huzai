package com.kudo.android

import com.kudo.android.ui.topic.ArticleBlock
import com.kudo.android.ui.topic.parseArticle
import com.kudo.android.ui.topic.articleImages
import com.kudo.android.ui.topic.parseInlineArticle
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class QuoteParserTest {
    private val actualQuote = """<aside class="quote no-group" data-username="babiwei" data-display-name="芭比威" data-post="1" data-topic="3002436">
        <div class="title"><div class="quote-controls"></div><img alt="" width="24" height="24" src="https://cdn.ldstatic.com/user_avatar/linux.do/babiwei/48/296965_2.png" class="avatar"> 芭比威:</div>
        <blockquote><p>电子称重勺</p></blockquote></aside>"""

    @Test fun quoteAuthorAvatarNeverBecomesAStandaloneBodyImage() {
        val blocks = parseArticle(actualQuote + "<p>挺有意思的产品啊</p>")
        assertTrue("引用标题的头像不能进入正文大图", blocks.filterIsInstance<ArticleBlock.Image>().isEmpty())
    }

    @Test fun authorAndQuotedParagraphStayInOneBlockBeforeTheReply() {
        val blocks = parseArticle(actualQuote + "<p>挺有意思的产品啊</p>")
        assertEquals("一块完整引用加一段回复", 2, blocks.size)
        assertEquals("挺有意思的产品啊", (blocks.last() as ArticleBlock.TextBlock).html)
    }

    @Test fun multiParagraphBlockquoteRemainsOneGroup() {
        assertEquals(1, parseArticle("<blockquote><p>第一段</p><p>第二段</p></blockquote>").size)
    }

    @Test fun realQuoteKeepsAuthorSmallAvatarBodyAndExactOriginalPost() {
        val quote = parseArticle(actualQuote).single() as ArticleBlock.Quote
        assertEquals("芭比威", quote.author)
        assertEquals("https://cdn.ldstatic.com/user_avatar/linux.do/babiwei/48/296965_2.png", quote.avatar)
        assertEquals("https://linux.do/t/topic/3002436/1", quote.source)
        assertEquals("电子称重勺", (quote.blocks.single() as ArticleBlock.TextBlock).html)
        assertTrue(listOf(quote).articleImages().isEmpty())
    }

    @Test fun nestedQuotesKeepInlineEmojiAndAllPhotoOccurrencesInOrder() {
        val blocks = parseArticle("""<img src="/first.png"><aside class="quote" data-username="outer"><div class="title"><img class="avatar" src="/avatar.png">Outer:</div><blockquote>
            <p>原文<img class="emoji" src="/emoji.png" alt=":ok:"></p><img src="/repeat.png">
            <aside class="quote" data-username="inner"><div class="title">Inner:</div><blockquote><img src="/repeat.png"></blockquote></aside>
            </blockquote></aside><img src="/last.png">""")
        assertEquals(listOf("first.png", "repeat.png", "repeat.png", "last.png"), blocks.articleImages().map { it.url.substringAfterLast('/') })
        val quote = blocks[1] as ArticleBlock.Quote
        assertEquals("Outer", quote.author)
        val inline = parseInlineArticle((quote.blocks.first() as ArticleBlock.TextBlock).html, Color.Blue)
        assertEquals("原文\uFFFC", inline.text.text)
        assertEquals("https://linux.do/emoji.png", inline.emojis.values.single().url)
        assertEquals("Inner", (quote.blocks.last() as ArticleBlock.Quote).author)
    }

    @Test fun bareTextAndMissingHeadersRemainReadableWithoutInventedNavigation() {
        val plain = parseArticle("<blockquote>没有段落包装的引用<strong>加粗</strong></blockquote>").single() as ArticleBlock.Quote
        assertTrue(plain.blocks.isNotEmpty())
        assertNull(plain.source)
        val invalid = parseArticle("""<aside class="quote" data-username="fallback" data-topic="-1" data-post="javascript:bad"><div class="title"><img class="avatar" src="http://insecure.test/a.png"></div><blockquote><p>正文</p><script>bad()</script></blockquote></aside>""").single() as ArticleBlock.Quote
        assertEquals("fallback", invalid.author)
        assertNull(invalid.source)
        assertNull(invalid.avatar)
        assertEquals("正文", (invalid.blocks.single() as ArticleBlock.TextBlock).html)
    }

    @Test fun oneboxRemainsAnEmbedAndDoesNotBecomeAQuote() {
        val block = parseArticle("""<aside class="onebox"><header><a href="https://example.com/article">文章标题</a></header></aside>""").single()
        assertEquals(ArticleBlock.Embed("https://example.com/article", "文章标题"), block)
    }
}
