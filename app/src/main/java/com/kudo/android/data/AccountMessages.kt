package com.kudo.android.data

import org.json.JSONObject
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

internal fun messageRecipients(value: String): List<String> = value.split(Regex("[,，;；\\s]+")).map { it.trim().removePrefix("@") }
    .filter(String::isNotBlank).distinct()

suspend fun CommunityRepository.createPrivateMessage(draft: Draft): PublishResult {
    val recipients = messageRecipients(draft.recipients)
    require(recipients.isNotEmpty() && draft.title.isNotBlank() && draft.raw.isNotBlank()) { "请填写收件人、标题和正文" }
    val result = client.write("POST", "/posts.json", mapOf("archetype" to "private_message", "target_recipients" to recipients.joinToString(","),
        "title" to draft.title, "raw" to draft.raw))
    if (result.text("action") == "enqueued") return PublishResult(null, true)
    val post = result.optJSONObject("post") ?: result
    if (post.positiveInt("id") == null || post.positiveInt("topic_id") == null) throw ApiFailure(FailureKind.SERVER, "发送结果未确认，草稿已保留，请刷新私信列表确认")
    return PublishResult(post.positiveInt("topic_id"), false)
}

data class ChatEntry(val id: Int, val author: Member, val cooked: String, val raw: String, val time: String, val deleted: Boolean)
data class ChatPage(val messages: List<ChatEntry>, val morePast: Boolean)

suspend fun CommunityRepository.chatMessages(channelId: Int, before: Int? = null, target: Int? = null): ChatPage {
    require(channelId > 0)
    val query = mutableMapOf("page_size" to "50")
    before?.let { query["direction"] = "past"; query["target_message_id"] = it.toString() }
    if (before == null) target?.takeIf { it > 0 }?.let { query["target_message_id"] = it.toString() }
    val json = client.get("/chat/api/channels/$channelId/messages", query)
    val array = json.optJSONArray("messages") ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回聊天消息")
    return ChatPage(array.objects().map { message ->
        ChatEntry(message.getInt("id"), DiscourseParser.member(message.optJSONObject("user") ?: JSONObject()),
            message.text("cooked"), message.text("message"), message.text("created_at"), !message.isNull("deleted_at"))
    }, json.optJSONObject("meta")?.optBoolean("can_load_more_past") == true)
}
suspend fun CommunityRepository.readChat(channelId: Int, messageId: Int) {
    require(channelId > 0 && messageId > 0)
    client.write("PUT", "/chat/api/channels/$channelId/read?message_id=$messageId")
}
suspend fun CommunityRepository.sendChat(channelId: Int, raw: String): Int {
    require(channelId > 0 && raw.isNotBlank())
    return client.write("POST", "/chat/$channelId", mapOf("message" to raw, "staged_id" to java.util.UUID.randomUUID().toString()))
        .positiveInt("message_id") ?: throw ApiFailure(FailureKind.SERVER, "消息发送结果未确认，请刷新后查看")
}
suspend fun CommunityRepository.createChat(recipients: String, name: String): Int {
    val users = messageRecipients(recipients)
    require(users.isNotEmpty()) { "请填写聊天对象的用户名" }
    val body = JSONObject().put("target_usernames", JSONArray(users)).put("upsert", users.size == 1 && name.isBlank())
    if (name.isNotBlank()) body.put("name", name.trim())
    val result = client.write("POST", "/chat/api/direct-message-channels", body = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
    return (result.optJSONObject("channel") ?: result).positiveInt("id") ?: throw ApiFailure(FailureKind.SERVER, "聊天创建结果未确认，请刷新频道列表")
}
