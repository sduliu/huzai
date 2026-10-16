package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class NotificationProtocolTest {
    @Test fun sessionCountsAndCursorDoNotDependOnLoadedNotificationPage() {
        val member = DiscourseParser.member(JSONObject("""{"id":42,"username":"fixture","all_unread_notifications_count":126,"notification_channel_position":86}"""))
        assertEquals(126, member.unreadNotifications)
        assertEquals(86L, member.notificationChannelPosition)
        assertEquals(0, DiscourseParser.member(JSONObject("""{"id":42}""")).unreadNotifications)
    }
    @Test fun channelsDeduplicateRecoverResetAndPreserveReadStatesAndChatTarget() {
        val cursors = NotificationCursors(Member(42, "fixture", "", "", notificationChannelPosition = 5))
        val messages = JSONArray("""[
          {"channel":"/notification/42","message_id":6,"data":{"all_unread_notifications_count":123,"last_notification":{"notification":{"id":70,"topic_id":10,"post_number":8,"data":{"title":"主题"}}},"recent":[[70,false],[69,true],null,["bad",true]]}},
          {"channel":"/chat/notification-alert/42","message_id":8,"data":{"channel_id":9,"chat_message_id":20,"translated_title":"聊天","excerpt":"<p>你好</p>"}},
          {"channel":"/notification/43","message_id":999,"data":{}}
        ]""")
        val batch = cursors.accept(messages, true, true)
        assertTrue(batch.initial)
        assertEquals(123, batch.snapshots.single().count)
        assertEquals(70, batch.snapshots.single().notice!!.id)
        assertEquals(mapOf(70 to false, 69 to true), batch.snapshots.single().recent)
        assertEquals(9, batch.alerts.single().target.chatChannelId)
        assertEquals(20, batch.alerts.single().target.chatMessageId)
        assertEquals("你好", batch.alerts.single().body)
        assertTrue(cursors.accept(messages, false, false).snapshots.isEmpty())
        assertTrue(cursors.accept(messages, false, false).alerts.isEmpty())
        cursors.accept(JSONArray("""[{"channel":"/__status","data":{"/notification/42":1}}]"""), false, true)
        assertEquals(1L, cursors.values["/notification/42"])
        assertEquals(1, cursors.accept(JSONArray("""[{"channel":"/notification/42","message_id":2,"data":{"unread_notifications":3,"unread_high_priority_notifications":1}}]"""), false, false).snapshots.size)
    }
    @Test fun notificationSubscriptionSharesTransportSecurityAndUsesSessionPosition() = runBlocking {
        val main = MockWebServer(); val push = MockWebServer()
        main.start(); push.start()
        try {
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="offline-key">"""))
            push.enqueue(MockResponse().setBody("[]"))
            val cookies = object : SessionCookies {
                override fun get(url: String) = "_t=main-only"
                override fun set(url: String, cookie: String) {}
            }
            val bus = DiscourseMessageBus(DiscourseClient(cookies, main.url("/")), push.url("/"))
            val batch = withTimeout(5000) { bus.notifications(Member(42, "fixture", "", "", notificationChannelPosition = 88)).first() }
            assertTrue(batch.initial)
            val request = push.takeRequest()
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("X-CSRF-Token"))
            assertEquals("offline-key", request.getHeader("X-Shared-Session-Key"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("%2Fnotification%2F42=88"))
            assertTrue(body.contains("%2Fnotification-alert%2F42=-1"))
            assertTrue(body.contains("%2Fchat%2Fnotification-alert%2F42=-1"))
        } finally { main.shutdown(); push.shutdown() }
    }
}
