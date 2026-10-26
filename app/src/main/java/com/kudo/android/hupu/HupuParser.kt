package com.kudo.android.hupu

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 从虎扑 SSR HTML 中提取内嵌 JSON 并映射为数据模型。
 *
 * 两条通道（均实测验证，见 docs/hupu_web_probe.md）：
 * 1. window.$$data = {...};</script>   — 版块/话题列表页
 * 2. <script id="__NEXT_DATA__">...</script> — 帖子详情页
 *
 * 提取策略：平衡括号扫描（正确处理字符串内的引号/转义/嵌套），
 * 比非贪婪正则更稳健。
 */
object HupuParser {

    // ---------- JSON 提取 ----------

    /** 从 html 中定位 startKey 后，用平衡括号法取出完整 JSON 文本 */
    private fun extractBalancedJson(html: String, startKey: String): String? {
        val keyIdx = html.indexOf(startKey)
        if (keyIdx < 0) return null
        val braceStart = html.indexOf('{', keyIdx)
        if (braceStart < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until html.length) {
            val c = html[i]
            if (escaped) { escaped = false; continue }
            when {
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(braceStart, i + 1)
                }
            }
        }
        return null
    }

    private fun String?.toObj(): JSONObject? = this?.let {
        try { JSONObject(it) } catch (e: Exception) { null }
    }

    fun extractDollarData(html: String): JSONObject? =
        extractBalancedJson(html, "window.\$\$data=").toObj()

    fun extractNextData(html: String): JSONObject? {
        val key = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
        val start = html.indexOf(key)
        if (start < 0) return null
        val contentStart = start + key.length
        val end = html.indexOf("</script>", contentStart)
        if (end < 0) return null
        return try { JSONObject(html.substring(contentStart, end)) } catch (e: Exception) { null }
    }

    // ---------- 列表页：版块热帖 /all-* ----------

    fun parseBoardPage(html: String): HupuBoardPage? {
        val root = extractDollarData(html) ?: return null
        val pd = root.optJSONObject("pageData") ?: return null
        val threadsJson = pd.optJSONArray("threads") ?: return null

        val catJson = pd.optJSONObject("category")
        val category = catJson?.let {
            HupuTopicInfo(
                topicId = it.optString("topicId"),
                name = it.optString("name"),
                url = it.optString("url"),
                logo = it.optString("logo").ifEmpty { null },
                desc = it.optString("desc"),
            )
        }

        val hot = mutableListOf<HupuTopicInfo>()
        pd.optJSONArray("hot")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                hot += HupuTopicInfo(
                    topicId = o.optString("topicId"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    logo = o.optString("logo").ifEmpty { null },
                    hotText = o.optString("countText"),
                )
            }
        }

        val rec = mutableListOf<HupuThread>()
        pd.optJSONArray("recommend")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { rec += threadFrom(it) }
            }
        }

        val trending = mutableListOf<String>()
        pd.optJSONArray("trending")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("title")?.takeIf { it.isNotBlank() }?.let(trending::add)
            }
        }

        return HupuBoardPage(
            category = category,
            threads = threadsJson.mapNotNull { o -> o?.let { threadFrom(it) } },
            hotTopics = hot,
            recommendations = rec,
            trending = trending,
        )
    }

    /**
     * 官方移动站首页「综合」流（m.hupu.com SSR __NEXT_DATA__.props.pageProps.res[]）。
     * 每项：tid/title/url/label(版块名)/lights/replies/type(1_pic|3_pic)/source[](缩略图)。
     * 映射为 HupuThread：label → topic.name（列表行显示版块名），source[0] → cover。
     */
    fun parseMobileHome(html: String): List<HupuThread> {
        val root = extractNextData(html) ?: return emptyList()
        val arr = root.optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONArray("res") ?: return emptyList()
        val out = mutableListOf<HupuThread>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tid = o.optString("tid").takeUnless { it == "null" } ?: ""
            val title = o.optString("title")
            if (tid.isEmpty() || title.isEmpty()) continue
            val label = o.optString("label").takeUnless { it == "null" } ?: ""
            val covers = buildList {
                val src = o.optJSONArray("source") ?: return@buildList
                for (j in 0 until src.length()) {
                    src.optString(j).takeUnless { it.isEmpty() || it == "null" }?.let { add(it) }
                }
            }
            out += HupuThread(
                tid = tid,
                title = title,
                cover = covers.firstOrNull(),
                covers = covers,
                lights = o.optString("lights").toIntOrNull() ?: 0,
                replies = o.optString("replies").toIntOrNull() ?: 0,
                url = o.optString("url").takeUnless { it == "null" } ?: "",
                topic = label.takeIf { it.isNotEmpty() }?.let { HupuTopic(name = it) },
            )
        }
        return out
    }

    /** 全站版块/话题导航树（来自任一 /all-* 页的 pageData.categories） */
    fun parseCategories(html: String): List<HupuCategory> {
        val root = extractDollarData(html) ?: return emptyList()
        val arr = root.optJSONObject("pageData")?.optJSONArray("categories") ?: return emptyList()
        val result = mutableListOf<HupuCategory>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val topics = mutableListOf<HupuTopicInfo>()
            c.optJSONArray("topics")?.let { ts ->
                for (j in 0 until ts.length()) {
                    val t = ts.optJSONObject(j) ?: continue
                    topics += HupuTopicInfo(
                        topicId = t.optString("topicId"),
                        name = t.optString("name"),
                        url = t.optString("url"),
                        logo = t.optString("logo").ifEmpty { null },
                        hotText = t.optString("countText"),
                    )
                }
            }
            result += HupuCategory(
                cateId = c.optString("cateId"),
                name = c.optString("name"),
                logo = c.optString("logo").ifEmpty { null },
                topics = topics,
            )
        }
        return result
    }

    // ---------- 列表页：具体话题 /topic-{n} ----------

    fun parseTopicPage(html: String): HupuTopicPage? {
        val root = extractDollarData(html) ?: return null
        val t = root.optJSONObject("topic") ?: return null
        val topicJson = t.optJSONObject("topic") ?: return null
        val tl = t.optJSONObject("threads") ?: return null
        val listJson = tl.optJSONArray("list") ?: return null

        val tabs = mutableListOf<SortTab>()
        t.optJSONArray("tabs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                tabs += SortTab(id = o.optInt("id"), title = o.optString("title"), url = o.optString("url"))
            }
        }

        // 批L-2：$$data.admins —— 专区信息（版主团队 + 招募标记），与 topic 平级的顶层节点
        val admins = mutableListOf<HupuBoardAdmin>()
        val adm = root.optJSONObject("admins")
        adm?.optJSONArray("adminList")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                admins += HupuBoardAdmin(
                    roleName = o.optString("roleName"),
                    userName = o.optString("userName"),
                    header = o.optString("header"),
                    url = o.optString("url"),
                )
            }
        }
        val boardInfo = HupuBoardInfo(
            title = adm?.optString("title") ?: "",
            adminApplicable = adm?.optInt("adminApplicable", 0) ?: 0,
            admins = admins,
        )

        return HupuTopicPage(
            topic = HupuTopicInfo(
                topicId = topicJson.optString("topicId"),
                name = topicJson.optString("name"),
                url = topicJson.optString("url"),
                desc = topicJson.optString("desc"),
                hotText = topicJson.optString("countText"),
                logo = topicJson.optString("logo").ifEmpty { null },
            ),
            threads = listJson.mapNotNull { o -> o?.let { threadFrom(it) } },
            page = t.optInt("page", 1),
            totalPages = tl.optInt("total", 1),
            sortTabs = tabs,
            boardInfo = boardInfo,
        )
    }

    /** 批L-2：m站 zone 页 _next/data JSON → JRs 成员数等（pageProps.zoneData） */
    fun parseZoneInfo(json: String): HupuZoneInfo? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        val z = root.optJSONObject("pageProps")?.optJSONObject("zoneData") ?: return null
        return HupuZoneInfo(
            memberText = z.optString("followedUserNum"),
            allThreadNum = z.optString("allThreadNum"),
            bgColor = z.optString("bgColor"),
            desc = z.optString("desc"),
        )
    }

    /**
     * 批L-1：专区版块搜索（m.hupu.com/api/v2/search2?type=topic）→ 版块条目。
     * title 带 <font> 高亮标签需剥除；结果无 url 字段，用 /{topicId} 数字路径
     * （服务端解析到正式版块页，实测 /127 → 湖人专区）。
     */
    fun parseTopicSearch(json: String): List<HupuTopicInfo> {
        val root = try { JSONObject(json) } catch (e: Exception) { return emptyList() }
        val arr = root.optJSONObject("data")?.optJSONObject("result")
            ?.optJSONArray("data") ?: return emptyList()
        val htmlTag = Regex("<[^>]+>")
        val out = mutableListOf<HupuTopicInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            out += HupuTopicInfo(
                topicId = id,
                name = o.optString("title").replace(htmlTag, ""),
                url = "/$id",
                desc = o.optString("content"),
                logo = o.optString("img").ifEmpty { null },
            )
        }
        return out
    }

    // ---------- 详情页 /{tid}.html ----------

    fun parseThreadDetail(html: String): HupuThreadDetail? {
        val next = extractNextData(html) ?: return null
        val pageProps = next.optJSONObject("props")
            ?.optJSONObject("pageProps") ?: return null
        val detail = pageProps.optJSONObject("detail") ?: return null

        // 批X-1：真实错误页 detailErrorInfo 在 pageProps 级（与 detail 平级，
        // 实测 4044 页 detail={"lights":[]}）——两级都读，code 缺省按成功处理，
        // 显式非 200 才判失败。
        val errObj = pageProps.optJSONObject("detailErrorInfo")
            ?: detail.optJSONObject("detailErrorInfo")
        if (errObj != null && errObj.optInt("code", 200) != 200) return null

        val th = detail.optJSONObject("thread") ?: return null
        // 0 回复帖子的 replies/list 可能缺失：回退空列表（帖子本体仍可展示）
        val rep = detail.optJSONObject("replies")
        val repList = rep?.optJSONArray("list")
        val page = rep?.optInt("current", 1) ?: 1

        val replies = mutableListOf<HupuReply>()
        for (i in 0 until (repList?.length() ?: 0)) {
            val o = repList?.optJSONObject(i) ?: continue
            replies += HupuReply(
                pid = o.optString("pid"),
                contentHtml = o.optString("content"),
                floor = (page - 1) * 20 + i + 1,
                lights = o.optInt("allLightCount", 0),
                // 子回复数：replyNum（实测与 bbs-reply-detail API 的实际子回复数一致）。
                // 注意：count 不是回复数而是点亮数（= allLightCount，实测 642258234 逐楼比对），
                // 若误用会出现「回复数=点赞数」且点开楼中楼为空
                totalReplies = o.optInt("replyNum", 0).coerceAtLeast(0),
                createdAtText = o.optString("createdAtFormat"),
                isStarter = o.optBoolean("isStarter", false),
                location = o.optString("location"),
                author = authorFrom(o.optJSONObject("author")),
                // 1.128: quote.pid 非空 = 该条实为「回复某条评论」的子回复（真一级评论无 quote 键）
                quotePid = o.optJSONObject("quote")
                    ?.optString("pid")
                    ?.takeIf { it.isNotEmpty() && it != "" }
                    ?: "",
                client = o.optString("client"),
                hidden = o.optBoolean("isDelete") || o.optBoolean("isHidden") ||
                    o.optInt("hidePost", 0) > 0 || o.optBoolean("isAudit"),
            )
        }

        val thread = threadFrom(th)
        fun relatedThreads(key: String): List<HupuThread> {
            val arr = detail.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { threadFrom(it) }?.takeIf { it.tid.isNotEmpty() }
            }
        }
        return HupuThreadDetail(
            thread = thread,
            contentHtml = th.optString("content"),
            replies = replies,
            replyCount = rep?.optInt("count", thread.replies) ?: thread.replies,
            replyPage = page,
            replyTotalPages = rep?.optInt("total", 1) ?: 1,
            isLocked = th.optBoolean("isLock", false),
            hot = relatedThreads("hot"),
            latest = relatedThreads("latest"),
            // 1.119: 投票从帖子 format 的 jsonV3 vote 节点解析（单选 radio / 多选 checkbox）
            vote = voteFromFormat(th.optString("format")),
        )
    }

    /**
     * 批Xfix-1：m.hupu.com/bbs/{tid}.html 移动 SSR 详情兜底解析。
     *
     * 触发场景：bbs.hupu.com 对移动 UA 发 307 → m.hupu.com/bbs/{tid}.html
     * （实测同 tid 桌面 200/移动 307 双形态并存）。移动端或边缘节点拿到移动页时
     * SSR 结构完全不同——pageProps.{threadData,threadInfo,initialRepliesData}，
     * 无 detail 节点 → 桌面解析返回 null → NOT_FOUND。本函数吃移动页结构。
     */
    fun parseMobileThreadDetail(html: String): HupuThreadDetail? {
        val next = extractNextData(html) ?: return null
        val pp = next.optJSONObject("props")?.optJSONObject("pageProps") ?: return null
        val td = pp.optJSONObject("threadData") ?: return null
        // 移动页自带错误码（帖被删/无权时非 200）→ 如实失败
        if (td.optInt("code", 200) != 200) return null
        val data = td.optJSONObject("data")
        val basic = data?.optJSONObject("basicInfo")
        val tInfo = pp.optJSONObject("threadInfo")
        val tDetail = tInfo?.optJSONObject("t_detail") ?: return null

        val tid = tDetail.optString("tid").ifEmpty { basic?.optString("tid").orEmpty() }
        if (tid.isEmpty() || tid == "0") return null

        // 作者：basicInfo.author {puid,name}（新结构）或 threadInfo.t_author {puid,username}（旧字段）
        val authorObj = basic?.optJSONObject("author") ?: tInfo.optJSONObject("t_author")
        val author = authorObj?.let { a ->
            val name = a.optString("name").ifEmpty { a.optString("username") }
            if (name.isEmpty()) null else HupuAuthor(
                puid = a.optString("puid"),
                name = name,
                url = a.optString("header"),
                // 移动页 author 无 euid——pageId 回落 puid（m.hupu.com/user/{puid} 实测同达）
                euid = a.optString("euid"),
            )
        }

        val repArr = pp.optJSONObject("initialRepliesData")?.optJSONArray("initialReplies")
        val replies = mutableListOf<HupuReply>()
        for (i in 0 until (repArr?.length() ?: 0)) {
            val o = repArr!!.optJSONObject(i) ?: continue
            val u = o.optJSONObject("user")
            val rAuthor = u?.let {
                val name = it.optString("username").ifEmpty { it.optString("name") }
                if (name.isEmpty()) null else HupuAuthor(
                    puid = it.optString("puid"),
                    name = name,
                    url = it.optString("header"),
                    euid = it.optString("euid"),
                )
            }
            replies += HupuReply(
                pid = o.optString("pid"),
                contentHtml = o.optString("content"),
                floor = i + 1,
                lights = o.optString("light").toIntOrNull() ?: o.optInt("light", 0),
                totalReplies = o.optString("replies").toIntOrNull()
                    ?: o.optInt("replies", 0).coerceAtLeast(0),
                createdAtText = o.optString("createDt"),
                isStarter = o.optInt("is_lz", 0) > 0,
                location = o.optString("location"),
                author = rAuthor,
                quotePid = o.optJSONObject("quote_info")?.optString("pid").orEmpty(),
                client = o.optString("via"),
                hidden = o.optBoolean("isDelete") || o.optBoolean("isHidden") ||
                    o.optInt("hide", 0) > 0 || o.optBoolean("isAudit") ||
                    o.optInt("illegal", 0) > 0,
            )
        }

        val totalReplies = tDetail.optString("replies").toIntOrNull()
            ?: tDetail.optInt("replies", replies.size)
        val thread = HupuThread(
            tid = tid,
            title = tDetail.optString("title"),
            fid = tDetail.optJSONObject("f_info")?.optString("fid")
                ?: basic?.optString("fid").orEmpty(),
            lights = tDetail.optString("lights").toIntOrNull()
                ?: tDetail.optInt("lights", 0),
            replies = totalReplies,
            read = tDetail.optString("hits").toIntOrNull()
                ?: tDetail.optInt("hits", 0),
            createdAtText = tDetail.optString("createDt")
                .ifEmpty { tDetail.optString("update") },
            author = author,
            topic = basic?.let {
                val name = it.optString("topicName")
                if (name.isEmpty()) null
                else HupuTopic(topicId = it.optString("topic_id"), name = name)
            },
            shareNum = tDetail.optString("share").toIntOrNull() ?: -1,
        )
        return HupuThreadDetail(
            thread = thread,
            contentHtml = tDetail.optString("content"),
            replies = replies,
            replyCount = totalReplies,
            replyPage = 1,
            // 移动 SSR 首屏即全部回复（initialHasMore=false 实测）；翻页仍走桌面
            // /{tid}-{page}.html，拿不到时同链路移动兜底不复用（页码结构不同）。
            replyTotalPages = maxOf(1, (totalReplies + 19) / 20),
            isLocked = tDetail.optString("is_lock").let { it == "1" || it == "true" },
        )
    }

    /**
     * 1.119: 从帖子 format 里取投票节点。
     * format = {htmlV3, jsonV3:{type:doc,content:[{type:"vote",attrs:{voteId,limit,title,type,choices}}]}}
     * 无投票返回 null。
     */
    internal fun voteFromFormat(fmt: String?): HupuVote? {
        if (fmt.isNullOrBlank()) return null
        return try {
            val doc = JSONObject(fmt).optJSONObject("jsonV3") ?: return null
            val arr = doc.optJSONArray("content") ?: return null
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i) ?: continue
                if (n.optString("type") != "vote") continue
                val a = n.optJSONObject("attrs") ?: continue
                val vid = a.optInt("voteId", 0)
                if (vid <= 0) continue
                val choices = a.optJSONArray("choices")?.let { ja ->
                    (0 until ja.length()).map { ja.optString(it, "") }
                } ?: emptyList()
                return HupuVote(
                    voteId = vid,
                    title = a.optString("title", ""),
                    choices = choices,
                    limit = a.optInt("limit", 1),
                    type = a.optString("type", "radio").ifEmpty { "radio" },
                )
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ---------- 楼中楼（m.hupu.com/api/v2/bbs-reply-detail/{tid}-{pid}） ----------

    /**
     * 解析楼中楼响应。parentPostJson 为当前回复（父楼层）对象，
     * 用于补全 API 响应里缺失的楼层/时间/位置信息。
     */
    fun parseFloorReplies(json: String, parent: HupuReply): HupuFloorReplies? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        val data = root.optJSONObject("data") ?: return null

        val post = data.optJSONObject("post")
        val repArr = data.optJSONArray("replies") ?: return null

        val subs = mutableListOf<HupuSubReply>()
        for (i in 0 until repArr.length()) {
            val o = repArr.optJSONObject(i) ?: continue
            val user = o.optJSONObject("user")
            val quote = o.optJSONObject("quote")
            subs += HupuSubReply(
                pid = o.optString("pid"),
                contentHtml = o.optString("content"),
                lights = o.optInt("lights", o.optInt("allLightCount", 0)),
                nestedCount = o.optString("replies").toIntOrNull() ?: 0,
                createdAtText = user?.optString("createDt") ?: "",
                isStarter = o.optBoolean("lz", false),
                location = o.optString("location"),
                author = subAuthorFrom(user),
                quoteUser = quote?.optString("username") ?: "",
            )
        }

        // post 可能带更全的父楼层信息（亮数等），有则更新父楼层
        val parentUpdated = post?.let { p ->
            val lights = p.optInt("lights", p.optInt("allLightCount", parent.lights))
            parent.copy(lights = lights)
        } ?: parent

        return HupuFloorReplies(
            parent = parentUpdated,
            subReplies = subs,
            hasMore = data.optInt("nextPage", 0) > 0,
        )
    }

    // ---------- 搜索页 /search ----------

    /** 剥除搜索高亮标签（<font color='#c01e2f'>词</font> → 词）并压空白 */
    private fun stripHighlight(html: String): String =
        html.replace(Regex("""<[^>]+>"""), "").replace(Regex("""\s+"""), " ").trim()

    fun parseSearchPage(html: String): HupuSearchPage? {
        val root = extractDollarData(html) ?: return null
        val sr = root.optJSONObject("searchRes") ?: return null
        val arr = sr.optJSONArray("data") ?: return null

        val items = mutableListOf<HupuSearchItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            items += HupuSearchItem(
                tid = o.optString("id"),
                title = stripHighlight(o.optString("title")),
                desc = stripHighlight(o.optString("content")),
                picture = o.optString("picture").ifEmpty { null },
                replies = o.optString("replies").toIntOrNull() ?: 0,
                lights = o.optString("lights").toIntOrNull() ?: 0,
                addtime = o.optString("addtime").toLongOrNull() ?: 0L,
                addTimeDisplay = o.optString("addTimeDisplay"),
                fid = o.optString("fid"),
                forumName = o.optString("forum_name"),
                username = o.optString("username"),
            )
        }

        val pageEcho = root.optJSONObject("query")?.optString("page")?.toIntOrNull() ?: 1
        return HupuSearchPage(
            items = items,
            page = pageEcho,
            totalPages = sr.optInt("totalPage", 0),
            count = sr.optLong("count", 0L),
        )
    }

    /**
     * 批Zfix：search2 type=posts → HupuSearchPage（桌面搜索 HTML 通道的降级备用源）。
     * 匿名实测（2026-10-28）：count=500、totalPage=25、hasNextPage=1、20 条/页。
     * 与桌面通道差异：sortby/topicId 无等价参数（实测传入被忽略），仅默认相关度序；
     * addTimeDisplay 不下发，由 addtime（unix 秒）合成相对时间文案。
     */
    fun parsePostSearchJson(json: String, page: Int): HupuSearchPage? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        val rs = root.optJSONObject("data")?.optJSONObject("result") ?: return null
        val arr = rs.optJSONArray("data") ?: return null

        val items = mutableListOf<HupuSearchItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val addtime = o.optLong("addtime", 0L)
            items += HupuSearchItem(
                tid = o.optString("id"),
                title = stripHighlight(o.optString("title")),
                desc = stripHighlight(o.optString("content")),
                picture = o.optString("picture").ifEmpty { null },
                replies = o.optString("replies").toIntOrNull() ?: 0,
                lights = o.optString("lights").toIntOrNull() ?: 0,
                addtime = addtime,
                addTimeDisplay = o.optString("addTimeDisplay").ifEmpty { relTimeDesc(addtime) },
                fid = o.optString("fid"),
                forumName = o.optString("forum_name"),
                username = o.optString("username"),
            )
        }

        return HupuSearchPage(
            items = items,
            page = page,
            totalPages = rs.optInt("totalPage", 0),
            count = rs.optLong("count", 0L),
        )
    }

    /** 相对时间文案（对齐官方 addTimeDisplay：x分钟前/x小时前/x天前；超 30 天回落日期） */
    private fun relTimeDesc(ts: Long): String {
        if (ts <= 0) return ""
        val diff = System.currentTimeMillis() / 1000 - ts
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            diff < 86400 * 30 -> "${diff / 86400}天前"
            else -> try {
                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts * 1000))
            } catch (e: Exception) { "" }
        }
    }

    /**
     * 批Z-1：search2 type=users → (用户条目, hasNextPage)。匿名可读（count=2000 实测）。
     * 字段名照抄响应：id=短puid、username(带高亮font)、header=头像、lights=获亮、recNum=推荐。
     */
    fun parseUserSearch(json: String): Pair<List<HupuSearchUser>, Boolean> {
        val root = try { JSONObject(json) } catch (e: Exception) { return emptyList<HupuSearchUser>() to false }
        val rs = root.optJSONObject("data")?.optJSONObject("result") ?: return emptyList<HupuSearchUser>() to false
        val arr = rs.optJSONArray("data") ?: return emptyList<HupuSearchUser>() to false
        val out = mutableListOf<HupuSearchUser>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            out += HupuSearchUser(
                puid = id,
                username = stripHighlight(o.optString("username")),
                header = o.optString("header"),
                lights = o.optLong("lights"),
                recNum = o.optLong("recNum"),
            )
        }
        return out to (rs.optInt("hasNextPage", 0) == 1)
    }

    /**
     * 批Z-1：search2 type=topic 的分页变体——除条目外回传 hasNextPage。
     * [parseTopicSearch] 保持原签名（ZonePage 选择器只取首屏）。
     */
    fun parseTopicSearchPage(json: String): Pair<List<HupuTopicInfo>, Boolean> {
        val hasNext = try {
            JSONObject(json).optJSONObject("data")?.optJSONObject("result")
                ?.optInt("hasNextPage", 0) == 1
        } catch (e: Exception) { false }
        return parseTopicSearch(json) to hasNext
    }

    private fun subAuthorFrom(o: JSONObject?): HupuAuthor? {
        o ?: return null
        val name = o.optString("username").ifEmpty { o.optString("puname") }
        if (name.isEmpty()) return null
        val puid = o.optString("puid")
        return HupuAuthor(
            puid = puid,
            name = name,
            url = o.optString("header"),
            // bbs-reply-detail 的 user 不带 euid（实测 2025-11），但 puid 可直接作为
            // 用户主页跳转键（m.hupu.com/user/{puid} 与 pcmapi getUserInfo 均可达）
            euid = puid,
        )
    }

    // ---------- 映射工具 ----------

    // ---------- 批F-4：官方热榜 / tag 聚合页（m.hupu.com） ----------

    /** m.hupu.com/hot SSR → 热搜 tag 榜（pageProps.res；实测 30 条/页、totalElements≈50，匿名仅首屏） */
    fun parseHotTags(html: String): List<HupuHotTag> {
        val arr = extractNextData(html)
            ?.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONArray("res") ?: return emptyList()
        val out = mutableListOf<HupuHotTag>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("tagName")
            if (name.isBlank()) continue
            out += HupuHotTag(
                tagId = o.optString("tagId"),
                name = name,
                heat = o.optLong("heat"),
                icon = o.optString("icon"),
                rank = o.optInt("rank", i + 1),
                competitionType = o.optString("competitionType"),
                updateDesc = o.optString("tagUpdateDesc"),
            )
        }
        return out
    }

    /** m.hupu.com/tag/{tagId} SSR → topicTopInfo + topicThreadList 首屏 */
    fun parseTagPage(html: String): HupuTagPage? {
        val pp = extractNextData(html)
            ?.optJSONObject("props")
            ?.optJSONObject("pageProps") ?: return null
        val top = pp.optJSONObject("topicTopInfo") ?: return null
        val tl = pp.optJSONObject("topicThreadList")
        return HupuTagPage(
            info = HupuTagInfo(
                tagId = top.optString("tagId"),
                name = top.optString("name"),
                icon = top.optString("icon"),
                banner = top.optString("banner"),
                bannerRgb = top.optString("bannerRgb"),
                tNum = top.optLong("tNum", top.optLong("tnum")),
                pv = top.optLong("pv"),
                followNum = top.optLong("followNum"),
            ),
            threads = tagThreadsFrom(tl?.optJSONArray("threadList")),
            nextPage = tl?.optBoolean("nextPage", false) ?: false,
            page = 1,
        )
    }

    /** m.hupu.com/api/v2/bbs/tagThreads JSON → {code:200,data:{nextPage,threadList}} */
    fun parseTagThreads(json: String, page: Int): HupuTagPage? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        if (root.optInt("code", -1) != 200) return null
        val data = root.optJSONObject("data") ?: return null
        return HupuTagPage(
            threads = tagThreadsFrom(data.optJSONArray("threadList")),
            nextPage = data.optBoolean("nextPage", false),
            page = page,
        )
    }

    private fun tagThreadsFrom(arr: org.json.JSONArray?): List<HupuTagThread> {
        if (arr == null) return emptyList()
        val out = mutableListOf<HupuTagThread>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pics = mutableListOf<String>()
            o.optJSONArray("picList")?.let { pa ->
                for (j in 0 until pa.length()) {
                    pa.optJSONObject(j)?.optString("url")?.takeIf { it.isNotBlank() }?.let(pics::add)
                }
            }
            out += HupuTagThread(
                tid = o.optString("tid"),
                userName = o.optString("userName"),
                header = o.optString("header").ifEmpty { o.optString("userHeader") },
                time = o.optString("time"),
                title = o.optString("title"),
                summary = o.optString("summary"),
                replies = o.optInt("replies"),
                recommendCount = o.optInt("recommendCount"),
                shareNum = o.optInt("shareNum"),
                pics = pics,
                video = o.optJSONObject("video")?.optString("src").orEmpty()
                    .ifEmpty { o.optString("video") },
            )
        }
        return out
    }

    private fun threadFrom(o: JSONObject): HupuThread = HupuThread(
        tid = o.optString("tid"),
        title = o.optString("title"),
        fid = o.optString("fid"),
        cover = o.optString("cover").ifEmpty { null },
        desc = o.optString("desc").ifEmpty { null },
        lights = o.optInt("recommend", o.optInt("lights", 0)),
        replies = o.optInt("replies", 0),
        read = o.optInt("read", 0),
        createdAt = o.optLong("createdAt", 0L),
        createdAtText = o.optString("createdAtFormat"),
        // 1.189: 主楼发布地（实测 detail JSON thread.location，如「上海」；部分帖为空）
        location = o.optString("location"),
        hasVideo = o.optBoolean("hasVideo", false),
        video = o.optString("video"),
        videoCover = o.optString("videoCover"),
        author = authorFrom(o.optJSONObject("author")),
        topic = o.optJSONObject("topic")?.let {
            HupuTopic(
                topicId = it.optString("topicId"),
                name = it.optString("name"),
                url = it.optString("url"),
            )
        },
        url = o.optString("url"),
        titleFont = o.optString("titleFont"),
    )

    /**
     * PC 个人中心 getUserInfo JSON → HupuUserProfile（不含列表，列表走分页端点）。
     */
    fun parseSpaceUserInfo(json: String): HupuUserProfile? {
        return try {
            val o = org.json.JSONObject(json)
            if (o.optInt("code") != 1) return null
            val u = o.optJSONObject("data") ?: return null
            val euid = u.optLong("euid", 0L)
            HupuUserProfile(
                euid = if (euid != 0L) euid.toString() else u.optLong("puid", 0L).toString(),
                puid = u.optLong("puid", 0L).toString(),
                name = u.optString("nickname"),
                avatar = u.optString("header"),
                // 1.190: PC getUserInfo 也可能只下发 location（location_str 为空串）→ 回退读取
                locationStr = u.optString("location_str").ifEmpty { u.optString("location") },
                regTimeStr = u.optString("reg_time_str"),
                level = u.optString("bbsUserLevel"),
                levelDesc = u.optString("bbsUserLevelDesc"),
                levelColor = u.optString("bbsUserLevelColor"),
                levelScore = u.optLong("bbsUserLevelScore", 0L),
                nextLevelScore = u.optLong("bbsUserNextLevelScore", 0L),
                levelPercent = u.optDouble("bbsUserLevelPercent", 0.0),
                followers = u.optInt("be_follow_count", 0),
                following = u.optInt("follow_count", 0),
                beLightCount = u.optInt("be_light_count", 0),
                beRecommendCount = u.optInt("be_recommend_count", 0),
                msgCount = u.optInt("bbs_msg_count", 0),
                postCount = u.optInt("bbs_post_count", 0),
                recommendCount = u.optInt("bbs_recommend_count", 0),
                favoriteCount = u.optInt("bbs_favorite_count", 0),
                // 1.190: PC getUserInfo 下发 reputation 对象（{detail_url, value}），此前未读取，
                // 导致用户主页「声望」恒为 0 而被整段隐藏（网页有、App 没有）
                reputation = u.optJSONObject("reputation")?.optInt("value", 0) ?: 0,
                isSelf = u.optInt("is_self", 0) == 1,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 1.190: 帖子/回帖封面统一解析（用户主页发帖/回帖/推荐/收藏四类列表共用）。
     *
     * 实测数据源里 `pics` 是**对象数组**：`[{"url":"https://…","width":1256,"height":686,
     * "is_gif":0,"type":"common"}]`；旧代码用 `optString(0)` 取第一项，拿到的是整个对象的
     * `toString()`（形如 `{"url":…}`）而不是 URL —— Coil 加载失败 → 列表右侧一直留一块
     * 84×60 的空洞，把标题压窄导致「还有很大空间就换行」，且缩略图从不显示。
     *
     * 这里同时兼容对象数组与字符串数组两种形态，并只接受 http(s) 真实地址，
     * 避免把脏值喂给图片加载器。
     */
    fun coverFrom(o: JSONObject?): String? {
        o ?: return null
        val arr = o.optJSONArray("pics") ?: o.optJSONArray("picInfos")
        if (arr != null && arr.length() > 0) {
            val url = arr.optJSONObject(0)?.optString("url").orEmpty().ifEmpty { arr.optString(0) }
            if (url.startsWith("http")) return url
        }
        // 兜底：首页信息流用的封面字段名是 cover（列表接口若换字段名也能接住）
        return o.optString("cover").takeIf { it.startsWith("http") }
    }

    private fun authorFrom(o: JSONObject?): HupuAuthor? {
        o ?: return null
        val name = o.optString("puname").ifEmpty { o.optString("username") }
        if (name.isEmpty()) return null
        // url 字段承载头像地址（详情页 author.header）；euid 承载加密用户 id（用户主页跳转用）
        return HupuAuthor(
            puid = o.optString("puid"),
            name = name,
            url = o.optString("header"),
            euid = o.optString("euid"),
        )
    }

    /**
     * 批N-1：`m.hupu.com/api/v2/bbs-thread/{tid}` → 转发数。
     * 响应 `data.t_detail.share`（字符串数字）；同时回传 `hits` 备用。
     * 实测匿名可读；无字段返回 null。
     */
    fun parseThreadShareNum(json: String): Int? {
        return try {
            val o = JSONObject(json)
            o.optJSONObject("data")?.optJSONObject("t_detail")
                ?.optString("share")?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /** 批N-1：同接口的发布时间（`data.thread.createdAtFormat`，如「2小时前」） */
    fun parseThreadCreatedAt(json: String): String {
        return try {
            val o = JSONObject(json)
            if (o.optInt("code", 0) != 200) return ""
            o.optJSONObject("data")?.optJSONObject("thread")
                ?.optString("createdAtFormat").orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    /** 批O-1：同接口的发布时刻（`data.thread.createdAt`，ms epoch——可缓存、转 ISO 走 relativeTime） */
    fun parseThreadCreatedAtMs(json: String): Long {
        return try {
            val o = JSONObject(json)
            if (o.optInt("code", 0) != 200) return 0L
            o.optJSONObject("data")?.optJSONObject("thread")
                ?.optLong("createdAt") ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 1.222: `/api/v2/threads?tid=` 详情接口里的作者。
     * 首页热帖列表不带 author，黑名单过滤需要按 tid 补取作者时用这里。
     */
    fun parseThreadAuthor(json: String): HupuAuthor? {
        return try {
            val o = JSONObject(json)
            if (o.optInt("code", 0) != 200) return null
            val t = o.optJSONObject("data")?.optJSONObject("thread") ?: return null
            authorFrom(t.optJSONObject("author"))
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <T> JSONArray.mapNotNull(transform: (JSONObject?) -> T?): List<T> {
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            transform(optJSONObject(i))?.let { out += it }
        }
        return out
    }

    // ---------- 用户主页（m.hupu.com/user/{euid}，移动 UA SSR） ----------
    /**
     * 解析用户主页。NEXT_DATA pageProps 一次给出：
     * userInfoData（资料卡全字段）+ threadList（TA 的主题帖 20 条）+ replyList（TA 的回帖 20 条）。
     * 官方移动网页同为 20+20 预览口径（?/?page/?type 均被忽略，实测），翻页留待后续版本。
     */
    fun parseUserProfile(html: String): HupuUserProfile? {
        val next = extractNextData(html) ?: return null
        val pp = next.optJSONObject("props")?.optJSONObject("pageProps") ?: return null
        val u = pp.optJSONObject("userInfoData") ?: return null
        val puid = u.optString("puid")
        val name = u.optString("nickname")
        if (name.isEmpty() && puid.isEmpty()) return null
        val threads = mutableListOf<HupuProfileThread>()
        val tl = pp.optJSONArray("threadList")
        for (i in 0 until (tl?.length() ?: 0)) {
            val o = tl?.optJSONObject(i) ?: continue
            val tid = o.optString("tid")
            if (tid.isEmpty()) continue
            threads += HupuProfileThread(
                tid = tid,
                title = o.optString("title"),
                forumName = o.optString("forum_name"),
                topicName = o.optString("topic_name"),
                replies = o.optInt("replies", 0),
                recommendNum = o.optInt("recommend_num", 0),
                createdAtText = o.optString("lastpost_time_text").ifEmpty { o.optString("create_time") },
                summary = o.optString("summary"),
                // 1.190: pics 为对象数组，需取 url（旧写法 optString(0) 拿到的是对象 toString）
                cover = coverFrom(o),
            )
        }
        val replies = mutableListOf<HupuProfileReply>()
        val rl = pp.optJSONArray("replyList")
        for (i in 0 until (rl?.length() ?: 0)) {
            val o = rl?.optJSONObject(i) ?: continue
            val pid = o.optString("pid")
            if (pid.isEmpty()) continue
            replies += HupuProfileReply(
                pid = pid,
                tid = o.optString("tid"),
                content = o.optString("content"),
                formatTime = o.optString("formatTime"),
                lights = o.optInt("allLightCount", 0),
                threadTitle = o.optString("title"),
                // 1.190: 回帖封面同样在 pics/picInfos 里，且是对象数组
                cover = coverFrom(o),
            )
        }
        val rep = u.optJSONObject("reputation")
        return HupuUserProfile(
            // 该页未下发 euid（仅 puid）；m.hupu.com/user/{puid} 实测同样返回完整 SSR，直接用 puid
            euid = pp.optString("euid").ifEmpty { u.optString("euid") }.ifEmpty { puid },
            puid = puid,
            name = name,
            avatar = u.optString("header"),
            // 1.190: 移动版 SSR 的 userInfoData 里 location_str 常为空串，真正的 IP 属地在
            // location（实测 m.hupu.com/user/119424238：location="山西" / location_str=""）
            // → 回退读取，否则用户主页会缺「IP 属地」（网页有、App 没有）。
            locationStr = u.optString("location_str").ifEmpty { u.optString("location") },
            regTimeStr = u.optString("reg_time_str"),
            level = u.optString("bbsUserLevel"),
            levelDesc = u.optString("bbsUserLevelDesc"),
            levelColor = u.optString("bbsUserLevelColor"),
            levelScore = u.optLong("bbsUserLevelScore", 0L),
            nextLevelScore = u.optLong("bbsUserNextLevelScore", 0L),
            levelPercent = u.optDouble("bbsUserLevelPercent", 0.0),
            followers = u.optInt("be_follow_count", 0),
            following = u.optInt("follow_count", 0),
            beLightCount = u.optInt("be_light_count", 0),
            beRecommendCount = u.optInt("be_recommend_count", 0),
            msgCount = u.optInt("bbs_msg_count", 0),
            postCount = u.optInt("bbs_post_count", 0),
            reputation = rep?.optInt("value", 0) ?: 0,
            threads = threads,
            replies = replies,
        )
    }

    // ---------- 正文 HTML 顺序切分（1.122 内联投票） ----------

    /** 正文 HTML 顺序 token：普通文本片段 / 图片 / 投票占位 */
    sealed class ContentToken {
        data class Text(val html: String) : ContentToken()
        data class Image(val url: String) : ContentToken()
        data class Vote(val voteId: Int) : ContentToken()
    }

    /**
     * 按出现顺序把正文 HTML 切成 文本 / 图片 / 投票 三类 token。
     * 图片 `<img src>` 与投票占位 `<span data-type="vote" data-vote-id="N">` 各成一个 token，
     * 其余原样作为 Text token（由调用方再按段落细分）。
     *
     * 实测 content 形态：
     *   `<p>你最喜欢谁呢</p><p><span data-type="vote" data-vote-id="11333655"></span></p><p>做出你的选择吧</p>`
     */
    fun contentTokens(html: String?): List<ContentToken> {
        val out = mutableListOf<ContentToken>()
        if (html.isNullOrBlank()) return out
        val tokenRe = Regex(
            """(<img[^>]*?src\s*=\s*["']([^"']+)["'][^>]*?>)|(<span[^>]*?data-type\s*=\s*["']vote["'][^>]*?>)""",
            RegexOption.IGNORE_CASE,
        )
        val voteIdRe = Regex("""data-vote-id\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        var cursor = 0
        for (m in tokenRe.findAll(html)) {
            if (m.range.first > cursor) out += ContentToken.Text(html.substring(cursor, m.range.first))
            cursor = m.range.last + 1
            val imgSrc = m.groups[2]?.value
            if (imgSrc != null) {
                out += ContentToken.Image(imgSrc)
            } else {
                val vid = voteIdRe.find(m.value)?.groupValues?.get(1)?.toIntOrNull()
                if (vid != null && vid > 0) out += ContentToken.Vote(vid)
            }
        }
        if (cursor < html.length) out += ContentToken.Text(html.substring(cursor))
        return out
    }
}