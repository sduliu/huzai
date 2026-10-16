package com.kudo.android

import com.kudo.android.ui.topic.*
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SvgSignatureTest {
    @Test fun svgImagePathsAcceptQueriesAndCaseButRejectOtherFormatsAndSchemes() {
        listOf("https://cdn.test/signature.svg", "https://cdn.test/signature.SVG?token=a&v=2#image").forEach {
            assertTrue(isSvgSignature(it))
            assertTrue(isSvgSignature((signatureBlocks(it).single() as ArticleBlock.Image).url))
        }
        listOf("https://cdn.test/a.png?name=a.svg", "https://cdn.test/a.svg.png", "http://cdn.test/a.svg", "file:///a.svg", "").forEach {
            assertFalse(isSvgSignature(it))
        }
    }

    @Test fun imageShellPreservesQueryAndNeverInterpolatesMarkupAsElements() {
        val url = "https://cdn.test/a.svg?x=1&quote=\"<tag>"
        val document = Jsoup.parse(signatureImageDocument(url))
        assertEquals(url, document.selectFirst("img")!!.attr("src"))
        assertEquals(1, document.select("img").size)
        assertTrue(document.select("tag,script,iframe,object").isEmpty())
        val policy = document.selectFirst("meta[http-equiv=Content-Security-Policy]")!!.attr("content")
        assertTrue(policy.contains("script-src 'none'"))
        assertTrue(policy.contains("default-src 'none'"))
    }
}
