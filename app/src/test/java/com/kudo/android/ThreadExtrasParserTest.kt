package com.kudo.android

import com.kudo.android.data.toPost
import com.kudo.android.hupu.HupuAuthor
import com.kudo.android.hupu.HupuParser
import com.kudo.android.hupu.HupuReply
import com.kudo.android.hupu.pageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** 批W-2：作者主页路由 id——euid 优先；官方账号帖 SSR 只下发 puid 时回落 puid
     *  （m.hupu.com/user/{puid} 实测同样返回完整 SSR）；"0" 为占位脏值不路由。 */
    @Test
    fun `author pageId prefers euid falls back puid`() {
        assertEquals("e1", HupuAuthor(puid = "p1", euid = "e1").pageId)
        assertEquals("p1", HupuAuthor(puid = "p1", euid = "").pageId)
        assertEquals("", HupuAuthor(puid = "0", euid = "").pageId)
        assertEquals("", HupuAuthor().pageId)
    }

    /**
     * 批X2-1：Member.username 是 user/{username} 路由参——必须承载 pageId 而非昵称，
     * 否则移动 SSR 作者（只下发 puid）会拿昵称进 UserScreen → NOT_FOUND（真机实证）。
     * 真实样本：tid=643321948「追梦谈0胜3负」basicInfo.author={puid:56098302,name:虎扑篮球资讯}。
     */
    @Test
    fun `member username carries pageId not nickname`() {
        val mobileAuthor = HupuReply(pid = "1", floor = 2,
            author = HupuAuthor(puid = "56098302", name = "虎扑篮球资讯")).toPost("t").author
        assertEquals("56098302", mobileAuthor.username)
        assertNotEquals(0, mobileAuthor.id)

        val euidAuthor = HupuReply(pid = "2", floor = 3,
            author = HupuAuthor(puid = "p1", euid = "e1", name = "n")).toPost("t").author
        assertEquals("e1", euidAuthor.username)

        // 双缺仅昵称：不造 id、路由参回落昵称（不接错页的边界由 UI 侧 id!=0 门控保证）
        val nameOnly = HupuReply(pid = "3", floor = 4,
            author = HupuAuthor(name = "x")).toPost("t").author
        assertEquals("x", nameOnly.username)
        assertEquals(0, nameOnly.id)

        assertEquals("", HupuReply(pid = "4", floor = 5).toPost("t").author.username)
    }

    /** 批X2-1：同 pageId 的两个 Member 拿到同一 user: 命名空间 id（楼主徽标不误标）。 */
    @Test
    fun `member ids are namespaced per pageId`() {
        val op = HupuReply(pid = "1", floor = 1,
            author = HupuAuthor(puid = "56098302", name = "虎扑篮球资讯")).toPost("t").author
        val other = HupuReply(pid = "2", floor = 2,
            author = HupuAuthor(puid = "20292842", name = "罩渐大")).toPost("t").author
        val dupe = HupuReply(pid = "3", floor = 3,
            author = HupuAuthor(puid = "56098302", name = "虎扑篮球资讯")).toPost("t").author
        assertNotEquals(op.id, other.id)
        assertEquals(op.id, dupe.id)
    }

    /**
     * 批X2-1：puid 路由 `m.hupu.com/user/56098302` 真实 SSR（2026-10-26 抓包）——
     * userInfoData/threadList/replyList 齐全，解析产出完整资料卡。
     */
    @Test
    fun `puid route user SSR parses full profile`() {
        val html = javaClass.classLoader!!.getResource("user_puid_56098302.html")!!.readText()
        val p = HupuParser.parseUserProfile(html)
        assertNotNull(p)
        p!!
        assertEquals("虎扑篮球资讯", p.name)
        assertEquals("56098302", p.puid)
        // euid 缺省回落 puid（真实页未下发 euid 字段）
        assertEquals("56098302", p.euid)
        assertTrue(p.threads.isNotEmpty())
        assertTrue(p.replies.isNotEmpty())
    }
}
