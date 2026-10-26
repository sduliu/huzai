package com.kudo.android.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kudo.android.hupu.HUPU_BOARDS
import com.kudo.android.hupu.HupuMatchApi
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuBoardPage
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HupuThread

/**
 * 社区数据仓库 —— 虎Do 版。
 * 读路径（话题流/板块目录/帖子详情/搜索）全部改走虎扑（HupuRepository），
 * 写路径保留 Discourse 会话调用，未登录时自然失败为「请先登录」。
 */
class CommunityRepository(val client: DiscourseClient, private val hupu: HupuRepository = HupuRepository()) {
    private val categoryLock = Any()
    private val _categoryCatalog = MutableStateFlow<List<Category>>(emptyList())
    val categoryCatalog = _categoryCatalog.asStateFlow()
    private var categoryAccount: Int? = null
    private var categoryGeneration = 0

    fun useCategoryAccount(accountId: Int?) = synchronized(categoryLock) {
        if (categoryAccount != accountId) {
            categoryAccount = accountId
            categoryGeneration++
            _categoryCatalog.value = emptyList()
        }
    }

    /**
     * 话题流。filter→虎扑数据源：
     * latest=官方移动站「综合」；hot=步行街热帖；top=NBA 区；其余（个人向的）→ 综合兜底。
     * categoryId 非空时走该板块的话题页，next 游标为 "p{N}"。
     */
    suspend fun topics(filter: String = "latest", categoryId: Int? = null, next: String? = null, refresh: Boolean = false): TopicPage {
        if (categoryId != null) {
            val url = HupuIds.boardUrl(categoryId) ?: return TopicPage(emptyList(), null)
            val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
            // 两类板块页：话题页（topicPage）/板块页（board）——解析失败自动回落
            hupu.topicPage(url, page, refresh)?.let { p ->
                val cursor = if (p.totalPages > page) "p${page + 1}" else null
                return TopicPage(p.threads.map { it.toTopic() }, cursor)
            }
            if (page > 1) return TopicPage(emptyList(), null)
            val b = hupu.board(url, refresh) ?: return TopicPage(emptyList(), null)
            return TopicPage(b.threads.map { it.toTopic() }, null)
        }
        // board:<url> 板块频道 → 话题页分页；sport:<id> 赛事频道 → 官方 newsList/SSR/专区帖兜底
        if (filter.startsWith("board:")) {
            val url = filter.removePrefix("board:")
            val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
            // 两类板块页：专区话题页（topicPage，有分页）/板块页（board，无分页）——
            // parseTopicPage 对板块页结构返回 null，自动回落
            hupu.topicPage(url, page, refresh)?.let { p ->
                val cursor = if (p.totalPages > page) "p${page + 1}" else null
                return TopicPage(p.threads.map { it.toTopic() }, cursor)
            }
            val b = hupu.board(url, refresh) ?: return TopicPage(emptyList(), null)
            return TopicPage(b.threads.map { it.toTopic() }, null)
        }
        if (filter.startsWith("sport:")) {
            val cfg = HUPU_BOARDS[filter.removePrefix("sport:")] ?: return TopicPage(emptyList(), null)
            // 首页赛事频道 = 板块帖子流（湿乎乎/足球话题区），与比赛 Tab 的
            // 资讯流分工；板块页无分页——p>1 时 topicPage 失败即终止，不再回落
            // board() 重发首页内容。
            if (cfg.boardUrl.isNotEmpty()) {
                val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
                hupu.topicPage(cfg.boardUrl, page, refresh)?.let { p ->
                    val cursor = if (p.totalPages > page) "p${page + 1}" else null
                    return TopicPage(p.threads.map { it.toTopic() }, cursor)
                }
                if (page > 1) return TopicPage(emptyList(), null)
                hupu.board(cfg.boardUrl, refresh)?.let { b ->
                    return TopicPage(b.threads.map { it.toTopic() }, null)
                }
                // 板块拉空（网络失败等）→ 回退下方资讯流
            }
            if (cfg.soccerSsr) {
                val items = hupu.soccerNews(refresh).filter { it.openableAsThread }
                return TopicPage(items.map { it.toTopic() }, null)
            }
            cfg.tagCode?.let { tag ->
                val (pre, anchor) = next?.removePrefix("n")?.split('|')?.let { it[0].toIntOrNull() to it.getOrNull(1).orEmpty() }
                    ?: (0 to "")
                val p = hupu.boardNews(tag, pre ?: 0, anchor, refresh) ?: return TopicPage(emptyList(), null)
                val items = p.items.filter { it.openableAsThread }
                val cursor = if (p.nextPreCount > (pre ?: 0)) "n${p.nextPreCount}|${p.anchorNid}" else null
                return TopicPage(items.map { it.toTopic() }, cursor)
            }
            val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
            val p = hupu.topicNews(cfg.newsTopic, page, refresh) ?: return TopicPage(emptyList(), null)
            return TopicPage(p.items.filter { it.openableAsThread }.map { it.toTopic() },
                if (p.items.isNotEmpty()) "p${page + 1}" else null)
        }
        val threads = when (filter) {
            // 热榜是榜单页（HotChannelPage 自取数），不走帖子流
            FeedFilter.HOT.value -> emptyList()
            // 批D-fix：首页空结果自愈——缓存命中脏 SSR（含 __NEXT_DATA__ 但 res 为空/变体）时
            // 强网重拉一次再宣判空列，否则首拉坏缓存会把首页饿死到 TTL 到期
            else -> {
                val first = hupu.mobileHome(refresh)
                (if (first.isEmpty() && !refresh) hupu.mobileHome(refresh = true) else first)
                    .let { filterLatestByFollowed(it) }
            }
        }
        return TopicPage(threads.map { it.toTopic() }, null)
    }

