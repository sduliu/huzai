package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * board：板块资讯解析单测。
 *
 * Fixture 取自 2026-10 实测响应（games.mobileapi basketballapi/news/v2/newsList?tagCode=nba），
 * 裁剪为三条代表性条目：
 *   [0] IMG_TEXT 置顶资讯，badge 含「置顶」，tid 直出
 *   [1] LINK 运营位（赛季倒计时卡），tid 为空，link 为 kanqiu:// scheme
 *   [2] 普通 IMG_TEXT
 * 以及 m.hupu.com/soccer SSR 的 __NEXT_DATA__ 片段。
 */
class HupuBoardParseTest {

    // ---- newsList fixtures ----

    private val pageOk = """
        {"success":true,"result":{
          "ad_page_id":"nba.news","topNewsCount":2,
          "data":[
            {"nid":"23107860","tid":"642969826","type":"IMG_TEXT",
             "title":"波尔津吉斯个人回应健康状况：正在好转，我没有HIV或艾滋病",
             "img":"https://i11.hoopchina.com.cn/news-editor/x.png",
             "link":"kanqiu://bbs/topic/642969826","replies":484,"lights":50,
             "publishTime":"2026-10-15 10:23:11","addTime":1760664693,
             "top":true,"showComment":true,
             "badge":[{"name":"置顶","color":"ff3f4c"}]},
            {"nid":"23122025","tid":null,"type":"LINK","linkType":"CUSTOM",
             "title":"巨星之子：继承巨星天赋踏上职业赛场写下超越父辈的传奇",
             "img":"","replies":0,"lights":0,"publishTime":"","addTime":0,"top":false,
             "link":"huputiyu://webview/openencodeurl?fullscreen=0&url=https%3A%2F%2Factivity-static.hupu.com%2Fshaper%2Fpublished%2Fapp_100f38e0ac%2Findex.html"},
            {"nid":"23171031","tid":"642974251","type":"IMG_TEXT",
             "title":"布兰登-威廉姆斯：勇士从1到18人都被尊重，像一个大家庭",
             "img":"https://i1.hoopchina.com.cn/x.png",
             "link":"kanqiu://bbs/topic/642974251","replies":0,"lights":0,
             "publishTime":"2026-10-14 18:02:01","addTime":1760661600,"top":false}
          ]}}
    """.trimIndent()

    /** 成功但 data 为空（实测 tagCode=football 等无数据板块返回） */
    private val pageEmpty = """
        {"success":true,"result":{"ad_page_id":"","topNewsCount":0,"data":[]}}
    """.trimIndent()

    /** 服务端报错（实测未知 tagCode / 参数错误返回） */
    private val pageFail = """{"success":false,"code":400,"msg":"参数错误"}"""

    // ---- soccer SSR fixture（__NEXT_DATA__ 片段，pageProps 仅保留 news）----

    private val soccerHtml = """
        <html><body>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"news":[
          {"nid":"23179049","title":"TA：曼城女足未被调查","img":"https://i11.hoopchina.com.cn/x.png",
           "link":"https://m.hupu.com/bbs/642976843","type":"IMG_TEXT","lights":0,"replies":2,
           "publishTime":"2026-10-15 12:44:50","tid":"642976843",
           "badge":[{"name":"置顶","color":"ff3f4c"}]},
          {"nid":"23176042","title":"登贝莱恩佐无处不在","img":"",
           "link":"https://m.hupu.com/bbs/642975551","type":"IMG_TEXT","lights":0,"replies":9,
           "publishTime":"2026-10-15 11:00:00","tid":""}
        ],"schedules":[]}}}
        </script>
        </body></html>
    """.trimIndent()

    private val soccerHtmlNoNext = "<html><body><div>shell</div></body></html>"

    // ---- parseBoardNews ----

    @Test
    fun boardNews_parsesItemsAndFields() {
        val page = HupuBoardParser.parseBoardNews(pageOk)
        assertNotNull(page)
        page!!
        assertEquals(3, page.items.size)
        assertEquals(2, page.topNewsCount)
        assertEquals(3, page.nextPreCount)          // 0 + 3
        assertEquals("23107860", page.anchorNid)    // 首条 nid 作锚点

        val a = page.items[0]
        assertEquals("23107860", a.nid)
        assertEquals("642969826", a.tid)
        assertEquals("IMG_TEXT", a.type)
        assertTrue(a.title.contains("波尔津吉斯"))
        assertEquals(484, a.replies)
        assertEquals(50, a.lights)
        assertTrue(a.top)
        assertTrue(a.openableAsThread)
        assertTrue(a.badges.contains("置顶"))
        assertEquals("kanqiu://bbs/topic/642969826", a.link)
    }

    @Test
    fun boardNews_linkSlotHasNoTid() {
        val page = HupuBoardParser.parseBoardNews(pageOk)!!
        val slot = page.items[1]
        assertEquals("LINK", slot.type)
        assertEquals("CUSTOM", slot.linkType)
        assertFalse(slot.openableAsThread)          // 运营位无 tid，不可当帖子开
        assertTrue(slot.link.startsWith("huputiyu://webview/"))
    }

    @Test
    fun boardNews_tidExtractedFromLink() {
        // tid 字段为空字符串时从 kanqiu://bbs/topic/{tid} 兜底提取
        val json = pageOk.replace("\"tid\":\"642974251\"", "\"tid\":\"\"")
        val page = HupuBoardParser.parseBoardNews(json)!!
        assertEquals("642974251", page.items[2].tid)
    }

    @Test
    fun boardNews_emptyDataIsEmptyPageNotNull() {
        val page = HupuBoardParser.parseBoardNews(pageEmpty)
        assertNotNull(page)
        assertTrue(page!!.items.isEmpty())
        assertEquals(0, page.nextPreCount)
        assertEquals("", page.anchorNid)
    }

    @Test
    fun boardNews_failureIsNull() {
        assertNull(HupuBoardParser.parseBoardNews(pageFail))
        assertNull(HupuBoardParser.parseBoardNews("not json"))
        assertNull(HupuBoardParser.parseBoardNews(""))
        assertNull(HupuBoardParser.parseBoardNews("{\"success\":true}"))   // 无 result
    }

    @Test
    fun boardNews_paginationCursorAccumulates() {
        val page = HupuBoardParser.parseBoardNews(pageOk, basePreCount = 20)
        assertEquals(23, page!!.nextPreCount)        // 20 + 3
    }

    // ---- parseSoccerNews ----

    @Test
    fun soccerNews_parsesFromNextData() {
        val items = HupuBoardParser.parseSoccerNews(soccerHtml)
        assertEquals(2, items.size)

        val a = items[0]
        assertEquals("23179049", a.nid)
        assertEquals("642976843", a.tid)
        assertTrue(a.openableAsThread)
        assertTrue(a.top)                            // badge 含「置顶」→ top
        assertTrue(a.badges.contains("置顶"))

        // tid 空 → 从 https://m.hupu.com/bbs/{tid} link 提取
        assertEquals("642975551", items[1].tid)
    }

    @Test
    fun soccerNews_missingNextDataReturnsEmpty() {
        assertTrue(HupuBoardParser.parseSoccerNews(soccerHtmlNoNext).isEmpty())
        assertTrue(HupuBoardParser.parseSoccerNews("").isEmpty())
        assertTrue(HupuBoardParser.parseSoccerNews("not html").isEmpty())
    }
}
