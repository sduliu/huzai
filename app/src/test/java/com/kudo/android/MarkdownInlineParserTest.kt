package com.kudo.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownInlineParserTest {
    @Test fun siteBbcodeColorsKeepNestedFormattingAndLinks() {
        val article = parseInlineArticle("""<span style="color:red">红<strong>字</strong> <a href="https://linux.do/">链接</a><span style="color:#ff9900">橙字</span></span>普通""", Color.Blue)
        assertEquals("红字 链接橙字普通", article.text.text)
        assertTrue(article.text.spanStyles.any { it.item.color == Color.Red })
        assertTrue(article.text.spanStyles.any { it.item.color == Color(0xFFFF9900) })
        assertTrue(article.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertEquals(1, article.text.getLinkAnnotations(0, article.text.length).size)
    }

    @Test fun cssColorSyntaxUsesCssAlphaAndRejectsInvalidValues() {
        assertEquals(Color.Red, articleColor("RED"))
        assertEquals(Color(0xFF663399), articleColor("rebeccapurple"))
        assertEquals(Color(0xFFFF9900), articleColor("#f90"))
        assertEquals(Color(0x88FF9900), articleColor("#ff990088"))
        assertEquals(Color(1f, 0f, 0f, .5f), articleColor("rgba(255,0,0,0.5)"))
        assertEquals(Color.Red, articleColor("rgb(100%,0%,0%)"))
        listOf("url(https://bad.test/)", "var(--color)", "#12", "#gg0000", "rgb(999,0,0)", "rgba(0,0,0,NaN)").forEach {
            assertNull(it, articleColor(it))
        }
        val article = parseInlineArticle("""<span style="color:expression(alert(1))"><strong>正文</strong></span>""", Color.Blue)
        assertEquals("正文", article.text.text)
        assertTrue(article.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }
}
