package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批F-3：详情页楼层区补齐——detail.hot/latest（同区热帖/最新帖）与
 * 回复的 client（客户端来源）/hidden（删除·隐藏·审核态）字段解析。
 * Fixture 字段名取自真实 detail JSON（hot/latest 数组元素与 threadFrom 同 schema）。
 */
class ThreadDetailRelatedTest {

    private fun detailHtml(detailJson: String): String =
        "<html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
            "{\"props\":{\"pageProps\":{\"detail\":$detailJson}}}</script></body></html>"

    private val baseDetail = """
    {
      "thread": {
        "tid": "642999001",
        "title": "主帖标题",
        "fid": "34",
        "replies": 3,
        "content": "<p>主楼正文</p>"
      },
      "replies": {
        "count": 3,
        "current": 1,
        "total": 1,
        "list": [
          {
            "pid": "1001",
            "content": "<p>正常回复</p>",
            "allLightCount": 12,
            "replyNum": 2,
            "createdAtFormat": "10-26 10:00",
            "isStarter": false,
            "location": "上海",
            "client": "ANDROID",
            "isDelete": false,
            "isHidden": false,
            "hidePost": 0,
            "isAudit": false,
            "author": {"euid": "11", "nickname": "甲"}
          },
          {
            "pid": "1002",
            "content": "<p>该内容已不可见</p>",
            "allLightCount": 0,
            "replyNum": 0,
            "createdAtFormat": "10-26 10:05",
            "isDelete": true,
            "client": "IPHONE",
            "author": {"euid": "22", "nickname": "乙"}
          },
          {
            "pid": "1003",
            "content": "<p>另一条正常回复</p>",
            "allLightCount": 1,
            "replyNum": 0,
            "createdAtFormat": "10-26 10:07",
            "client": "",
            "author": {"euid": "33", "nickname": "丙"}
          }
        ]
      },
      "hot": [
        {
          "tid": "642111111",
          "title": "同区热帖A",
          "cover": "https://img/a.png",
          "lights": 88,
          "replies": 230,
          "read": 50000,
          "url": "/642111111.html",
          "author": {"euid": "44", "nickname": "丁"},
          "topic": {"topicId": "34", "name": "湿乎乎", "url": "/xixi"}
        },
        {
          "tid": "642222222",
          "title": "同区热帖B",
          "recommend": 40,
          "replies": 9
        }
      ],
      "latest": [
        {
          "tid": "642333333",
          "title": "最新发帖C",
          "replies": 0,
          "createdAtFormat": "10-26 11:00"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `hot and latest lists parse into related threads`() {
        val d = HupuParser.parseThreadDetail(detailHtml(baseDetail))
        assertNotNull(d)
        d!!
        assertEquals(2, d.hot.size)
        assertEquals("642111111", d.hot[0].tid)
        assertEquals("同区热帖A", d.hot[0].title)
        assertEquals(88, d.hot[0].lights)
        assertEquals(230, d.hot[0].replies)
        assertEquals("湿乎乎", d.hot[0].topic?.name)
        assertEquals(40, d.hot[1].lights) // recommend 兜底
        assertEquals(1, d.latest.size)
        assertEquals("642333333", d.latest[0].tid)
        assertEquals("最新发帖C", d.latest[0].title)
    }

    @Test
    fun `missing hot and latest arrays degrade to empty`() {
        val json = """{"thread":{"tid":"1","title":"t"},"replies":{"count":0,"current":1,"total":1,"list":[]}}"""
        val d = HupuParser.parseThreadDetail(detailHtml(json))
        assertNotNull(d)
        assertTrue(d!!.hot.isEmpty())
        assertTrue(d.latest.isEmpty())
    }

    @Test
    fun `reply client and hidden flags parse`() {
        val d = HupuParser.parseThreadDetail(detailHtml(baseDetail))!!
        val normal = d.replies[0]
        assertEquals("ANDROID", normal.client)
        assertFalse(normal.hidden)
        val deleted = d.replies[1]
        assertTrue(deleted.hidden) // isDelete
        assertEquals("IPHONE", deleted.client)
        val bare = d.replies[2]
        assertEquals("", bare.client)
        assertFalse(bare.hidden)
    }

    @Test
    fun `hidden via hidePost and isAudit flags`() {
        fun replyJson(extra: String) = detailHtml(
            """{"thread":{"tid":"1","title":"t"},
               "replies":{"count":1,"current":1,"total":1,
                 "list":[{"pid":"9","content":"x",$extra"author":{"euid":"1","nickname":"n"}}]}}""",
        )
        assertTrue(HupuParser.parseThreadDetail(replyJson("\"hidePost\":2,"))!!.replies[0].hidden)
        assertTrue(HupuParser.parseThreadDetail(replyJson("\"isAudit\":true,"))!!.replies[0].hidden)
        assertTrue(HupuParser.parseThreadDetail(replyJson("\"isHidden\":true,"))!!.replies[0].hidden)
        assertFalse(HupuParser.parseThreadDetail(replyJson(""))!!.replies[0].hidden)
    }
}
