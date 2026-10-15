package com.java.myapplication.data

import org.json.JSONObject

/**
 * board：板块资讯 JSON / SSR 解析。
 *
 *  · [parseBoardNews] — games.mobileapi `basketballapi/news/v2/newsList` 响应：
 *    `result.data[]` + `result.topNewsCount`。
 *  · [parseSoccerNews] — m.hupu.com/soccer SSR `__NEXT_DATA__.props.pageProps.news[]`。
 *
 * 两来源条目结构一致（nid/tid/type/title/img/link/replies/lights/publishTime/badge/top），
 * 统一走 [newsFrom]；tid 缺失时尝试从 link 提取（kanqiu://bbs/topic/{tid}、m.hupu.com/bbs/{tid}）。
 */
object HupuBoardParser {

    private val TID_IN_LINK = Regex("(?:topic|bbs)/(\\d+)")

    /**
     * 解析 newsList 响应。网络失败/非成功返回 null；成功但空 data 返回空页（区分空态与失败）。
     *
     * @param basePreCount 本请求的 preCount——翻页游标需累计（下页 = basePreCount + 本页条数）
     */
    fun parseBoardNews(json: String, basePreCount: Int = 0): HupuBoardNewsPage? {
        return try {
            val root = JSONObject(json)
            // 该接口错误时 success=false 且 result 可能是对象或空
            if (!root.optBoolean("success", false)) return null
            val result = root.optJSONObject("result") ?: return null
            val arr = result.optJSONArray("data") ?: return HupuBoardNewsPage(
                items = emptyList(),
                topNewsCount = result.optInt("topNewsCount", 0),
                nextPreCount = basePreCount,
            )
            val items = mutableListOf<HupuBoardNews>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o -> newsFrom(o)?.let { items += it } }
            }
            HupuBoardNewsPage(
                items = items,
                topNewsCount = result.optInt("topNewsCount", 0),
                nextPreCount = basePreCount + items.size,
                anchorNid = items.firstOrNull()?.nid ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析国际足球 SSR 页的 news 列表。页面不含 SSR 数据 / 无 news 返回空列表。
     */
    fun parseSoccerNews(html: String): List<HupuBoardNews> {
        return try {
            val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            val i = html.indexOf(marker)
            if (i < 0) return emptyList()
            val start = i + marker.length
            val end = html.indexOf("</script>", start)
            if (end < 0) return emptyList()
            val root = JSONObject(html.substring(start, end))
            val arr = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONArray("news") ?: return emptyList()
            val out = mutableListOf<HupuBoardNews>()
            for (j in 0 until arr.length()) {
                arr.optJSONObject(j)?.let { o -> newsFrom(o)?.let { out += it } }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun newsFrom(o: JSONObject): HupuBoardNews? {
        val nid = o.optString("nid")
        if (nid.isEmpty()) return null
        var tid = o.optString("tid")
        val link = o.optString("link")
        if (tid.isEmpty()) {
            tid = TID_IN_LINK.find(link)?.groupValues?.get(1) ?: ""
        }
        val badgeArr = o.optJSONArray("badge")
        val badges = mutableListOf<String>()
        var top = o.optBoolean("top", false)
        if (badgeArr != null) {
            for (k in 0 until badgeArr.length()) {
                val name = badgeArr.optJSONObject(k)?.optString("name") ?: continue
                if (name.isNotEmpty()) badges += name
                if (name == "置顶") top = true
            }
        }
        return HupuBoardNews(
            nid = nid,
            tid = tid,
            type = o.optString("type"),
            title = o.optString("title"),
            img = o.optString("img"),
            link = link,
            linkType = o.optString("linkType"),
            replies = o.optInt("replies", 0),
            lights = o.optInt("lights", 0),
            publishTime = o.optString("publishTime"),
            addTime = o.optLong("addTime", 0L),
            top = top,
            badges = badges,
        )
    }
}
