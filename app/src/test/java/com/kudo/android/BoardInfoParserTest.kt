package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批L-2：版块档案补充——SSR admins 节点与 zoneData 成员数的解析验证 */
class BoardInfoParserTest {

    private fun topicHtml(adminsJson: String? = null): String {
        val adm = adminsJson ?: """{"title":"专区信息","adminApplicable":1,
            "adminList":[{"roleId":10,"roleName":"总版主","puid":1,"userName":"布丁先生",
            "header":"https://i1.hoopchina.com.cn/h.jpg","url":"https://my.hupu.com/u1"},
            {"roleId":18,"roleName":"三级版主","puid":2,"userName":"猫猫",
            "header":"https://i1.hoopchina.com.cn/h2.jpg","url":"https://my.hupu.com/u2"}]}"""
        return """<html><script>window.${'$'}${'$'}data={"topic":{"isLogin":false,
            "topic":{"topicId":"127","name":"湖人专区","url":"/lakers","desc":"欢迎","countText":"20.8w","logo":"l.png"},
            "tabs":[{"id":2,"title":"最新回复","url":"/lakers"}],
            "threads":{"count":1,"current":1,"total":9,
              "list":[{"tid":"1","title":"帖","url":"/1.html","lights":3,"replies":7}]},
            "page":1,"sort":2},"admins":$adm,"env":{}}</script></html>"""
    }

    @Test
    fun `admins node parses moderator list and recruit flag`() {
        val p = HupuParser.parseTopicPage(topicHtml())!!
        assertEquals(1, p.boardInfo.adminApplicable)
        assertEquals("专区信息", p.boardInfo.title)
        assertEquals(2, p.boardInfo.admins.size)
        assertEquals("总版主", p.boardInfo.admins[0].roleName)
        assertEquals("布丁先生", p.boardInfo.admins[0].userName)
        assertEquals("https://i1.hoopchina.com.cn/h.jpg", p.boardInfo.admins[0].header)
    }

    @Test
    fun `missing or empty admins node degrades to empty boardInfo`() {
        val p = HupuParser.parseTopicPage(topicHtml("null"))!!
        assertEquals(0, p.boardInfo.adminApplicable)
        assertTrue(p.boardInfo.admins.isEmpty())
        val p2 = HupuParser.parseTopicPage(topicHtml("""{"title":"专区信息","adminApplicable":0,"adminList":[]}"""))!!
        assertEquals(0, p2.boardInfo.adminApplicable)
        assertTrue(p2.boardInfo.admins.isEmpty())
    }

    @Test
    fun `zoneData parses member and thread counts`() {
        val json = """{"pageProps":{"zoneData":{"topicId":127,"name":"湖人专区",
            "followedUserNum":"277.5万","allThreadNum":"144.5万","bgColor":"#5F1B95",
            "desc":"欢迎来到湖人专区"}}}"""
        val z = HupuParser.parseZoneInfo(json)!!
        assertEquals("277.5万", z.memberText)
        assertEquals("144.5万", z.allThreadNum)
        assertEquals("#5F1B95", z.bgColor)
    }

    @Test
    fun `titleFont marker parses from thread list, absent degrades empty`() {
        val html = topicHtml("null").replace(
            """{"tid":"1","title":"帖","url":"/1.html","lights":3,"replies":7}""",
            """{"tid":"1","title":"帖","url":"/1.html","lights":3,"replies":7,"titleFont":"orange~1~~~~~"},""" +
                """{"tid":"2","title":"普通","url":"/2.html","lights":0,"replies":1}""",
        )
        val p = HupuParser.parseTopicPage(html)!!
        assertEquals("orange~1~~~~~", p.threads[0].titleFont)
        assertEquals("", p.threads[1].titleFont)
    }

    @Test
    fun `zoneData absent or malformed is safe`() {
        assertNull(HupuParser.parseZoneInfo("""{"pageProps":{}}"""))
        assertNull(HupuParser.parseZoneInfo("not-json"))
    }

    @Test
    fun `newsList subject link does not leak fake tid`() {
        // 批U-0a：huputiyu://news/topic/{nid} 是专题合集 id，旧 topic/ 正则误提为帖 tid
        // → 点按进「内容不存在」。收紧后该条目保持不可开帖（openableAsThread=false）。
        val json = """{"success":true,"result":{"data":[
            {"nid":"23327534","tid":"","type":"SUBJECT","title":"今日赛况","img":"i.jpg",
             "replies":3552,"lights":815,"link":"huputiyu://news/topic/23327534?enName=nba"},
            {"nid":"1","tid":"643310802","type":"LINK","title":"杰威","img":"i.jpg",
             "link":"kanqiu://bbs/topic/643310802"},
            {"nid":"2","tid":"","type":"IMG_TEXT","title":"数据帖","img":"i.jpg",
             "link":"https://m.hupu.com/bbs/643315009"}]}}"""
        val p = com.kudo.android.hupu.HupuBoardParser.parseBoardNews(json)!!
        assertEquals(3, p.items.size)
        assertTrue(p.items[0].tid.isEmpty())            // SUBJECT 专题 id 不再漏进 tid
        assertTrue(!p.items[0].openableAsThread)
        assertEquals("643310802", p.items[1].tid)       // 真帖 tid 直读
        assertEquals("643315009", p.items[2].tid)       // m.hupu.com/bbs/{tid} 兜底提取
    }
}
