package com.kudo.android

import com.kudo.android.hupu.HupuDestination
import com.kudo.android.hupu.HupuLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批P：正文话题词/站内链接 → 应用内目的地分类。 */
class HupuLinkTest {
    @Test
    fun threadUrl() {
        val d = HupuLink.destination("https://bbs.hupu.com/643300337.html")
        assertTrue(d is HupuDestination.Thread)
        assertEquals("643300337", (d as HupuDestination.Thread).tid)
    }

    @Test
    fun threadUrlWithBoardPrefixAndPage() {
        val d = HupuLink.destination("https://bbs.hupu.com/all-nba/643300337-2.html")
        assertEquals("643300337", (d as HupuDestination.Thread).tid)
    }

    @Test
    fun threadUrlMobile() {
        assertEquals("39559121", (HupuLink.destination("https://m.hupu.com/bbs/39559121") as HupuDestination.Thread).tid)
        assertEquals("39559121", (HupuLink.destination("https://m.hupu.com/bbs/39559121.html") as HupuDestination.Thread).tid)
    }

    @Test
    fun threadUrlRelative() {
        assertEquals("643300337", (HupuLink.destination("/643300337.html") as HupuDestination.Thread).tid)
    }

    @Test
    fun boardUrl() {
        assertEquals("/all-nba", (HupuLink.destination("https://bbs.hupu.com/all-nba") as HupuDestination.Board).boardUrl)
        assertEquals("/502", (HupuLink.destination("/502") as HupuDestination.Board).boardUrl)
        assertEquals("/ent", (HupuLink.destination("https://m.hupu.com/bbs/ent") as HupuDestination.Board).boardUrl)
    }

    @Test
    fun userUrl() {
        assertEquals("someone", (HupuLink.destination("https://my.hupu.com/someone") as HupuDestination.User).euid)
        assertEquals("someone", (HupuLink.destination("https://m.hupu.com/user/someone") as HupuDestination.User).euid)
    }

    @Test
    fun tagUrl() {
        assertEquals("184", (HupuLink.destination("https://m.hupu.com/tag/184") as HupuDestination.Tag).tagId)
        assertEquals("184", (HupuLink.destination("huputiyu://bbs/subject/184") as HupuDestination.Tag).tagId)
        assertEquals("184", (HupuLink.destination("https://m.hupu.com/bbs/topicTag?tagId=184") as HupuDestination.Tag).tagId)
    }

    @Test
    fun nonHupuOrUnresolvable() {
        assertNull(HupuLink.destination("https://example.com/x"))
        assertNull(HupuLink.destination("https://weibo.com/abc"))
        assertNull(HupuLink.destination("javascript:void(0)"))
        assertNull(HupuLink.destination("#reply"))
        assertNull(HupuLink.destination(""))
        assertNull(HupuLink.destination("mailto:a@b.c"))
        assertNull(HupuLink.destination("hupuapp://other/scheme"))
        // 多段非 html 路径不可判定
        assertNull(HupuLink.destination("https://bbs.hupu.com/all-nba/photo/123"))
        // www.hupu.com 资讯域不在 bbs 路由集内
        assertNull(HupuLink.destination("https://www.hupu.com/nba/news"))
    }
}