    /**
     * 批Nfix-1：信息流卡按 tid 惰性补齐——作者（`threads?tid=`）+日期（createdAtFormat→bumpedAt
     * 仅当为空）+转发数（`bbs-thread`）。缺什么补什么；4 条一批 + 批间 120ms，同批并发。
     * 作者落盘缓存（值格式 puid|euid|name|avatar，旧两段值视为不完整再补）；
     * 转发数易变不落盘。返回与入参等长的新列表。
     */
    suspend fun enrichTopics(topics: List<Topic>): List<Topic> = coroutineScope {
        val cache = HupuPrefs.loadThreadAuthors()
        val fresh = mutableMapOf<String, String>()
        val out = topics.toMutableList()
        val pending = topics.mapIndexedNotNull { i, t ->
            HupuIds.tid(t.id)?.let { i to (t to it) }
        }.filter { (_, pair) -> pair.first.author == null || pair.first.shareNum < 0 || pair.first.bumpedAt.isBlank() }
        pending.chunked(4).forEach { batch ->
            batch.map { (i, pair) ->
                val (t, tid) = pair
                async(Dispatchers.IO) {
                    var author = t.author
                    var bumped = t.bumpedAt
                    var share = t.shareNum
                    if (author == null || bumped.isBlank()) {
                        val cached = cache[tid]?.split('|')
                        if (author == null && cached != null && cached.size >= 4 &&
                            (cached[0].isNotEmpty() || cached[1].isNotEmpty())) {
                            author = Member(
                                id = cached[1].takeIf { it.isNotBlank() }?.let(HupuIds::id) ?: 0,
                                username = cached[1].ifBlank { cached[2] },
                                name = cached[2],
                                avatar = cached[3],
                            )
                        } else {
                            val (a, date) = hupu.threadCardHead(tid)
                            if (a != null && (a.puid.isNotEmpty() || a.euid.isNotEmpty() || a.name.isNotBlank())) {
                                fresh[tid] = "${a.puid}|${a.euid}|${a.name}|${a.url}"
                                if (author == null) author = Member(
                                    id = a.euid.takeIf { it.isNotBlank() }?.let(HupuIds::id) ?: 0,
                                    username = a.euid.ifBlank { a.name },
                                    name = a.name,
                                    avatar = a.url,
                                )
                            }
                            if (bumped.isBlank() && date.isNotBlank()) bumped = date
                        }
                    }
                    if (share < 0) share = hupu.threadShareNum(tid) ?: -1
                    i to t.copy(author = author, bumpedAt = bumped, shareNum = share)
                }
            }.forEach { deferred ->
                val (i, nt) = deferred.await()
                out[i] = nt
            }
            if (batch.size == 4) delay(120)
        }
        HupuPrefs.putThreadAuthors(fresh)
        out
    }

