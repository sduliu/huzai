package com.kudo.android.data

import okhttp3.FormBody
import org.json.JSONObject
import java.time.Instant

data class PollOption(val id: String, val html: String, val votes: Int?)
data class PostPoll(
    val name: String, val type: String, val status: String, val results: String,
    val options: List<PollOption>, val voters: Int, val public: Boolean = false,
    val min: Int = 1, val max: Int = 1, val close: Instant? = null,
    val title: String = "", val groups: String = "",
) {
    val supported get() = type in setOf("regular", "multiple", "number") && groups.isBlank()
    fun closed(now: Instant = Instant.now()) = status != "open" || close?.isAfter(now) == false
    fun resultsVisible(voted: Boolean, owner: Boolean = false): Boolean = options.all { it.votes != null } && when (results) {
        "always" -> true
        "on_vote" -> voted || owner || closed()
        "on_close" -> closed()
        else -> false
    }
    fun validSelection(ids: List<String>): Boolean = supported && !closed() && ids.distinct().size == ids.size &&
        ids.all { id -> options.any { it.id == id } } && ids.size in min..max
}

internal fun parsePoll(json: JSONObject): PostPoll {
    val type = json.stringField("type")
    val options = json.optJSONArray("options").objects().mapNotNull { option ->
        option.stringField("id").takeIf { it.isNotEmpty() }?.let {
            PollOption(it, option.text("html"), (option.opt("votes") as? Number)?.toInt()?.coerceAtLeast(0))
        }
    }.distinctBy(PollOption::id)
    val min = if (type == "multiple") json.optInt("min", 1).coerceIn(1, maxOf(1, options.size)) else 1
    val max = if (type == "multiple") json.optInt("max", options.size).coerceIn(min, maxOf(min, options.size)) else 1
    return PostPoll(json.stringField("name"), type, json.stringField("status"), json.stringField("results"),
        options, json.optInt("voters").coerceAtLeast(0), json.optBoolean("public"), min, max,
        runCatching { Instant.parse(json.stringField("close")) }.getOrNull(), json.stringField("title"), json.stringField("groups"))
}

suspend fun CommunityRepository.vote(post: Post, name: String, options: List<String>): Post {
    val poll = post.polls.firstOrNull { it.name == name } ?: error("投票暂不可用，请刷新")
    require(poll.supported && !poll.closed()) { "此投票不可修改" }
    require(options.isEmpty() || poll.validSelection(options)) { "请选择 ${poll.min} 至 ${poll.max} 项" }
    val body = FormBody.Builder().add("post_id", post.id.toString()).add("poll_name", name).apply {
        options.forEach { add("options[]", it) }
    }.build()
    val response = client.write(if (options.isEmpty()) "DELETE" else "PUT", "/polls/vote", body = body)
    val updated = response.optJSONObject("poll")?.let(::parsePoll)
        ?.takeIf { it.name == name && it.options.isNotEmpty() }
        ?: throw ApiFailure(FailureKind.SERVER, "投票结果尚未确认，请刷新后再操作")
    return post.copy(polls = post.polls.map { if (it.name == name) updated else it },
        pollVotes = if (options.isEmpty()) post.pollVotes - name else post.pollVotes + (name to options))
}
