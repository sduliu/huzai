package com.kudo.android.data

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class FlagReason(val id: Int, val key: String, val name: String, val description: String, val requireMessage: Boolean) {
    val contactAuthor get() = key == "notify_user"
    val illegal get() = key == "illegal"
}
data class FlagConfiguration(val reasons: List<FlagReason>, val minimum: Int, val maximum: Int = 500) {
    fun available(post: Post) = reasons.filter { it.id in post.availableFlags }
    fun valid(reason: FlagReason?, message: String, confirmed: Boolean): Boolean = reason != null && reason in reasons &&
        (!reason.requireMessage || (message.trim().length in minimum..maximum && (!reason.illegal || confirmed)))
}

internal fun parseFlagConfiguration(site: JSONObject, html: String): FlagConfiguration {
    val document = Jsoup.parse(html)
    val preload = document.selectFirst("[data-preloaded]")?.attr("data-preloaded")
        ?: document.getElementById("data-preloaded")?.data()
        ?: throw ApiFailure(FailureKind.SERVER, "暂时无法读取举报字数配置")
    val data = JSONObject(preload)
    val settings = data.optJSONObject("siteSettings") ?: JSONObject(data.getString("siteSettings"))
    val minimum = settings.positiveInt("min_personal_message_post_length")
        ?: throw ApiFailure(FailureKind.SERVER, "暂时无法读取举报字数配置")
    val reasons = site.optJSONArray("post_action_types").objects()
        .filter { it.optBoolean("is_flag") && it.optBoolean("enabled") && "Post" in it.optJSONArray("applies_to").strings() }
        .sortedBy { it.optInt("position") }.map {
            FlagReason(it.getInt("id"), it.stringField("name_key"), plainText(it.stringField("name")),
                plainText(it.stringField("description")), it.optBoolean("require_message"))
        }.distinctBy { it.id }
    return FlagConfiguration(reasons, minimum)
}

suspend fun CommunityRepository.flagConfiguration(): FlagConfiguration =
    parseFlagConfiguration(client.get("/site.json"), client.siteSettingsHtml())

suspend fun CommunityRepository.flag(post: Post, configuration: FlagConfiguration, reason: FlagReason, message: String, confirmed: Boolean) {
    require(reason.id in post.availableFlags) { "当前账号无法使用此举报原因" }
    require(configuration.valid(reason, message, confirmed)) { "请检查举报原因和说明" }
    val fields = mutableMapOf("id" to post.id.toString(), "post_action_type_id" to reason.id.toString())
    if (reason.requireMessage) fields["message"] = message.trim()
    val result = client.write("POST", "/post_actions", fields)
    if (result.optInt("id") != post.id) throw ApiFailure(FailureKind.SERVER, "提交结果尚未确认，请到网站查看后再操作")
}

enum class RevisionKind { NORMAL, ADDED, REMOVED }
data class RevisionPart(val text: String, val kind: RevisionKind)
data class RevisionChange(val label: String, val parts: List<RevisionPart>)
data class PostRevision(val postId: Int, val current: Int, val previous: Int?, val next: Int?, val author: String,
    val createdAt: String, val reason: String, val changes: List<RevisionChange>, val hidden: Boolean, val diffError: Boolean)

internal fun revisionParts(html: String): List<RevisionPart> = buildList {
    fun visit(node: Node, kind: RevisionKind) {
        if (node is TextNode) add(RevisionPart(node.wholeText, kind))
        else if (node is Element) {
            val nextKind = when {
                node.tagName() == "ins" || node.hasClass("diff-ins") -> RevisionKind.ADDED
                node.tagName() == "del" || node.hasClass("diff-del") -> RevisionKind.REMOVED
                else -> kind
            }
            if (node.tagName() == "br") add(RevisionPart("\n", kind))
            node.childNodes().forEach { visit(it, nextKind) }
            if (node.tagName() in setOf("p", "div", "li", "pre", "h1", "h2", "h3")) add(RevisionPart("\n", kind))
        }
    }
    visit(Jsoup.parseBodyFragment(html).body(), RevisionKind.NORMAL)
}

internal fun parsePostRevision(json: JSONObject): PostRevision {
    val changes = buildList {
        listOf("title_changes" to "标题", "body_changes" to "正文").forEach { (key, label) ->
            val inline = json.optJSONObject(key)?.stringField("inline").orEmpty()
            if (inline.isNotBlank()) add(RevisionChange(label, revisionParts(inline)))
        }
        fun display(value: Any?): String = when (value) {
            is org.json.JSONArray -> value.strings().joinToString("、").ifBlank { "无" }
            is JSONObject -> value.stringField("display_username").ifBlank { value.positiveInt("post_number")?.toString().orEmpty() }.ifBlank { "无" }
            null, JSONObject.NULL -> "无"
            else -> value.toString()
        }
        listOf("tags_changes" to "标签", "user_changes" to "作者", "category_id_changes" to "分类", "wiki_changes" to "Wiki", "post_type_changes" to "帖子类型", "locale_changes" to "语言", "reply_to_post_number_changes" to "回复楼层").forEach { (key, label) ->
            json.optJSONObject(key)?.let { value -> add(RevisionChange(label, listOf(
                RevisionPart(display(value.opt("previous")), RevisionKind.REMOVED), RevisionPart(" → ", RevisionKind.NORMAL),
                RevisionPart(display(value.opt("current")), RevisionKind.ADDED)))) }
        }
    }
    return PostRevision(json.getInt("post_id"), json.getInt("current_revision"), json.positiveInt("previous_revision"),
        json.positiveInt("next_revision"), json.stringField("acting_user_name").ifBlank { json.stringField("display_username").ifBlank { json.stringField("username") } },
        json.stringField("created_at"), json.stringField("edit_reason"), changes,
        json.optBoolean("previous_hidden") || json.optBoolean("current_hidden"), json.optBoolean("diff_error"))
}

suspend fun CommunityRepository.revision(post: Post, number: Int? = null): PostRevision {
    require(post.hasEditHistory) { "当前帖子没有可查看的编辑历史" }
    require(number == null || number > 0)
    val result = parsePostRevision(client.get("/posts/${post.id}/revisions/${number ?: "latest"}.json"))
    if (result.postId != post.id) throw ApiFailure(FailureKind.SERVER, "编辑历史与当前帖子不符")
    return result
}
