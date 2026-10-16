package com.kudo.android.data

import com.kudo.android.hupu.HupuAuthor
import com.kudo.android.hupu.HupuCategory
import com.kudo.android.hupu.HupuReply
import com.kudo.android.hupu.HupuThread
import com.kudo.android.hupu.HupuThreadDetail
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

    fun id(key: String): Int = synchronized(lock) {
        idByString.getOrPut(key) { (++nextId).also { stringById[it] = key } }
    }

    fun key(id: Int): String? = synchronized(lock) { stringById[id] }

    /** 板块 id 与帖子 id 共表不安全（碰撞即跳错页），用前缀隔离命名空间。 */
    fun boardId(url: String): Int = id("board:$url")
    fun boardUrl(id: Int): String? = key(id)?.removePrefix("board:")?.takeIf { it != key(id) }
    fun topicId(tid: String): Int = id("topic:$tid")
    fun tid(id: Int): String? = key(id)?.let { if (it.startsWith("topic:")) it.removePrefix("topic:") else null }
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

private fun HupuTopicInfo?.categoryIdOf(fallbackFid: String): Int = when {
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

fun HupuReply.toPost(): Post = Post(
    id = HupuIds.id("post:$pid"),
    number = floor,
    author = author.toMember(),
    cooked = contentHtml,
    createdAt = createdAtText,
    likes = lights,
    liked = false,
    bookmarkId = null,
    replyTo = null,
    canLike = false,
)

fun HupuThreadDetail.toTopicDetail(): TopicDetail {
    val op = Post(
        id = HupuIds.id("post:op-${thread.tid}"),
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
    val posts = listOf(op) + replies.map(HupuReply::toPost)
    return TopicDetail(
        id = HupuIds.topicId(thread.tid),
        title = thread.title,
        categoryId = thread.topic.categoryIdOf(thread.fid),
        posts = posts,
        stream = posts.map { it.id },
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
