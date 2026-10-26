package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批L-1：专区版块搜索解析（search2 type=topic）映射验证 */
class ZoneSearchParserTest {

    private val sample = """
        {"data":{"result":{"count":2,"totalPage":1,"type":"topic","hasNextPage":0,
        "data":[
          {"id":127,"title":"<font color='var(--primary_button)'>湖人</font>专区",
           "content":"欢迎来到湖人专区讨论与球队有关的话题","img":"https://i3.hoopchina.com.cn/a.png","itemid":"topic_127"},
          {"id":502,"title":"篮球资讯","content":"仅限官方账号发布资讯主帖","img":"https://i3.hoopchina.com.cn/b.png","itemid":"topic_502"}
        ],"search_title":"专区"}},"status":"200"}
    """.trimIndent()

    @Test
    fun `topic search maps fields and strips highlight tags`() {
        val list = HupuParser.parseTopicSearch(sample)
        assertEquals(2, list.size)
        val lakers = list[0]
        assertEquals("127", lakers.topicId)
        assertEquals("湖人专区", lakers.name)
        assertEquals("/127", lakers.url)
        assertEquals("欢迎来到湖人专区讨论与球队有关的话题", lakers.desc)
        assertEquals("https://i3.hoopchina.com.cn/a.png", lakers.logo)
        assertEquals("篮球资讯", list[1].name)
    }

    @Test
    fun `empty result array yields empty list`() {
        val json = """{"data":{"result":{"count":0,"data":[]}},"status":"200"}"""
        assertTrue(HupuParser.parseTopicSearch(json).isEmpty())
    }

    @Test
    fun `missing id entries are skipped and malformed json is safe`() {
        val json = """{"data":{"result":{"data":[{"title":"无id"},{"id":"7","title":"有效","content":"","img":""}]}}}"""
        val list = HupuParser.parseTopicSearch(json)
        assertEquals(1, list.size)
        assertEquals("7", list[0].topicId)
        assertTrue(HupuParser.parseTopicSearch("not-json").isEmpty())
    }

    // ---------- 批Z-1：搜索结果分类 ----------

    /** 批Z-1：search2 type=users——字段照抄 2026-10-27 匿名实测（lights=获亮、recNum=推荐）。 */
    @Test
    fun `user search maps puid name avatar and stats`() {
        val json = """{"data":{"result":{"count":2000,"totalPage":100,"type":"users","hasNextPage":1,"data":[
            {"id":50082186,"lights":1240,"recNum":6200,
             "header":"https://i3.hoopchina.com.cn/user/559/a.png","itemid":"user_50082186",
             "display_type":"users","username":"小一画<font color='#c01e2f'>库里</font>"},
            {"id":31865185,"lights":195299,"recNum":13632,"header":"","username":"库里二世"}
        ]}}}"""
        val (users, hasNext) = HupuParser.parseUserSearch(json)
        assertTrue(hasNext)
        assertEquals(2, users.size)
        assertEquals("50082186", users[0].puid)
        assertEquals("小一画库里", users[0].username)   // 高亮 font 标签已剥
        assertEquals("https://i3.hoopchina.com.cn/user/559/a.png", users[0].header)
        assertEquals(1240L, users[0].lights)
        assertEquals(6200L, users[0].recNum)
    }

    @Test
    fun `user search degrades on empty and malformed payloads`() {
        val (u1, n1) = HupuParser.parseUserSearch("""{"data":{"result":{"count":0,"hasNextPage":0,"data":[]}}}""")
        assertTrue(u1.isEmpty()); assertFalse(n1)
        val (u2, n2) = HupuParser.parseUserSearch("not-json")
        assertTrue(u2.isEmpty()); assertFalse(n2)
    }

    /** 批Z-1：search2 type=topic 分页变体透传 hasNextPage（条目解析沿用原路径）。 */
    @Test
    fun `topic search page carries hasNextPage`() {
        val (list, hasNext) = HupuParser.parseTopicSearchPage(sample)
        assertEquals(2, list.size)
        assertFalse(hasNext)  // fixture hasNextPage=0
        val paged = """{"data":{"result":{"type":"topic","hasNextPage":1,"data":[
            {"id":9,"title":"x","content":"","img":""}]}}}"""
        val (l2, n2) = HupuParser.parseTopicSearchPage(paged)
        assertEquals(1, l2.size); assertTrue(n2)
        val (l3, n3) = HupuParser.parseTopicSearchPage("not-json")
        assertTrue(l3.isEmpty()); assertFalse(n3)
    }
}