    /** 板块页原始数据（热榜频道/版块页头部用）。 */
    suspend fun boardPage(url: String): HupuBoardPage? = hupu.board(url)

    /** 批F-4：官方热榜 tag 榜（m.hupu.com/hot 匿名首屏 30 条）。 */
    suspend fun hotTags() = hupu.hotTags()

    /** 批F-4：tag 聚合页首屏 / 帖分页（m.hupu.com/tag/{id} + tagThreads API）。 */
    suspend fun tagPage(tagId: String) = hupu.tagPage(tagId)
    suspend fun tagThreads(tagId: String, page: Int) = hupu.tagThreads(tagId, page)

    /** 赛事频道的近期赛程（首页频道页顶部赛事条）。 */
    suspend fun sportMatches(key: String): List<HupuMatchDay> {
        val cfg = HUPU_BOARDS[key] ?: return emptyList()
        return hupu.matchSchedule(cfg.scheduleId)
    }

    /** 频道的数据 tab 轨道（matchallapi getDataTabList；快捷行按它决定数据入口）。 */
    suspend fun leagueDataTabs(key: String): List<com.kudo.android.hupu.HupuDataTab> =
        hupu.leagueDataTabs(key)

    /** 频道管理候选池：全部赛事频道 + 板块树（key→展示名）。
     *  热门赛事聚合频道排除——它是「比赛」Tab 的本体。 */
    suspend fun channelPool(): List<Pair<String, String>> {
        val sports = HUPU_BOARDS.filterKeys { it != HupuMatchApi.COMMON_HOT_SPORTS_ID }
            .map { (k, c) -> "sport:$k" to c.name }
        val boards = hupu.categories().flatMap { it.topics }
            .map { "board:${it.url}" to it.name }
        return (sports + boards).distinctBy { it.first }
    }

    suspend fun tagTopics(slug: String, id: Int? = null, filter: String = "latest", next: String? = null): TopicPage =
        topics(filter, categoryId = id, next = next)

    suspend fun newTopicCount(user: Member): Int = 0

    suspend fun categories(): List<Category> {
        val token = synchronized(categoryLock) { categoryGeneration }
        // 虎扑大类树里同一板块会在多个分组重复（如「步行街主干道」），按名去重
        val categories = hupu.categories().flatMap { it.toCategories() }.distinctBy { it.name }
        synchronized(categoryLock) {
            if (token == categoryGeneration && categories.isNotEmpty()) _categoryCatalog.value = categories
        }
        return categories
    }

    suspend fun tagIcons(): Map<String, TagIconStyle> = emptyMap()

    /** 匿名只读：无当前用户。 */
    suspend fun currentUser(): Member? = null

    /** 作者主页：euid 在 Member.username 里携带。 */
    suspend fun user(username: String): Member {
        val profile = hupu.userProfile(username)
            ?: throw ApiFailure(FailureKind.NOT_FOUND, "用户不存在或暂无权限")
        return Member(
            id = HupuIds.id("user:${profile.euid}"),
            username = profile.euid,
            name = profile.name,
            avatar = profile.avatar,
            bio = profile.levelDesc,
        )
    }

    suspend fun userCards(ids: List<Int>): List<Member> = emptyList()

    suspend fun emojiGroups(): List<SiteEmojiGroup> = emptyList()
    suspend fun emojiUrls(): Map<String, String> = emptyMap()

    suspend fun detail(id: Int, number: Int? = null, trackVisit: Boolean = false): TopicDetail {
        val tid = HupuIds.tid(id) ?: throw ApiFailure(FailureKind.NOT_FOUND, "内容不存在或暂无权限")
        val detail = hupu.threadDetail(tid)
            ?: throw ApiFailure(FailureKind.NOT_FOUND, "内容不存在或暂无权限")
        return detail.toTopicDetail()
    }

    suspend fun recordReading(batch: TopicReadingBatch) = Unit

