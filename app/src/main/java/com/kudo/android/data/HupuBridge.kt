package com.kudo.android.data

import com.kudo.android.hupu.HupuAuthor
import com.kudo.android.hupu.HupuCategory
import com.kudo.android.hupu.HupuReply
import com.kudo.android.hupu.HupuSubReply
import com.kudo.android.hupu.HupuThread
import com.kudo.android.hupu.HupuThreadDetail
import com.kudo.android.hupu.HupuTopic
import com.kudo.android.hupu.HupuTopicInfo
import java.time.Instant

/**
 * 虎扑 → kudo 模型桥接。
 * 虎扑 tid/pid 是超出 Int 的长串/字符串，而 UI 层模型用 Int id；
 * 这里维护进程内双向映射表，仅在数据进入 UI 时分配。
 */
object HupuIds {
    private val lock = Any()
    private var nextId = 1
    private val stringById = mutableMapOf<Int, String>()
    private val idByString = mutableMapOf<String, Int>()
    /** 父楼层 post id → 已加载子回复 pid 序列（楼中楼翻页 maxpid 游标用） */
    private val subPids = mutableMapOf<Int, MutableList<String>>()
    /** 占位 post id（stream 合成项）→ 加载后回写的真实 pid */
    private val pidById = mutableMapOf<Int, String>()

    fun id(key: String): Int = synchronized(lock) {
        idByString.getOrPut(key) { (++nextId).also { stringById[it] = key } }
    }

    fun key(id: Int): String? = synchronized(lock) { stringById[id] }

    /** 板块 id 与帖子 id 共表不安全（碰撞即跳错页），用前缀隔离命名空间。 */
    fun boardId(url: String): Int = id("board:$url")
    fun boardUrl(id: Int): String? = key(id)?.removePrefix("board:")?.takeIf { it != key(id) }
    fun topicId(tid: String): Int = id("topic:$tid")
    fun tid(id: Int): String? = key(id)?.let { if (it.startsWith("topic:")) it.removePrefix("topic:") else null }

    /** 楼层 post id：编码 tid+pid+floor，翻页/楼中楼反查用。 */
    fun postId(tid: String, pid: String, floor: Int): Int = id("post:$tid:$pid:$floor")

    data class PostRef(val tid: String, val pid: String, val floor: Int, val isOp: Boolean)

    fun postRef(id: Int): PostRef? = key(id)?.let { k ->
        if (!k.startsWith("post:")) return null
        val p = k.split(":")
        when {
            p.size == 4 -> PostRef(p[1], p[2].ifEmpty { pidById[id].orEmpty() }, p[3].toIntOrNull() ?: 0, false)
            p.size == 3 && p[1] == "op" -> PostRef(p[2], "", 1, true)
            else -> null
        }
    }

    /** 占位 id 加载出真实 pid 后回写，楼中楼父楼层取 pid 用。 */
    fun bindPid(id: Int, pid: String) = synchronized(lock) { if (pid.isNotEmpty()) pidById[id] = pid }

    fun recordSubPids(parentId: Int, pids: List<String>) = synchronized(lock) {
        subPids.getOrPut(parentId) { mutableListOf() }.addAll(pids)
    }

    /** after=已加载子回复数；其上一页最后一条 pid 即 maxpid。 */
    fun subPidCursor(parentId: Int, after: Int): String? = synchronized(lock) {
        if (after <= 0) null else subPids[parentId]?.getOrNull(after - 1)
    }
}

private fun isoTime(millis: Long, text: String): String =
    if (millis > 0) Instant.ofEpochMilli(millis).toString() else text

private fun HupuAuthor?.toMember(): Member =
    Member(
        id = this?.euid?.takeIf { it.isNotBlank() }?.let(HupuIds::id) ?: 0,
        username = this?.euid?.takeIf { it.isNotBlank() } ?: this?.name.orEmpty(),
        name = this?.name.orEmpty(),
        avatar = "",
    )

private fun HupuTopic?.categoryIdOf(fallbackFid: String): Int = when {
    this?.url?.isNotBlank() == true -> HupuIds.boardId(url)
    fallbackFid.isNotBlank() -> HupuIds.boardId("fid:$fallbackFid")
    else -> 0
}

fun HupuThread.toTopic(): Topic = Topic(
    id = HupuIds.topicId(tid),
    title = title,
    excerpt = desc.orEmpty(),
    author = author?.takeIf { it.name.isNotBlank() || it.euid.isNotBlank() }?.toMember(),
    categoryId = topic.categoryIdOf(fid),
    tags = topic?.name?.takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList(),
    replies = replies,
    views = read,
    likes = lights,
    image = cover.orEmpty(),
    bumpedAt = isoTime(createdAt, createdAtText),
)

fun HupuReply.toPost(tid: String): Post = Post(
    id = HupuIds.postId(tid, pid, floor),
    number = floor,
    author = author.toMember(),
    cooked = contentHtml,
    createdAt = createdAtText,
    likes = lights,
    liked = false,
    bookmarkId = null,
    replyTo = null,
    canLike = false,
    replyCount = totalReplies,
)

fun HupuSubReply.toPost(tid: String, parentFloor: Int): Post = Post(
    id = HupuIds.id("post:$tid:sub:$pid"),
    number = parentFloor,
    author = author.toMember(),
    cooked = contentHtml,
    createdAt = createdAtText,
    likes = lights,
    liked = false,
    bookmarkId = null,
    replyTo = parentFloor,
    canLike = false,
)

fun HupuThreadDetail.toTopicDetail(): TopicDetail {
    val op = Post(
        id = HupuIds.id("post:op:${thread.tid}"),
        number = 1,
        author = (thread.author ?: replies.firstOrNull { it.isStarter }?.author).toMember(),
        cooked = contentHtml,
        createdAt = isoTime(thread.createdAt, thread.createdAtText),
        likes = thread.lights,
        liked = false,
        bookmarkId = null,
        replyTo = null,
        canLike = false,
    )
    val posts = listOf(op) + replies.map { it.toPost(thread.tid) }
    // stream 按 Discourse 语义承载全量楼层 id：未加载楼层用空 pid 占位，
    // TopicViewModel 翻页时由 posts() 解码 floor → threadReplies 按页补齐。
    val pidByFloor = replies.associate { it.floor to it.pid }
    val stream = buildList {
        add(op.id)
        for (f in 1..thread.replyCount.coerceAtLeast(replies.size)) {
            add(HupuIds.postId(thread.tid, pidByFloor[f].orEmpty(), f))
        }
    }
    return TopicDetail(
        id = HupuIds.topicId(thread.tid),
        title = thread.title,
        categoryId = thread.topic.categoryIdOf(thread.fid),
        posts = posts,
        stream = stream,
        closed = isLocked,
    )
}

fun HupuCategory.toCategories(): List<Category> = topics.map { topic ->
    Category(
        id = HupuIds.boardId(topic.url),
        name = topic.name,
        slug = topic.url,
        color = "",
        description = topic.desc.ifBlank { name },
        count = 0,
        icon = topic.logo.orEmpty(),
    )
}
