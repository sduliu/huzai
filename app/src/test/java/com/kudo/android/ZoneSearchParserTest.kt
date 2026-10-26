package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
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
}
