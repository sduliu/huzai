package com.kudo.android.data

import org.json.JSONObject

data class PostBoost(val id: Int, val author: Member, val cooked: String)
data class BoostCount(val length: Int, val emojis: Int) {
    val problem: String? get() = when {
        length > 16 -> "Boost 最多 16 字"
        emojis > 5 -> "Boost 最多 5 个表情"
        length == 0 -> "请输入 Boost 内容"
        else -> null
    }
}

/** Website emoji sequences are matched longest first; other characters retain UTF-16 length. */
object BoostRules {
    private val standardNames by lazy {
        checkNotNull(javaClass.getResourceAsStream("/emoji/boost-shortcodes.txt")).bufferedReader(Charsets.UTF_8).use { it.readLines().toSet() }
    }
    private class Node {
        val children = mutableMapOf<Char, Node>()
        var emoji = false
    }
    private val root by lazy {
        val tree = Node()
        checkNotNull(javaClass.getResourceAsStream("/emoji/boost-unicode.txt")).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter(String::isNotEmpty).forEach { sequence ->
                var node = tree
                sequence.forEach { node = node.children.getOrPut(it, ::Node) }
                node.emoji = true
            }
        }
        tree
    }
    private val shortcode = Regex(":([A-Za-z0-9_+\\-]+(?::t[2-6])?):")

    fun count(raw: String, emojiNames: Set<String>): BoostCount {
        val text = raw.trim()
        var index = 0; var length = 0; var emojis = 0
        while (index < text.length) {
            val named = if (text[index] == ':') shortcode.matchAt(text, index) else null
            if (named != null && (named.groupValues[1].lowercase(java.util.Locale.ROOT) in standardNames || named.groupValues[1] in emojiNames)) {
                length++; emojis++; index += named.value.length; continue
            }
            var node = root; var cursor = index; var end = index
            while (cursor < text.length) {
                node = node.children[text[cursor]] ?: break
                cursor++
                if (node.emoji) end = cursor
            }
            if (end > index) {
                if (text.getOrNull(end) == '\uFE0F') end++
                length++; emojis++; index = end
            } else { length++; index++ }
        }
        return BoostCount(length, emojis)
    }
}

internal fun parseBoost(json: JSONObject): PostBoost {
    val id = json.positiveInt("id") ?: throw ApiFailure(FailureKind.SERVER, "Boost 返回内容不完整")
    val user = json.optJSONObject("user") ?: throw ApiFailure(FailureKind.SERVER, "Boost 作者暂不可用")
    val cooked = json.stringField("cooked").takeIf(String::isNotEmpty)
        ?: throw ApiFailure(FailureKind.SERVER, "Boost 内容暂不可用")
    return PostBoost(id, DiscourseParser.member(user), cooked)
}

suspend fun CommunityRepository.boost(post: Post, raw: String, emojiNames: Set<String>): Post {
    require(post.id > 0 && post.canBoost) { "此帖子暂不能 Boost" }
    val text = raw.trim()
    BoostRules.count(text, emojiNames).problem?.let { throw IllegalArgumentException(it) }
    val boost = parseBoost(client.write("POST", "/discourse-boosts/posts/${post.id}/boosts", mapOf("raw" to text)))
    return post.copy(boosts = (post.boosts + boost).distinctBy(PostBoost::id))
}
