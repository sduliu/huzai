package com.kudo.android.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

fun interface NotificationUpdates { fun notifications(user: Member): Flow<NotificationBatch> }

data class NotificationTarget(val accountId: Int, val noticeId: Int? = null, val topicId: Int? = null,
    val postNumber: Int? = null, val chatChannelId: Int? = null, val chatMessageId: Int? = null,
    val token: String = "")

data class NotificationAlert(val key: String, val title: String, val body: String,
    val target: NotificationTarget, val username: String = "")

data class NotificationSnapshot(val count: Int?, val notice: Notice?, val recent: Map<Int, Boolean>)
data class NotificationBatch(val snapshots: List<NotificationSnapshot> = emptyList(),
    val alerts: List<NotificationAlert> = emptyList(), val initial: Boolean = false, val reconnect: Boolean = false)

internal fun notificationCount(json: JSONObject): Int? =
    (json.opt("all_unread_notifications_count") as? Number)?.toInt()?.coerceAtLeast(0)
        ?: (json.opt("unread_notifications") as? Number)?.toInt()?.coerceAtLeast(0)?.let {
            it + ((json.opt("unread_high_priority_notifications") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0)
        }

internal class NotificationCursors(private val user: Member) {
    val values = linkedMapOf("/notification/${user.id}" to user.notificationChannelPosition,
        "/notification-alert/${user.id}" to -1L, "/chat/notification-alert/${user.id}" to -1L)

    fun accept(messages: JSONArray, initial: Boolean, reconnect: Boolean): NotificationBatch {
        val snapshots = mutableListOf<NotificationSnapshot>()
        val alerts = mutableListOf<NotificationAlert>()
        for (message in messages.objects()) {
            val channel = message.text("channel")
            val data = message.optJSONObject("data") ?: continue
            if (channel == "/__status") {
                values.keys.toList().forEach { name -> if (data.has(name)) values[name] = data.optLong(name, -1) }
                continue
            }
            val previous = values[channel] ?: continue
            val cursor = (message.opt("message_id") as? Number)?.toLong() ?: continue
            if (cursor <= previous) continue
            values[channel] = cursor
            if (channel == "/notification/${user.id}") {
                val noticeJson = data.optJSONObject("last_notification")?.optJSONObject("notification")
                val notice = noticeJson?.takeIf { it.positiveInt("id") != null }?.let {
                    DiscourseParser.notices(JSONObject().put("notifications", JSONArray().put(it))).firstOrNull()
                }
                val recent = data.optJSONArray("recent")
                val reads = buildMap {
                    if (recent != null) for (i in 0 until recent.length()) {
                        val pair = recent.optJSONArray(i) ?: continue
                        val id = (pair.opt(0) as? Number)?.toInt()?.takeIf { it > 0 } ?: continue
                        (pair.opt(1) as? Boolean)?.let { put(id, it) }
                    }
                }
                snapshots += NotificationSnapshot(notificationCount(data), notice, reads)
            } else {
                val chat = channel.startsWith("/chat/")
                alerts += NotificationAlert("$channel:$cursor",
                    plainText(data.text(if (chat) "translated_title" else "topic_title")).ifBlank { "新的社区消息" },
                    plainText(data.text("excerpt")).ifBlank { data.text("username").ifBlank { "你有一条新消息" } },
                    NotificationTarget(user.id, data.positiveInt("notification_id"),
                        data.positiveInt("topic_id"), data.positiveInt("post_number"),
                        if (chat) data.positiveInt("channel_id") else null,
                        if (chat) data.positiveInt("chat_message_id") ?: data.positiveInt("message_id") else null), data.text("username"))
            }
        }
        return NotificationBatch(snapshots, alerts, initial, reconnect)
    }
}