    /**
     * 回复分页。ids 为 stream 中的 post id（编码 tid+floor，占位项 pid 为空）。
     * 按 floor 定位页码调用虎扑回复分页，返回的 Post 沿用请求方占位 id 以匹配 stream，
     * 真实 pid 回写进 HupuIds 侧表供楼中楼展开用。
     */
    suspend fun posts(topicId: Int, ids: List<Int>): List<Post> {
        val tid = HupuIds.tid(topicId) ?: return emptyList()
        val wanted = ids.mapNotNull { id ->
            HupuIds.postRef(id)?.takeUnless { it.isOp }?.takeIf { it.tid == tid }
                ?.let { id to it.floor }
        }
        if (wanted.isEmpty()) return emptyList()
        val byFloor = wanted.groupBy({ it.second }, { it.first })
        val found = mutableMapOf<Int, Post>()
        for (p in byFloor.keys.map { (it - 1) / 20 + 1 }.toSet()) {
            val d = hupu.threadReplies(tid, p) ?: continue
            for (r in d.replies) {
                for (id in byFloor[r.floor].orEmpty()) {
                    found[id] = r.toPost(tid).copy(id = id)
                    HupuIds.bindPid(id, r.pid)
                }
            }
        }
        return ids.mapNotNull(found::get)
    }

    /** 楼中楼：postId → (tid,pid,floor) → 虎扑 floorReplies；after=已加载数，取末位 pid 作 maxpid 游标。 */
    suspend fun replies(postId: Int, after: Int): List<Post> {
        val ref = HupuIds.postRef(postId)?.takeIf { it.pid.isNotEmpty() } ?: return emptyList()
        val parent = com.kudo.android.hupu.HupuReply(pid = ref.pid, floor = ref.floor)
        val r = hupu.floorReplies(ref.tid, parent, HupuIds.subPidCursor(postId, after)) ?: return emptyList()
        HupuIds.recordSubPids(postId, r.subReplies.map { it.pid })
        return r.subReplies.map { it.toPost(ref.tid, ref.floor) }
    }

    suspend fun post(id: Int): Post = throw ApiFailure(FailureKind.NOT_FOUND, "内容不存在或暂无权限")

    suspend fun search(query: String, page: Int = 1): Pair<List<Topic>, Boolean> {
        val result = hupu.search(query, page = page) ?: return emptyList<Topic>() to false
        val topics = result.items.map { item ->
            Topic(
                id = HupuIds.topicId(item.tid),
                title = item.title,
                excerpt = item.desc,
                author = null,
                categoryId = if (item.fid.isNotBlank()) HupuIds.boardId("fid:${item.fid}") else 0,
                tags = item.forumName.takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList(),
                replies = item.replies,
                views = 0,
                likes = item.lights,
                image = item.picture.orEmpty(),
                bumpedAt = if (item.addtime > 0)
                    java.time.Instant.ofEpochSecond(item.addtime).toString() else item.addTimeDisplay,
            )
        }
        return topics to (result.totalPages > page)
    }

    suspend fun like(post: Post): Post = throw ApiFailure(FailureKind.SIGN_IN, "请先登录虎扑")

    suspend fun react(post: Post, reaction: String): Post = throw ApiFailure(FailureKind.SIGN_IN, "请先登录虎扑")

    suspend fun follow(member: Member): Boolean = throw ApiFailure(FailureKind.SIGN_IN, "请先登录虎扑")

    suspend fun bookmark(post: Post): Int? = throw ApiFailure(FailureKind.SIGN_IN, "请先登录虎扑")

    suspend fun bookmarks(username: String, page: Int): Pair<List<SavedTopic>, Boolean> = emptyList<SavedTopic>() to false

    suspend fun notifications(offset: Int): List<Notice> = emptyList()

    suspend fun readNotification(id: Int? = null) = Unit

    suspend fun publish(raw: String, title: String?, categoryId: Int?, topicId: Int?, replyTo: Int?, tags: List<String> = emptyList()): PublishResult =
        throw ApiFailure(FailureKind.SIGN_IN, "请先登录虎扑")

    suspend fun upload(bytes: ByteArray, mimeType: String, filename: String): String =
        throw ApiFailure(FailureKind.SIGN_IN, "请先登录虎扑")
}

/**
 * 「综合」流的编辑类 label → 内容家族。
 * 命中某家族但用户未关注该家族（无对应 board:/sport: 频道或收藏板块）→ 过滤；
 * 不命中任何家族的 label 一律放行（板块帖/未识别杂项宁可多给也别饿死推荐流）。
 */
