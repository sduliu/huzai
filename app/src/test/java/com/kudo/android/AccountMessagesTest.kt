package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class AccountMessagesTest {
    private val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
    @Test fun privateMessageAndChatUseSeparateEndpointsAndNeverRetryFailedSend() = runBlocking {
        val server = MockWebServer()
        try {
            val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"csrf":"fixture"}"""))
            server.enqueue(MockResponse().setBody("""{"id":5,"topic_id":9}"""))
            assertEquals(9, repository.createPrivateMessage(Draft("标题", "正文", recipients = "@a, b，a")).topicId)
            assertEquals("/session/csrf", server.takeRequest().path)
            val message = server.takeRequest()
            assertEquals("/posts.json", message.path)
            val body = message.body.readUtf8()
            assertTrue(body.contains("archetype=private_message")); assertTrue(body.contains("target_recipients=a%2Cb"))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"errors":["failed"]}"""))
            try { repository.sendChat(3, "正文"); fail("发送应失败") } catch (e: ApiFailure) { assertEquals(FailureKind.SERVER, e.kind) }
            assertEquals("/chat/3", server.takeRequest().path)
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun chatPastPaginationPreservesDeletedMessagesForReconciliation() = runBlocking {
        val server = MockWebServer()
        try {
            val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"messages":[{"id":2,"message":"正文","user":{"id":7,"username":"member"}},{"id":3,"deleted_at":"2026-01-01","user":{}}],"meta":{"can_load_more_past":true}}"""))
            val page = repository.chatMessages(4, 8)
            assertTrue(page.morePast); assertTrue(page.messages[1].deleted)
            assertEquals("/chat/api/channels/4/messages?page_size=50&direction=past&target_message_id=8", server.takeRequest().path)
            assertEquals(7, page.messages[0].author.id)
        } finally { server.shutdown() }
    }
}
