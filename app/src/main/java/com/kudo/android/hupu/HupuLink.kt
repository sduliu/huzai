package com.kudo.android.hupu

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 虎扑正文/内容链接 → 本端可路由目的地（帖/版块/用户/话题词）。
 * 官方形态：正文话题词与站内链接在 App 内打开；归不进下述目的地的返回 null，
 * 交给既有 openLink 分流（应用内 WebView/外部浏览器）。
 */
sealed interface HupuDestination {
    /** 归一化后的绝对 URL，交给 openLink/UriHandler 的值 */
    val url: String

    /** {tid}.html 帖链接（可带版块前缀 /all-nba/643x.html、翻页 643x-2.html）→ topic/{id} */
    data class Thread(override val url: String, val tid: String) : HupuDestination

    /** /all-nba、/lakers、/502、/ent 等单段版块路径 → category/{boardId} */
    data class Board(override val url: String, val boardUrl: String) : HupuDestination

    /** my.hupu.com/{euid}、m.hupu.com/user/{euid} → user/{euid} */
    data class User(override val url: String, val euid: String) : HupuDestination

    /** m.hupu.com/tag/{id}、huputiyu://bbs/subject/{id}、?tagId={id} → tag 聚合页 */
    data class Tag(override val url: String, val tagId: String) : HupuDestination
}

object HupuLink {
    private val HOSTS = setOf("bbs.hupu.com", "m.hupu.com")
    private val TID = Regex("^\\d{6,}$")
    private val SUBJECT = Regex("^huputiyu://bbs/subject/([\\w-]+)")
    private val BASE = "https://bbs.hupu.com/".toHttpUrlOrNull()

    /** 解析 href（可相对）为虎扑站内目的地；非虎扑域、非 http(s)、不可判定一律 null。 */
    fun destination(raw: String): HupuDestination? {
        val href = raw.trim()
        if (href.isEmpty() || href.startsWith("#")) return null
        SUBJECT.find(href)?.let { return HupuDestination.Tag(href, it.groupValues[1]) }
        if (!href.startsWith("http", true) && !href.startsWith("/")) return null
        val url = BASE?.resolve(href) ?: return null
        val abs = url.toString()
        val host = url.host
        val segs = url.pathSegments.dropLastWhile(String::isEmpty)
        if (host == "my.hupu.com") {
            return segs.firstOrNull()?.takeIf(String::isNotBlank)?.let { HupuDestination.User(abs, it) }
        }
        if (host !in HOSTS) return null
        url.queryParameter("tagId")?.takeIf(String::isNotBlank)?.let { return HupuDestination.Tag(abs, it) }
        when (segs.firstOrNull()) {
            "user" -> return segs.getOrNull(1)?.takeIf(String::isNotBlank)?.let { HupuDestination.User(abs, it) }
            "tag" -> return segs.getOrNull(1)?.takeIf(String::isNotBlank)?.let { HupuDestination.Tag(abs, it) }
            "bbs" -> segs.getOrNull(1)?.takeIf(String::isNotBlank)?.let { seg ->
                // m.hupu.com/bbs/{tid}(.html) 帖页；/bbs/{board} 版块页
                val tid = seg.removeSuffix(".html").substringBefore("-")
                if ((seg.endsWith(".html") || seg.all(Char::isDigit)) && TID.matches(tid)) {
                    return HupuDestination.Thread(abs, tid)
                }
                return HupuDestination.Board(abs, "/$seg")
            }
        }
        val last = segs.lastOrNull() ?: return null
        if (last.endsWith(".html")) {
            val tid = last.removeSuffix(".html").substringBefore("-")
            if (TID.matches(tid)) return HupuDestination.Thread(abs, tid)
        }
        // 单段版块路径；多段非 html 路径不可判定，返回 null 走既有分流
        if (segs.size == 1 && !last.contains('.')) return HupuDestination.Board(abs, "/$last")
        return null
    }
}