private val LATEST_LABEL_FAMILIES: Map<String, List<String>> = mapOf(
    "basketball" to listOf("篮球", "NBA", "CBA", "WNBA", "CUBA", "男篮"),
    "soccer" to listOf("足球", "英超", "西甲", "意甲", "德甲", "法甲", "中超", "欧冠", "世界杯", "欧联", "FIFA"),
    "lol" to listOf("英雄联盟", "LOL", "LPL", "LCK", "MSI"),
    "kog" to listOf("王者", "KPL"),
    "val" to listOf("无畏契约", "VALORANT", "瓦罗兰特"),
    "cs" to listOf("CS2", "CSGO", "反恐精英"),
    "pubg" to listOf("绝地求生", "PUBG", "和平精英", "吃鸡"),
    "street" to listOf("步行街"),
    "othersport" to listOf("综合体育", "F1", "网球", "乒乓", "羽毛", "台球", "田径", "游泳", "拳击", "UFC", "奥运", "赛车", "高尔夫", "橄榄球", "棒球", "排球"),
)

/** sport 频道 id → 内容家族（决定它能为「综合」解锁哪类编辑资讯） */
private val LATEST_FAMILY_OF_SPORT: Map<String, String> = mapOf(
    "nba" to "basketball", "cba" to "basketball", "wnba" to "basketball", "cuba" to "basketball",
    "soccer" to "soccer", "epl" to "soccer", "csl" to "soccer", "worldcup" to "soccer",
    "laliga" to "soccer", "seriea" to "soccer", "bundesliga" to "soccer", "ligue1" to "soccer",
    "lol" to "lol", "lpl" to "lol", "lck" to "lol",
    "kog" to "kog", "val" to "val",
    "cs2" to "cs", "pubg" to "pubg", "pubgmobile" to "pubg",
    "tennis" to "othersport", "tabletennis" to "othersport",
    "badminton" to "othersport", "olympics" to "othersport",
)

/** 板块 url → 内容家族：由 HUPU_BOARDS.boardUrl 反查 + 社区大板块补充 */
private val LATEST_FAMILY_OF_BOARD: Map<String, String> by lazy {
    HUPU_BOARDS.entries.mapNotNull { (id, cfg) ->
        cfg.boardUrl.takeIf { it.isNotBlank() }
            ?.let { url -> LATEST_FAMILY_OF_SPORT[id]?.let { fam -> url to fam } }
    }.plus("/all-gambia" to "street").toMap()
}

/**
 * 已关注内容在「综合」流的放行关键词：
 * 家族关键词（sport: 频道与可映射板块的家族）+ 各关注板块自身名称。
 */
private fun followedLatestKeywords(): Set<String> {
    val out = mutableSetOf<String>()
    fun allow(url: String, name: String) {
        LATEST_FAMILY_OF_BOARD[url]?.let { out += LATEST_LABEL_FAMILIES[it].orEmpty() }
        if (name.isNotBlank()) out += name.trim()
    }
    for ((k, l) in HupuPrefs.effectiveHomeChannels()) when {
        k.startsWith("sport:") -> LATEST_FAMILY_OF_SPORT[k.removePrefix("sport:")]
            ?.let { out += LATEST_LABEL_FAMILIES[it].orEmpty() }
        k.startsWith("board:") -> allow(k.removePrefix("board:"), l)
    }
    HupuPrefs.loadFavoriteTopics().forEach { allow(it.url, it.name) }
    return out
}

/**
 * 「综合」按关注过滤：只放行已关注家族（或未识别 label）的条目。
 * 过滤后不足 5 条时放弃过滤返回原列表（fail-open，防误配饿死首屏）。
 */
private fun filterLatestByFollowed(threads: List<HupuThread>): List<HupuThread> {
    val allowed = followedLatestKeywords()
    if (allowed.isEmpty()) return threads
    val known = LATEST_LABEL_FAMILIES.values.flatten()
    val filtered = threads.filter { th ->
        val label = th.topic?.name.orEmpty()
        allowed.any { label.contains(it, ignoreCase = true) } ||
            known.none { label.contains(it, ignoreCase = true) }
    }
    return if (filtered.size >= 5) filtered else threads
}
