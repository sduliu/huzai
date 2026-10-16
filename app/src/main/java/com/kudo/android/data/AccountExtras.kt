package com.kudo.android.data

import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

data class AccountRecord(val id: Int, val title: String, val body: String, val time: String = "",
    val topicId: Int? = null, val url: String = "", val payload: String = "{}")

suspend fun CommunityRepository.accountRecords(destination: AccountDestination, username: String): List<AccountRecord> {
    fun path(vararg segments: String) = client.baseUrl.newBuilder().apply { segments.forEach(::addPathSegment) }.build().toString()
    return when (destination) {
        AccountDestination.PENDING -> {
            val json = client.get(path("posts", username, "pending.json"))
            val array = json.optJSONArray("pending_posts") ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回待审核内容")
            array.objects().map { item -> AccountRecord(item.getInt("id"), item.text("title").ifBlank { "待审核回复" },
                item.text("raw").ifBlank { item.text("raw_text") }, item.text("created_at"), item.positiveInt("topic_id"), payload = item.toString()) }
        }
        AccountDestination.INVITES -> parseAccountInvites(client.getCollection(path("u", username, "invited", "pending")))
        AccountDestination.CHAT -> {
            val json = client.get("/chat/api/me/channels")
            if (!json.has("public_channels") && !json.has("direct_message_channels")) throw ApiFailure(FailureKind.SERVER, "服务器未返回聊天频道")
            (json.optJSONArray("direct_message_channels").objects() + json.optJSONArray("public_channels").objects()).map { channel ->
                val title = channel.text("title").ifBlank { channel.text("unicode_title") }.ifBlank {
                    channel.optJSONObject("chatable")?.optJSONArray("users").objects().joinToString("、") { it.text("username") }
                }.ifBlank { "频道 #${channel.optInt("id")}" }
                AccountRecord(channel.getInt("id"), plainText(title), plainText(channel.text("description")), payload = channel.toString())
            }.distinctBy { it.id }
        }
        else -> error("不支持的个人功能")
    }
}

internal fun parseAccountInvites(json: JSONObject): List<AccountRecord> {
    val array = listOf("items", "invites", "pending_invites", "invited", "pending").firstNotNullOfOrNull { json.optJSONArray(it) }
        ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回邀请列表")
    return array.objects().map { item ->
        val invite = item.optJSONObject("invite") ?: item
        val key = invite.text("invite_key")
        val url = item.text("invite_link").ifBlank { invite.text("invite_link") }.ifBlank { item.text("invite_url") }
            .ifBlank { if (key.isNotBlank()) "https://linux.do/invites/${java.net.URLEncoder.encode(key, "UTF-8")}" else "" }
        AccountRecord(invite.optInt("id"), invite.text("description").ifBlank { "邀请链接" },
            "已使用 ${invite.optInt("redemption_count")} / ${invite.optInt("max_redemptions_allowed", 1)} 次",
            invite.text("expires_at"), url = url, payload = invite.toString())
    }
}
suspend fun CommunityRepository.withdrawPending(id: Int) { require(id > 0); client.write("DELETE", "/review/$id") }
suspend fun CommunityRepository.createAccountInvite(description: String, email: String, days: Int): AccountRecord {
    require(days in setOf(1, 7, 30, 90))
    val fields = mutableMapOf("max_redemptions_allowed" to "1", "expires_at" to Instant.now().plus(days.toLong(), ChronoUnit.DAYS).toString())
    description.trim().takeIf(String::isNotEmpty)?.let { fields["description"] = it }
    email.trim().takeIf(String::isNotEmpty)?.let { fields["email"] = it }
    val json = client.write("POST", "/invites", fields)
    val records = parseAccountInvites(JSONObject().put("items", org.json.JSONArray().put(json)))
    return records.single().takeIf { it.url.isNotBlank() } ?: throw ApiFailure(FailureKind.SERVER, "邀请结果未确认，请刷新列表查看")
}
