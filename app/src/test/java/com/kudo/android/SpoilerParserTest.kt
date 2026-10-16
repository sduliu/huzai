package com.kudo.android

import androidx.compose.ui.graphics.Color
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Test

class SpoilerParserTest {
    @Test fun blockSpoilerKeepsItsImageInsideAndOutOfTheOrdinaryAlbum() {
        val blocks = parseArticle("""<div class="spoiler"><p>答案<img class="emoji" src="/emoji.png" alt=":ok:"></p><img src="/secret.png"></div><img src="/public.png">""")
        assertEquals("Spoiler", blocks.first().javaClass.simpleName)
        assertEquals(listOf("https://linux.do/public.png"), blocks.articleImages().map { it.url })
    }
    @Test fun imageSpoilerInsideAParagraphDoesNotEscapeItsWrapper() {
        val blocks = parseArticle("""<p>前文<span class="spoiler"><a class="lightbox" href="/original.png"><img src="/small.png"></a></span>后文</p>""")
        assertTrue(blocks.any { it.javaClass.simpleName == "Spoiler" })
        assertTrue(blocks.articleImages().isEmpty())
        assertEquals("前文后文", blocks.filterIsInstance<ArticleBlock.TextBlock>().joinToString("") { it.html })
    }
    @Test fun inlineSpoilerKeepsTheSentenceAndItsEmojiInOneParagraph() {
        val blocks = parseArticle("""<p>前<span class="spoiler">秘密<img class="emoji" src="/emoji.png" alt=":ok:"></span>后</p>""")
        assertEquals(1, blocks.size)
        val text = parseInlineArticle((blocks.single() as ArticleBlock.TextBlock).html, Color.Blue)
        assertEquals("前秘密\uFFFC后", text.text.text)
        assertEquals(1, text.emojis.size)
        assertTrue(text.text.getStringAnnotations("spoiler", 0, text.text.length).isNotEmpty())
    }
    @Test fun codeContainingSpoilerSyntaxRemainsLiteralCode() {
        assertEquals(ArticleBlock.Code("[spoiler]literal[/spoiler]"), parseArticle("<pre>[spoiler]literal[/spoiler]</pre>").single())
    }
    @Test fun multipleInlineSpoilersAreIndependentAndRedactOnlyTheirOwnRanges() {
        val inline = parseInlineArticle("前<span class=spoiler>甲</span>中<span class=spoiler>乙</span>后", Color.Blue)
        val ranges = inline.text.getStringAnnotations("spoiler", 0, inline.text.length)
        assertEquals(2, ranges.size)
        assertNotEquals(ranges[0].item, ranges[1].item)
        assertEquals("前剧透内容中剧透内容后", redactedSpoilerText(inline.text, ranges))
        assertEquals("前甲中剧透内容后", redactedSpoilerText(inline.text, ranges.drop(1)))
    }
    @Test fun spoilerInAQuoteStaysGroupedAndNeverEntersThePublicAlbum() {
        val quote = parseArticle("""<blockquote><div class="spoiler"><p>答案</p><img src="/secret.png"></div></blockquote>""").single() as ArticleBlock.Quote
        assertEquals("Spoiler", quote.blocks.single().javaClass.simpleName)
        assertTrue(listOf(quote).articleImages().isEmpty())
    }
}
