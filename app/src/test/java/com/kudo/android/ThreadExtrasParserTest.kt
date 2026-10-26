package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 批N-1：信息流卡补齐字段解析——`threads?tid=` 的作者/日期、`bbs-thread` 的转发数。
 * 样本为 2026-10-26 实测响应精简版。
 */
class ThreadExtrasParserTest {

    private val threadJson = """
        {"data":{"thread":{"author":{"puid":"114534948","euid":"264887970925976",
        "puname":"不减30斤不改名","header":"https://i3.hoopchina.com.cn/user/default/tiger3.png"},
        "createdAt":1792977459000,"createdAtFormat":"2小时前","lights":5,"replies":15}},"code":200,"msg":"ok"}
    """.trimIndent()

    private val bbsThreadJson = """
        {"data":{"t_detail":{"tid":643298428,"title":"x","hits":"6922","replies":"15",
        "lights":"5","share":"1","is_top":"0"},"t_author":{"puid":"114534948","username":"不减30斤不改名"}}}
    """.trimIndent()

    @Test
    fun `share number parsed from t_detail`() {
        assertEquals(1, HupuParser.parseThreadShareNum(bbsThreadJson))
    }

    @Test
    fun `author and createdAt parsed from threads endpoint`() {
        val a = HupuParser.parseThreadAuthor(threadJson)
        assertEquals("不减30斤不改名", a?.name)
        assertEquals("114534948", a?.puid)
        assertEquals("2小时前", HupuParser.parseThreadCreatedAt(threadJson))
    }

    @Test
    fun `missing or malformed payloads degrade safely`() {
        assertNull(HupuParser.parseThreadShareNum("""{"data":{"t_detail":{}}}"""))
        assertNull(HupuParser.parseThreadShareNum("not-json"))
        assertEquals("", HupuParser.parseThreadCreatedAt("""{"code":400,"data":{}}"""))
        assertEquals("", HupuParser.parseThreadCreatedAt("not-json"))
    }

    /** 批O-1：createdAt(ms epoch) 解析——随作者缓存落盘供日期命中 */
    @Test
    fun `createdAt ms parsed for cacheable date`() {
        assertEquals(1792977459000L, HupuParser.parseThreadCreatedAtMs(threadJson))
        assertEquals(0L, HupuParser.parseThreadCreatedAtMs("""{"code":400,"data":{}}"""))
        assertEquals(0L, HupuParser.parseThreadCreatedAtMs("""{"data":{"thread":{}},"code":200}"""))
        assertEquals(0L, HupuParser.parseThreadCreatedAtMs("not-json"))
    }
}
