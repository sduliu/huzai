package com.kudo.android.hupu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * 虎扑数据仓库 —— UI 层唯一入口。
 * 所有方法挂起执行，内部处理缓存/网络/解析。
 * 解析统一跑在 Dispatchers.Default：大 JSON/HTML 在主线程解析会在
 * 盖入动画期间掉帧（赛程 256KB、评分树 222KB 等实测体积）。
 */
class HupuRepository {

    /** 批F-4：官方热榜（m.hupu.com/hot SSR，30条/页匿名首屏、totalElements≈50；失败空表） */
    suspend fun hotTags(refresh: Boolean = false): List<HupuHotTag> {
        val html = HupuApi.fetchMobileHtml("/hot", forceNetwork = refresh) ?: return emptyList()
        return try { HupuParser.parseHotTags(html) } catch (e: Exception) { emptyList() }
    }

    /** 批F-4：tag 聚合页首屏（m.hupu.com/tag/{id} SSR；失败 null） */
    suspend fun tagPage(tagId: String, refresh: Boolean = false): HupuTagPage? {
        val html = HupuApi.fetchMobileHtml("/tag/$tagId", forceNetwork = refresh) ?: return null
        return try { HupuParser.parseTagPage(html) } catch (e: Exception) { null }
    }

    /**
     * 批F-4：tag 帖分页（m.hupu.com/api/v2/bbs/tagThreads?tagType= 空串=非播客、pageSize=20，官方页 JS 同款）。
     * 网络/非200返回 null（与「nextPage=false 榜尾」区分）。
     */
    suspend fun tagThreads(tagId: String, page: Int): HupuTagPage? {
        val json = HupuApi.getMobileJson(
            "/api/v2/bbs/tagThreads?tagId=$tagId&tagType=&page=$page&pageSize=20"
        ) ?: return null
        return try { HupuParser.parseTagThreads(json, page) } catch (e: Exception) { null }
    }

    /** 版块热帖页（首页默认：步行街 /all-gambia） */
    suspend fun board(url: String = "/all-gambia", refresh: Boolean = false): HupuBoardPage? {
        val html = HupuApi.fetchHtml(url, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseBoardPage(html) }
    }

    /** 官方移动站「综合」流（m.hupu.com 首页 SSR res[]）。失败返回空表。 */
    suspend fun mobileHome(refresh: Boolean = false): List<HupuThread> {
        val html = HupuApi.fetchMobileHomeHtml(forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuParser.parseMobileHome(html) }
    }

