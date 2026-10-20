package com.kudo.android.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kudo.android.hupu.HUPU_BOARDS
import com.kudo.android.hupu.HupuMatchApi
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuRepository

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
    suspend fun topics(filter: String = "latest", categoryId: Int? = null, next: String? = null): TopicPage {
        if (categoryId != null) {
            val url = HupuIds.boardUrl(categoryId) ?: return TopicPage(emptyList(), null)
            val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
            val p = hupu.topicPage(url, page) ?: return TopicPage(emptyList(), null)
            val cursor = if (p.totalPages > page) "p${page + 1}" else null
            return TopicPage(p.threads.map { it.toTopic() }, cursor)
        }
        // board:<url> 板块频道 → 话题页分页；sport:<id> 赛事频道 → 官方 newsList/SSR/专区帖兜底
        if (filter.startsWith("board:")) {
            val url = filter.removePrefix("board:")
            val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
            val p = hupu.topicPage(url, page) ?: return TopicPage(emptyList(), null)
            val cursor = if (p.totalPages > page) "p${page + 1}" else null
            return TopicPage(p.threads.map { it.toTopic() }, cursor)
        }
        if (filter.startsWith("sport:")) {
            val cfg = HUPU_BOARDS[filter.removePrefix("sport:")] ?: return TopicPage(emptyList(), null)
            if (cfg.soccerSsr) {
                val items = hupu.soccerNews().filter { it.openableAsThread }
                return TopicPage(items.map { it.toTopic() }, null)
            }
            cfg.tagCode?.let { tag ->
                val (pre, anchor) = next?.removePrefix("n")?.split('|')?.let { it[0].toIntOrNull() to it.getOrNull(1).orEmpty() }
                    ?: (0 to "")
                val p = hupu.boardNews(tag, pre ?: 0, anchor) ?: return TopicPage(emptyList(), null)
                val items = p.items.filter { it.openableAsThread }
                val cursor = if (p.nextPreCount > (pre ?: 0)) "n${p.nextPreCount}|${p.anchorNid}" else null
                return TopicPage(items.map { it.toTopic() }, cursor)
            }
            val page = next?.removePrefix("p")?.toIntOrNull() ?: 1
            val p = hupu.topicNews(cfg.newsTopic, page) ?: return TopicPage(emptyList(), null)
            return TopicPage(p.items.filter { it.openableAsThread }.map { it.toTopic() },
                if (p.items.isNotEmpty()) "p${page + 1}" else null)
        }
        val threads = when (filter) {
            FeedFilter.HOT.value -> hupu.board("/all-gambia")?.threads
            else -> hupu.mobileHome()
        }.orEmpty()
        return TopicPage(threads.map { it.toTopic() }, null)
    }

    /** 赛事频道的近期赛程（首页频道页顶部赛事条）。 */
    suspend fun sportMatches(key: String): List<HupuMatchDay> {
        val cfg = HUPU_BOARDS[key] ?: return emptyList()
        return hupu.matchSchedule(cfg.scheduleId)
    }

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
