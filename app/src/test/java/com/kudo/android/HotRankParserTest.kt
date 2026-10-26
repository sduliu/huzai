package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批F-4：官方热榜（m.hupu.com/hot）与 tag 聚合页（/tag/{id} + tagThreads API）解析。
 * Fixture 字段名/值取自 2026-10-26 匿名实测响应（rank/heat/tagName/topicTopInfo/threadList）。
 */
class HotRankParserTest {

    private fun nextData(pageProps: String) =
        "<html><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
            "{\"props\":{\"pageProps\":$pageProps}}</script></html>"

    @Test
    fun `hot tags parse rank name heat`() {
        val html = nextData(
            """{"res":[
              {"heat":48261099,"aggregationType":"ACTIVITY","tagId":159942,"useBusiness":1,
               "icon":"https://i10.hoopchina.com.cn/x.png","rank":1,"competitionType":"非赛事",
               "tagName":"如果乔丹在2003年选秀","tagUpdateDesc":"每10分钟更新一次","generatorType":"非算法生成"},
              {"heat":7690187,"tagId":159154,"rank":2,"competitionType":"非赛事",
               "tagName":"回到2009，我来打造勇士王朝","tagUpdateDesc":"每10分钟更新一次"}
             ],"totalElements":50}""",
        )
        val tags = HupuParser.parseHotTags(html)
        assertEquals(2, tags.size)
        assertEquals(159942, tags[0].tagId.toInt())
        assertEquals("如果乔丹在2003年选秀", tags[0].name)
        assertEquals(48261099L, tags[0].heat)
        assertEquals(1, tags[0].rank)
        assertEquals("每10分钟更新一次", tags[0].updateDesc)
        assertEquals("非赛事", tags[0].competitionType)
    }

    @Test
    fun `hot tags degrade on missing res`() {
        assertTrue(HupuParser.parseHotTags(nextData("""{"other":1}""")).isEmpty())
        assertTrue(HupuParser.parseHotTags("<html>no data</html>").isEmpty())
    }

    @Test
    fun `tag page parses top info and first page threads`() {
        val html = nextData(
            """{"tagId":159942,
               "topicTopInfo":{"tagId":159942,"name":"如果乔丹在2003年选秀","icon":"i.png",
                 "banner":"b.png","bannerRgb":"#1b51c8","description":null,"tNum":26620,"pv":399375,
                 "aggregationType":"ACTIVITY","isTopic":true,"followNum":1},
               "topicThreadList":{"nextPage":true,"stamp":null,"cursor":null,
                 "threadList":[
                   {"tid":643282053,"puid":35332848,"userName":"打球不要太帅","header":"h.jpg",
                    "time":"昨天 21:41","title":"把乔丹放进2003年，我打出了这样的生涯",
                    "summary":"我在《巨星穿越时空》扮演…","recommendCount":0,"shareNum":0,
                    "replies":1,"contentType":1,"picList":[{"url":"p1.jpg"},{"url":"p2.jpg"}],"video":null}
                 ]}}""",
        )
        val tp = HupuParser.parseTagPage(html)
        assertNotNull(tp)
        tp!!
        assertEquals("如果乔丹在2003年选秀", tp.info.name)
        assertEquals("#1b51c8", tp.info.bannerRgb)
        assertEquals(26620L, tp.info.tNum)
        assertEquals(399375L, tp.info.pv)
        assertTrue(tp.nextPage)
        assertEquals(1, tp.page)
        assertEquals(1, tp.threads.size)
        val t = tp.threads[0]
        assertEquals("643282053", t.tid)
        assertEquals("打球不要太帅", t.userName)
        assertEquals(2, t.pics.size)
        assertEquals("p1.jpg", t.pics[0])
    }

    @Test
    fun `tag page null without top info`() {
        assertNull(HupuParser.parseTagPage(nextData("""{"res":[]}""")))
    }

    @Test
    fun `tagThreads api json parses page and honors code`() {
        val ok = """{"data":{"nextPage":true,"stamp":null,"threadList":[
            {"tid":643294545,"userName":"x","header":"","userHeader":"u.jpg","time":"今天",
             "title":"第二页首帖","summary":"s","recommendCount":3,"shareNum":1,"replies":9,"picList":[]}
        ]},"code":200,"msg":"success"}"""
        val p = HupuParser.parseTagThreads(ok, page = 2)
        assertNotNull(p)
        assertEquals(2, p!!.page)
        assertTrue(p.nextPage)
        assertEquals("643294545", p.threads[0].tid)
        assertEquals("u.jpg", p.threads[0].header) // userHeader 兜底
        assertFalse(HupuParser.parseTagThreads("""{"code":400001,"data":"","msg":"参数类型错误"}""", 2) != null)
        assertNull(HupuParser.parseTagThreads("not json", 2))
    }
}