    /**
     * 1.222: 单条帖子的作者（`/api/v2/threads?tid=`）。
     * 首页热帖列表不带 author，黑名单过滤前需要按 tid 补齐作者身份。
     */
    suspend fun threadAuthor(tid: String): HupuAuthor? {
        val json = HupuApi.fetchThreadAuthorJson(tid) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseThreadAuthor(json) }
    }

    /**
     * 批N-1：帖子转发数（`m.hupu.com/api/v2/bbs-thread/{tid}` 的 t_detail.share）。
     * 信息流 SSR 列表不带该字段，首页热帖卡按 tid 惰性补齐；失败返回 null。
     */
    suspend fun threadShareNum(tid: String): Int? {
        val json = HupuApi.getMobileJson("/api/v2/bbs-thread/$tid") ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseThreadShareNum(json) }
    }

    /**
     * 批N-1：热帖卡作者行补齐（`threads?tid=` 单次请求拿三样）：
     * first = author（头像+昵称），second = createdAtFormat（「4小时前」相对文本，会变陈旧），
     * third = createdAt（ms epoch——批O-1 起随作者缓存落盘，转 ISO 后 relativeTime 恒定准确）。
     */
    suspend fun threadCardHead(tid: String): Triple<HupuAuthor?, String, Long> {
        val json = HupuApi.fetchThreadAuthorJson(tid) ?: return Triple(null, "", 0L)
        return withContext(Dispatchers.Default) {
            Triple(
                HupuParser.parseThreadAuthor(json),
                HupuParser.parseThreadCreatedAt(json),
                HupuParser.parseThreadCreatedAtMs(json),
            )
        }
    }

    /** 全站版块/话题导航树（取自任一 /all-* 页） */
    suspend fun categories(refresh: Boolean = false): List<HupuCategory> {
        val html = HupuApi.fetchHtml("/all-gambia", forceNetwork = refresh) ?: return emptyList()
        val list = withContext(Dispatchers.Default) { HupuParser.parseCategories(html) }
        // 批Q-1：目录解析结果持久化（SWR——专区冷启动先吃盘立即上屏，本通道后台合回）
        if (list.isNotEmpty()) HupuPrefs.saveZoneCategories(list)
        return list
    }

    /**
     * 批L-2：版块档案补充信息（JRs 成员数等）。
     * 数据在 m站 zone 页 pageProps.zoneData；走 Next.js _next/data JSON 端点，
     * buildId 随部署变化——先取 /zone/{id} 页提取 buildId 缓存复用，404 时重取一次。
     */
    suspend fun zoneInfo(topicId: String): HupuZoneInfo? {
        suspend fun buildId(): String? {
            cachedZoneBuildId?.let { return it }
            val html = HupuApi.fetchMobileHtml("/zone/$topicId") ?: return null
            return ZONE_BUILD_ID_RE.find(html)?.groupValues?.get(1)
                ?.also { cachedZoneBuildId = it }
        }
        suspend fun fetch(bid: String) = HupuApi.getMobileJson(
            "/_next/data/$bid/zone/$topicId.json?id=$topicId",
        )
        val bid = buildId() ?: return null
        var json = fetch(bid)
        if (json == null) {
            // buildId 过期（站点重新部署）——清缓存重取一次
            cachedZoneBuildId = null
            json = buildId()?.let { fetch(it) }
        }
        return json?.let { withContext(Dispatchers.Default) { HupuParser.parseZoneInfo(it) } }
    }

    /** 批L-1：专区版块搜索（m.hupu.com/api/v2/search2 type=topic，匿名可读） */
    suspend fun searchTopics(query: String): List<HupuTopicInfo> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val json = HupuApi.getMobileJson(
            "/api/v2/search2?keyword=$q&puid=0&type=topic&topicId=0&page=1",
        ) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuParser.parseTopicSearch(json) }
    }

    /**
     * 批Z-1：搜索分类「版块」（search2 type=topic 分页变体，匿名）。
     * hasNextPage=1 时可继续翻页；网络/JSON 失败返回 null（交页内错误态）。
     */
    suspend fun searchTopicsPage(query: String, page: Int = 1): HupuSearchSlice<HupuTopicInfo>? {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val json = HupuApi.getMobileJson(
            "/api/v2/search2?keyword=$q&puid=0&type=topic&topicId=0&page=$page",
        ) ?: return null
        val (items, hasNext) = withContext(Dispatchers.Default) { HupuParser.parseTopicSearchPage(json) }
        return HupuSearchSlice(items, page, hasNext)
    }

    /**
     * 批Z-1：搜索分类「用户」（search2 type=users，匿名实测 count=2000）。
     * 失败返回 null；空结果返回空 items（页内显式空态）。
     */
    suspend fun searchUsers(query: String, page: Int = 1): HupuSearchSlice<HupuSearchUser>? {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val json = HupuApi.getMobileJson(
            "/api/v2/search2?keyword=$q&puid=0&type=users&topicId=0&page=$page",
        ) ?: return null
        val (items, hasNext) = withContext(Dispatchers.Default) { HupuParser.parseUserSearch(json) }
        return HupuSearchSlice(items, page, hasNext)
    }

    /** 话题列表页（含分页）。url 如 /topic、/topic-daily；page 从 1 开始 */
    suspend fun topicPage(url: String, page: Int = 1, refresh: Boolean = false): HupuTopicPage? {
        val path = if (page <= 1) url else "$url-$page"
        val html = HupuApi.fetchHtml(path, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseTopicPage(html) }
    }

    /**
     * 1.223t: 同一 tid 的**在途请求去重**（进程级共享，跨页面实例有效）。
     * 场景：点开 A（请求在途）→ 没加载完就退出 → 立刻再点 A：`openedThread` 从 null 变回 A
     * 会让加载 effect 重启，同一个 /A.html 就被发了两次——弱网/限流下雪上加霜。
     * 复用同一在途请求：少一次网络往返、少一次全局节流排队，也无旧请求覆盖新状态的竞态。
     */
    private companion object {
        val threadScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )
        val inflightThreads =
            java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<HupuThreadDetail?>>()

        /** 批L-2：m站 Next.js buildId（zoneInfo 端点路径用；部署变化时自动重取） */
        @Volatile var cachedZoneBuildId: String? = null
        val ZONE_BUILD_ID_RE = Regex("\"buildId\":\"([^\"]+)\"")
    }

    /** 帖子详情（第 1 页回复） */
    suspend fun threadDetail(tid: String, refresh: Boolean = false): HupuThreadDetail? {
        // 1.223t: 同一 tid 已有在途请求 → 直接复用（不重复发请求）
        inflightThreads[tid]?.let { return it.await() }
        val job = threadScope.async { loadThreadDetail(tid, refresh) }
        inflightThreads[tid] = job
        // 1.223ac: 等请求**真正结束**再摘表 —— 原先写在 finally 里，调用方被取消（切页/销毁）时会提前
        // 摘除，那段时间内的重复点击会再发一次相同请求（去重出现缝隙）。
        job.invokeOnCompletion { inflightThreads.remove(tid, job) }
        return job.await()
    }

    private suspend fun loadThreadDetail(tid: String, refresh: Boolean): HupuThreadDetail? {
        var html = HupuApi.fetchHtml("/$tid.html", forceNetwork = refresh)
        // 1.223s: 首拉失败自动重试一次（500ms 退避）——真机反馈的「打开帖子经常提示网络错误」
        // 多为瞬时抖动 / 被限流，重试一次能挡掉大部分假失败；仍失败才进入失败态。
        if (html == null) {
            kotlinx.coroutines.delay(500)
            html = HupuApi.fetchHtml("/$tid.html", forceNetwork = refresh)
        }
        val h = html
            // 1.123: 强刷失败降级磁盘缓存（TTL 内）——断网/服务器抖动时详情页仍可打开
            ?: if (refresh) HupuCache.get("/$tid.html") else null
        // 批Xfix-1：移动 UA/边缘节点会把 bbs.hupu.com/{tid}.html 307 到 m.hupu.com 移动页
        //（SSR 结构不同）——同一份 HTML 先试桌面结构再试移动结构，不额外发请求。
        var det = h?.let {
            withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(it) }
                ?: withContext(Dispatchers.Default) { HupuParser.parseMobileThreadDetail(it) }
        }
        if (h != null && det == null) {
            // 批X-1：错误页 SSR（detailErrorInfo≠200，如 4044/未同步壳页）也带 __NEXT_DATA__ 会过壳页
            // 门槛被缓存，毒化条目让该 tid 在 TTL 内恒报「内容不存在」。剔除后强制走网重试一次。
            HupuCache.remove("/$tid.html")
            det = HupuApi.fetchHtml("/$tid.html", forceNetwork = true)
                ?.let {
                    withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(it) }
                        ?: withContext(Dispatchers.Default) { HupuParser.parseMobileThreadDetail(it) }
                }
        }
        if (det == null) {
            // 批Xfix-1：桌面链路仍失败（网络层失败/持续错误页/重定向移动页仍异常）→ 移动 SSR
            // 兜底（不同 host+UA，实测匿名完整返回 threadData/threadInfo/initialRepliesData）。
            // 移动页同样有「含 __NEXT_DATA__ 的错误页被缓存」风险（m: 前缀键）→ 同机制剔除重拉。
            val mpath = "/bbs/$tid.html"
            var mh = HupuApi.fetchMobileHtml(mpath)
            var mdet = mh?.let { withContext(Dispatchers.Default) { HupuParser.parseMobileThreadDetail(it) } }
            if (mh != null && mdet == null) {
                HupuCache.remove("m:$mpath")
                mh = HupuApi.fetchMobileHtml(mpath, forceNetwork = true)
                mdet = mh?.let { withContext(Dispatchers.Default) { HupuParser.parseMobileThreadDetail(it) } }
            }
            det = mdet
        }
        // 1.223z: 详情加载成功 → 回写浏览记录，用**真实版块名**补齐。
        // 通知中心/回帖列表等入口首次写入时拿不到版块名，这里在数据到手后自愈。
        det?.thread?.let { t ->
            try {
                HupuPrefs.addHistory(
                    HupuHistoryEntry(
                        tid = t.tid.ifBlank { tid },
                        title = t.title,
                        topicName = t.topic?.name ?: "",
                        lights = t.lights,
                        replies = t.replies,
                        read = t.read,
                        visitedAt = System.currentTimeMillis(),
                    )
                )
            } catch (_: Exception) {
                // 历史写入失败绝不能影响详情加载
            }
        }
        return det
    }

    /**
     * 1.123: 只读磁盘缓存（无网络）——详情页每次进入都强制刷新时效性，
     * 但进程重启/跨页首进时先用缓存秒开第一帧，再后台拉最新。
     */
    suspend fun threadDetailCached(tid: String): HupuThreadDetail? {
        val html = HupuCache.get("/$tid.html") ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(html) }
    }

    /** 帖子回复翻页 */
    suspend fun threadReplies(tid: String, page: Int): HupuThreadDetail? {
        if (page <= 1) return threadDetail(tid)
        val path = "/$tid-$page.html"
        val html = HupuApi.fetchHtml(path) ?: return null
        var det = withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(html) }
        if (det == null) {
            // 批X-1：与详情同机制——翻页错误 SSR 毒化缓存时剔除并重拉一次
            HupuCache.remove(path)
            det = HupuApi.fetchHtml(path, forceNetwork = true)
                ?.let { withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(it) } }
        }
        return det
    }

    /** 1.185: 方向感知——是否还有更多回复 */
    fun hasMoreReplies(cur: HupuThreadDetail): Boolean =
        if (cur.descReplies) cur.replyPage > 1 else cur.replyPage < cur.replyTotalPages

    /** 1.185: 首屏/切向——desc=true 时从最后一页取（页内反转），正序同 threadDetail */
    suspend fun threadDetailDirected(tid: String, desc: Boolean, refresh: Boolean = false): HupuThreadDetail? {
        val base = threadDetail(tid, refresh) ?: return null
        if (!desc) return base.copy(descReplies = false)
        val total = base.replyTotalPages
        if (total <= 1) return base.copy(replies = base.replies.reversed(), descReplies = true)
        val last = threadReplies(tid, total) ?: return base.copy(descReplies = true)
        return base.copy(replies = last.replies.reversed(), replyPage = total, descReplies = true)
    }

    /** 1.185: 方向感知翻页（倒序递减）。返回 null = 无更多 */
    suspend fun threadRepliesNext(tid: String, cur: HupuThreadDetail): HupuThreadDetail? {
        val total = cur.replyTotalPages
        val next = if (cur.descReplies) cur.replyPage - 1 else cur.replyPage + 1
        if (next < 1 || next > total) return null
        return threadReplies(tid, next)?.let {
            it.copy(replies = if (cur.descReplies) it.replies.reversed() else it.replies, descReplies = cur.descReplies)
        }
    }

    /** 1.185: 仅切换回帖方向——保留主楼与统计（thread），只替换 replies，避免数字抖动 */
    suspend fun repliesDirected(tid: String, base: HupuThreadDetail, desc: Boolean): HupuThreadDetail? {
        if (!desc) {
            val p1 = threadReplies(tid, 1) ?: return null
            return base.copy(replies = p1.replies, replyPage = 1, descReplies = false)
        }
        val total = base.replyTotalPages
        if (total <= 1) return base.copy(replies = base.replies.reversed(), descReplies = true)
        val last = threadReplies(tid, total) ?: return null
        return base.copy(replies = last.replies.reversed(), replyPage = total, descReplies = true)
    }

    /** 用户主页（资料卡 + 主题帖/回帖首屏，SSR 一次请求） */
    // ---------- PC 个人中心（my.hupu.com 契约，登录态） ----------

    /**
     * getUserInfo：全量资料卡（声望/IP/真实计数/是否本人）。
     * PC 端点需要登录 cookie；未登录或失败时回退 m.hupu.com/user/{euid} 移动 SSR
     * （匿名可读，顺带下发 20+20 帖子/回帖首屏列表）。
     */
    suspend fun spaceUserInfo(euid: String): HupuUserProfile? {
        // 1.190: 导航快车道（200ms 间隔，不等全局 1s 时间片）+ 结果写进程级缓存
        if (HupuAccount.isLoggedIn) {
            HupuApi.fetchSpaceApi("getUserInfo?euid=$euid", fast = true)
                ?.let { HupuParser.parseSpaceUserInfo(it) }
                ?.let { hupuUserInfoCache.put(euid, it); return it }
        }
        // 批X2-1：错误 SSR（无 userInfoData）同样含 __NEXT_DATA__ 会被 fetchUserHtml
        // 缓存——解析 null 时剔除条目并强制重拉一次，仍失败才判真不存在（同 X-1 详情机制）。
        val html = HupuApi.fetchUserHtml(euid)
        var p = html?.let { HupuParser.parseUserProfile(it) }
        if (p == null && html != null) {
            HupuCache.remove("/user/$euid")
            p = HupuApi.fetchUserHtml(euid, forceNetwork = true)
                ?.let { HupuParser.parseUserProfile(it) }
        }
        return p?.also { hupuUserInfoCache.put(euid, it) }
    }

    /**
     * 1.190: 读取资料卡缓存。命中即先渲染内容（零骨架），随后照常请求刷新覆盖。
     * 覆盖「用户主页 → 返回 → 再进同一人」「详情点作者 → 返回 → 再点」等高频往返路径。
     */
    fun cachedUserInfo(euid: String): HupuUserProfile? = hupuUserInfoCache.get(euid)

    /** 发帖列表（page 分页，数组直出） */
    suspend fun spaceThreads(euid: String, page: Int): List<HupuProfileThread> {
        val body = HupuApi.fetchSpaceApi("getThreadList?euid=$euid&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<HupuProfileThread>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileThread(
                    tid = t.optString("tid"),
                    title = t.optString("title"),
                    forumName = t.optString("forum_name"),
                    topicName = t.optString("topic_name"),
                    replies = t.optInt("replies", 0),
                    recommendNum = t.optInt("recommend_num", 0),
                    createdAtText = t.optLong("create_time", 0L).takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it * 1000))
                    } ?: "",
                    summary = t.optString("summary"),
                    // 1.190: pics 是对象数组（[{"url":…}]），旧写法 optString(0) 拿到的是对象 toString
                    cover = HupuParser.coverFrom(t),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 回帖列表（maxTime 游标分页，pageSize=10） */
    suspend fun spaceReplies(euid: String, page: Int, maxTime: Long = 0L): Pair<List<HupuProfileReply>, Long> {
        val body = HupuApi.fetchSpaceApi("getReplyList?euid=$euid&maxTime=$maxTime&page=$page&pageSize=10") ?: return Pair(emptyList(), 0L)
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return Pair(emptyList(), maxTime)
            val d = o.optJSONObject("data") ?: return Pair(emptyList(), maxTime)
            val arr = d.optJSONArray("replyWithQuoteDtoList") ?: return Pair(emptyList(), maxTime)
            val out = mutableListOf<HupuProfileReply>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileReply(
                    pid = t.optString("pid"),
                    tid = t.optString("tid"),
                    content = t.optString("content"),
                    formatTime = t.optString("formatTime"),
                    lights = t.optInt("lightCount", 0),
                    threadTitle = t.optString("title").ifEmpty { t.optString("topicName") },
                    // 1.190: 回帖封面同样走统一解析（pics/picInfos 皆为对象数组）
                    cover = HupuParser.coverFrom(t),
                )
            }
            Pair(out, d.optLong("maxTime", maxTime))
        } catch (e: Exception) {
            Pair(emptyList(), maxTime)
        }
    }

    /** 推荐列表（page 分页，data.content 数组） */
    suspend fun spaceRecommends(euid: String, page: Int): List<HupuProfileThread> {
        val body = HupuApi.fetchSpaceApi("getRecommendList?euid=$euid&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val d = o.optJSONObject("data") ?: return emptyList()
            val arr = d.optJSONArray("content") ?: return emptyList()
            val out = mutableListOf<HupuProfileThread>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileThread(
                    tid = t.optString("tid"),
                    title = t.optString("title"),
                    forumName = t.optString("forum_name"),
                    topicName = t.optString("topic_name"),
                    replies = t.optInt("replies", 0),
                    recommendNum = t.optInt("recommend_num", 0),
                    createdAtText = t.optLong("create_time", 0L).takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it * 1000))
                    } ?: "",
                    summary = t.optString("summary"),
                    // 1.190: pics 是对象数组（[{"url":…}]），旧写法 optString(0) 拿到的是对象 toString
                    cover = HupuParser.coverFrom(t),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 收藏列表（仅自己可见，page 分页，条目同发帖结构） */
    suspend fun spaceFavorites(euid: String, page: Int): List<HupuProfileThread> {
        val body = HupuApi.fetchSpaceApi("getFavoritesList?euid=$euid&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val d = o.optJSONObject("data") ?: return emptyList()
            val arr = d.optJSONArray("content") ?: return emptyList()
            val out = mutableListOf<HupuProfileThread>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileThread(
                    tid = t.optString("tid"),
                    title = t.optString("title"),
                    forumName = t.optString("forum_name"),
                    topicName = t.optString("topic_name"),
                    replies = t.optInt("replies", 0),
                    recommendNum = t.optInt("recommend_num", 0),
                    createdAtText = t.optLong("create_time", 0L).takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it * 1000))
                    } ?: "",
                    summary = t.optString("summary"),
                    // 1.190: pics 是对象数组（[{"url":…}]），旧写法 optString(0) 拿到的是对象 toString
                    cover = HupuParser.coverFrom(t),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 关注列表（type=1 TA关注的/我关注的，type=2 关注TA的/关注我的） */
    suspend fun spaceFollows(euid: String, type: Int, page: Int): List<HupuFollowUser> {
        val body = HupuApi.fetchSpaceApi("getUserFollowList?euid=$euid&type=$type&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<HupuFollowUser>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                // \u5b98\u65b9\u5b57\u6bb5\u65b9\u5411\u76f8\u5bf9\uff1atype=1(\u4ed6\u5173\u6ce8\u7684)\u5bf9\u65b9\u6807\u8bc6\u5728 buddyPuid\uff1btype=2(\u5173\u6ce8\u4ed6\u7684)\u5728 uid
                val oid = if (type == 1) t.optLong("buddyPuid", 0L) else t.optLong("uid", 0L)
                out += HupuFollowUser(
                    puid = oid.toString(),
                    name = t.optString("username"),
                    avatar = t.optString("header"),
                    level = t.optInt("level", -1),
                    fansNum = t.optInt("fansNum", 0),
                    joinDaysText = t.optString("time"),
                    threadsNum = t.optInt("threadsNum", 0),
                    repliesNum = t.optInt("repliesNum", 0),
                    isSelf = t.optInt("isSelf", 0) == 1,
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun userProfile(euid: String, refresh: Boolean = false): HupuUserProfile? {
        val html = HupuApi.fetchUserHtml(euid, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseUserProfile(html) }
    }

    /**
     * 楼中楼：拉取某楼层的子回复（首屏 20 条）。
     * maxpid 非空时翻页。失败返回 null。
     */
    suspend fun floorReplies(tid: String, parent: HupuReply, maxpid: String? = null): HupuFloorReplies? {
        val json = HupuApi.fetchFloorReplies(tid, parent.pid, maxpid) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseFloorReplies(json, parent) }
    }

    /** 搜索页（桌面版 SSR，20 条/页；sortby 六种排序；topicId 专区过滤） */
    suspend fun search(query: String, topicId: String? = null, sortby: String = "general", page: Int = 1, refresh: Boolean = false): HupuSearchPage? {
        val html = HupuApi.fetchSearchHtml(query, topicId, sortby, page, forceNetwork = refresh)
        if (html != null) {
            val parsed = withContext(Dispatchers.Default) { HupuParser.parseSearchPage(html) }
            if (parsed != null) return parsed
        }
        // 批Zfix：桌面通道整体失败（请求失败或 $$data 结构变化）时的降级源。
        // 仅在未启用排序/专区筛选时启用——search2 无 sortby/topicId 等价参数（实测传入被忽略），
        // 带筛选降级会以未过滤结果冒充过滤结果，故此时仍显式失败交页内错误态。
        if (!topicId.isNullOrEmpty() || sortby != "general") return null
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val json = HupuApi.getMobileJson(
            "/api/v2/search2?keyword=$q&puid=0&type=posts&topicId=0&page=$page",
        ) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parsePostSearchJson(json, page) }
    }

    /** 比赛赛程评分（6 大电竞项目）。businessId 如 lol/kog/val/cs2/pubgmobile/pubg */
    suspend fun matchSchedule(businessId: String, refresh: Boolean = false): List<HupuMatchDay> {
        // 1.196：国际足球赛程与 match-api **不同源**（无聚合 businessId），走 m.hupu.com/soccer/schedule 的 SSR
        if (businessId == HupuMatchApi.SOCCER_ID) {
            val html = HupuMatchApi.fetchSoccerScheduleHtml(forceNetwork = refresh) ?: return emptyList()
            return withContext(Dispatchers.Default) { HupuMatchParser.parseSoccerSchedule(html) }
        }
        val json = HupuMatchApi.fetchSchedule(businessId, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSchedule(json) }
    }

    /**
     * 1.196：国际足球比赛 → 评分钥匙 outBizNo（赛程里没有，按需换取；outBizType 恒为 football_match）。
     * 失败返回 null。
     */
    suspend fun footballScoreNo(matchId: String, refresh: Boolean = false): String? =
        HupuMatchApi.fetchFootballScoreNo(matchId, forceNetwork = refresh)

    /** board：板块资讯一页（newsList）。失败 null；空 data 返回空页（区分空态与失败）。 */
    suspend fun boardNews(
        tagCode: String,
        preCount: Int = 0,
        anchorNid: String = "",
        refresh: Boolean = false,
    ): HupuBoardNewsPage? {
        val json = HupuBoardApi.fetchBoardNews(tagCode, preCount, anchorNid, forceNetwork = refresh)
            ?: return null
        return withContext(Dispatchers.Default) { HupuBoardParser.parseBoardNews(json, preCount) }
    }

    /** board：国际足球资讯（m.hupu.com/soccer SSR；newsList 无对应 tagCode）。 */
    suspend fun soccerNews(refresh: Boolean = false): List<HupuBoardNews> {
        val html = HupuBoardApi.fetchSoccerNewsHtml(forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuBoardParser.parseSoccerNews(html) }
    }

    /**
     * board：专区帖子流兜底资讯（无 newsList tagCode 的板块）。
     * [url] 是 bbs.hupu.com 的话题页路径（如 /245 网球场、/252 羽毛球），
     * 线程映射为 [HupuBoardNews]（tid 必非空 → 全部可开帖）。失败返回 null。
     */
    suspend fun topicNews(url: String, page: Int = 1, refresh: Boolean = false): HupuBoardNewsPage? {
        val p = topicPage(url, page, refresh) ?: return null
        val items = p.threads.map { t ->
            HupuBoardNews(
                nid = t.tid,
                tid = t.tid,
                type = "TOPIC",
                title = t.title,
                img = t.cover ?: "",
                link = t.url,
                replies = t.replies,
                lights = t.lights,
                publishTime = t.createdAtText,
            )
        }
        return HupuBoardNewsPage(
            items = items,
            nextPreCount = page + 1,      // 对 topic 复用 preCount 槽位存页码
            anchorNid = items.firstOrNull()?.nid ?: "",
        )
    }

    // ---------- 批B：联赛数据中枢（私有 mobileapi 匿名 GET，见 HupuDataApi 头注释） ----------

    /** 联赛数据 tab 轨道（getDataTabList）。enType=联赛小写；只回接口真实给出的 tab。 */
    suspend fun leagueDataTabs(enType: String, refresh: Boolean = false): List<HupuDataTab> {
        val json = HupuDataApi.fetchDataTabs(enType, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuDataParser.parseDataTabs(json) }
    }

    /** 「全球」赛程（oddsScheduleList）；dayGameData 复用 HupuMatchParser 行结构。 */
    suspend fun globalSchedule(code: String, filterCode: String = "all", refresh: Boolean = false): List<HupuMatchDay> {
        val json = HupuDataApi.fetchGlobalSchedule(code, filterCode, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSchedule(json) }
    }

    /** 球队榜整版：东/西/分区类别 + 技术统计（两次匿名请求聚合）。赛季参数空=当前季。 */
    suspend fun standingsBoard(leagueType: String, season: String = "", stageType: String = "", refresh: Boolean = false): HupuStandingsBoard? {
        val json = HupuDataApi.fetchTeamStandings(leagueType, season, stageType, forceNetwork = refresh)
            ?: return null
        val board = withContext(Dispatchers.Default) { HupuDataParser.parseStandingsBoard(json) }
            ?: return null
        val statsJson = HupuDataApi.fetchTeamStatRanks(leagueType, forceNetwork = refresh)
        val stats = statsJson?.let { withContext(Dispatchers.Default) { HupuDataParser.parseTeamStatRanks(it) } }
            ?: emptyList()
        return board.copy(statCategories = stats)
    }

    /** 赛季选项（rankType 枚举：NBA_TEAM_RANK / NBA_PLAYER_RANK）。 */
    suspend fun rankSeasons(rankType: String, refresh: Boolean = false): List<HupuSeasonOption> {
        val json = HupuDataApi.fetchRankSeasons(rankType, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuDataParser.parseSeasons(json) }
    }

    /** 球员榜整版（基础/进阶维度，每项 top5）。 */
    suspend fun playerRankBoard(leagueType: String, season: String = "", stageType: String = "", refresh: Boolean = false): HupuPlayerRankBoard? {
        val json = HupuDataApi.fetchPlayerSeasonRank(leagueType, season, stageType, forceNetwork = refresh)
            ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parsePlayerRankBoard(json) }
    }

    /** 批F-1：单维度球员全量榜一页（pageNo 20 行/页；返回空 leaders = 已到榜尾）。 */
    suspend fun playerRankPage(
        leagueType: String,
        rankType: String,
        season: String = "",
        stageType: String = "",
        pageNo: Int = 1,
        refresh: Boolean = false,
    ): HupuRankItem? {
        val json = HupuDataApi.fetchPlayerSingleDimRank(leagueType, rankType, season, stageType, pageNo, forceNetwork = refresh)
            ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parsePlayerRankPage(json) }
    }

    /** 选秀整版（年份表 + 指定年顺位）。 */
    suspend fun draftBoard(leagueType: String, year: String?, refresh: Boolean = false): HupuDraftBoard? {
        val yearsJson = HupuDataApi.fetchDraftYears(leagueType, forceNetwork = refresh)
        val years = yearsJson?.let { withContext(Dispatchers.Default) { HupuDataParser.parseDraftYears(it) } }
            ?: emptyList()
        val y = year ?: years.firstOrNull() ?: return null
        val json = HupuDataApi.fetchDraft(leagueType, y, forceNetwork = refresh) ?: return null
        val rounds = withContext(Dispatchers.Default) { HupuDataParser.parseDraftBoard(json, y) }
        return HupuDraftBoard(y, years, rounds)
    }

    /** 伤病表。 */
    suspend fun injuries(leagueType: String, refresh: Boolean = false): List<HupuInjury> {
        val json = HupuDataApi.fetchInjuries(leagueType, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuDataParser.parseInjuries(json) }
    }

    /** 批Y：荣誉榜整版（allPlayerAwardList，20 奖项 × top5 获奖者）。 */
    suspend fun playerAwards(leagueType: String, refresh: Boolean = false): List<HupuAward> {
        val json = HupuDataApi.fetchPlayerAwards(leagueType, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuDataParser.parseAwards(json) }
    }

    /** 批Y：历史榜整版（playerAllDimensionCareerRank，stage/grain 由页内开关选择）。 */
    suspend fun careerRankBoard(
        leagueType: String,
        stage: String,
        grain: String,
        refresh: Boolean = false,
    ): HupuCareerRankBoard? {
        val json = HupuDataApi.fetchCareerRank(leagueType, stage, grain, forceNetwork = refresh)
            ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parseCareerRank(json, stage, grain) }
    }

    /** 批Y：季后赛对阵图（against-plan-chart）。官方数据源空时返回 null → 页内显式空态。 */
    suspend fun againstChart(leagueType: String, refresh: Boolean = false): HupuAgainstBracket? {
        val json = HupuDataApi.fetchAgainstChart(leagueType, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parseAgainstChart(json) }
    }

    /** 单节比分（仅 businessType=basketball 的比赛有效；失败返回 null）。 */
    suspend fun quarterStats(matchId: String, refresh: Boolean = false): HupuQuarterScore? {
        val json = HupuDataApi.fetchQuarterStats(matchId, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parseQuarterStats(json) }
    }

    /** 批U-1：篮球比赛详情头（basketballapi/matchInfo，匿名 GET）。 */
    suspend fun matchInfo(matchId: String, refresh: Boolean = false): HupuMatchInfo? {
        val json = HupuDataApi.fetchMatchInfo(matchId, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parseMatchInfo(json) }
    }

    /** 批U-3：篮球单场球员统计（basketballapi/playerMatchStats，匿名 GET）。 */
    suspend fun matchPlayerStats(matchId: String, refresh: Boolean = false): HupuMatchPlayerStats? {
        val json = HupuDataApi.fetchMatchPlayerStats(matchId, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuDataParser.parseMatchPlayerStats(json) }
    }

    /** board：NBA 积分榜（nba.hupu.com/standings SSR 表格）。失败返回 null。 */
    suspend fun nbaStandings(refresh: Boolean = false): HupuStandings? {
        val html = HupuBoardApi.fetchNbaStandingsHtml(forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuStandingParser.parseNbaStandings(html) }
    }

    /**
     * board：足球积分榜（football-api 两段式：subDataCategory 取最新赛季 →
     * competition/ranking 取榜）。competitionId 如 英超=81、中超=531、欧冠=46。
     * 失败返回 null。
     */
    suspend fun soccerStandings(competitionId: String, refresh: Boolean = false): HupuStandings? {
        val seasons = HupuBoardApi.fetchSoccerSeasonsJson(competitionId, forceNetwork = refresh)
            ?: return null
        val seasonId = withContext(Dispatchers.Default) {
            HupuStandingParser.parseSoccerSeasonId(seasons)
        } ?: return null
        val json = HupuBoardApi.fetchSoccerRankingJson(
            competitionId, seasonId, forceNetwork = refresh,
        ) ?: return null
        return withContext(Dispatchers.Default) { HupuStandingParser.parseSoccerStandings(json) }
    }

    /** 球队页：NBA 球队信息+阵容（nba.hupu.com/teams/{slug} SSR）。失败返回 null。 */
    suspend fun nbaTeamInfo(slug: String, refresh: Boolean = false): HupuTeamInfo? {
        val html = HupuTeamApi.fetchNbaTeamHtml(slug, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuTeamParser.parseNbaTeam(html) }
    }

    /** 球队页：NBA 全季赛程窗（nba.hupu.com/schedule/{slug} SSR，本队视角胜负）。失败返回空表。 */
    suspend fun nbaTeamSchedule(slug: String, refresh: Boolean = false): List<HupuTeamGame> {
        val html = HupuTeamApi.fetchNbaTeamScheduleHtml(slug, forceNetwork = refresh)
            ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuTeamParser.parseNbaTeamSchedule(html) }
    }

    /** 评分树：比赛 → 对局[] → 选手[]（一次请求全量，匿名可用）。失败返回 null。 */
    suspend fun scoreTree(bizType: String, bizNo: String, refresh: Boolean = false): HupuScoreTree? {
        val json = HupuMatchApi.fetchScoreTree(bizType, bizNo, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseScoreTree(json) }
    }

    /** 服务端分组定义（官方分类条数据源，所有赛事通用；flat 结构的「全部/队名/趣评」）。 */
    suspend fun scoreGroups(bizType: String, bizNo: String): List<HupuScoreGroup> {
        val json = HupuMatchApi.fetchSubGroups(bizType, bizNo) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSubGroups(json) }
    }

    /** 分组成员（groupAndSubNodes?nodeId=rootNodeId）。失败返回空列表。 */
    suspend fun scoreGroupNodes(nodeId: Long): List<HupuScoreItem> {
        val json = HupuMatchApi.fetchGroupNodes(nodeId) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseGroupNodes(json) }
    }

    /** 节点详情（第 4 层选手/对局：评分分布+热评）。失败返回 null。 */
    suspend fun scoreSelf(bizType: String, bizNo: String, refresh: Boolean = false): HupuSelfDetail? {
        val json = HupuMatchApi.fetchSelf(bizType, bizNo, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSelfDetail(json) }
    }

    /** 评分区评论（游标分页：cursor=0 取最新页，翻页用上一页返回的 cursor）。失败返回 null。 */
    suspend fun scoreComments(bizType: String, bizNo: String, cursor: Long, refresh: Boolean = false, queryType: String = "latest"): ScoreCommentState? {
        if (queryType == "brightest") {
            // 最亮走独立端点（官方主列表 bright tab 用 hottest，无分页）
            val json = HupuMatchApi.fetchHottestComments(bizType, bizNo, forceNetwork = refresh) ?: return null
            val list = withContext(Dispatchers.Default) { HupuMatchParser.parseHottestComments(json) }
            if (list.isEmpty()) return null
            return ScoreCommentState(comments = list, commentCount = list.size.toLong(), cursor = 0L, hasMore = false)
        }
        val json = HupuMatchApi.fetchComments(bizType, bizNo, cursor, forceNetwork = refresh, queryType = queryType) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseComments(json) }
    }

    /** 楼中楼全量子评论（subCommentList；cursor=0 第一页）。失败返回 null。
     *  1.149: `refresh=true` 绕过 HupuCache 强制网络——回复成功后回填真实数据用。
     */
    suspend fun scoreSubComments(bizType: String, bizNo: String, parentCommentId: String, queryType: String = "latest", cursor: Long = 0L, refresh: Boolean = false): ScoreCommentState? {
        val json = HupuMatchApi.fetchSubComments(bizType, bizNo, parentCommentId, queryType, cursor, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSubComments(json) }
    }

    /** 楼中楼翻页（getMore；cursor=上一页返回的 publishTime）。失败返回 null。 */
    suspend fun scoreSubCommentsMore(bizType: String, bizNo: String, parentCommentId: String, queryType: String = "latest", cursor: Long): ScoreCommentState? {
        val json = HupuMatchApi.fetchSubCommentsMore(bizType, bizNo, parentCommentId, queryType, cursor) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSubComments(json) }
    }

    /** 通用评分首页（SSR，每次刷新换一批）。失败返回空列表。 */
    suspend fun commonSubjects(refresh: Boolean = false): List<HupuCommonSubject> {
        val html = HupuCommonApi.fetchScoreHome(forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuCommonParser.parseScoreHome(html) }
    }

    /** 通用评分主题树（分页）。失败返回 null。 */
    suspend fun commonTree(bizType: String, bizNo: String, page: Int = 1, refresh: Boolean = false): HupuCommonTree? {
        val json = HupuMatchApi.fetchCommonTree(bizType, bizNo, page, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuCommonParser.parseCommonTree(json) }
    }

    /**
     * 1.123: 详情换新合并——服务端新数据替换主楼/计数，已加载的多页回复按 pid 保序合并保留。
     * 场景：每次进入详情页都后台强刷（时效性），但不让已翻页加载过的深层回复被第 1 页覆盖砍短。
     */
    fun mergeThreadDetail(old: HupuThreadDetail?, fresh: HupuThreadDetail): HupuThreadDetail {
        if (old == null) return fresh
        if (old.replyPage <= 1) return fresh
        // old 已翻页：保留已加载回复（新数据里已含第 1 页最新回复），翻页游标取新
        val merged = (fresh.replies + old.replies).distinctBy { it.pid }
        return fresh.copy(replies = merged, replyPage = fresh.replyPage)
    }
}