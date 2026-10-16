package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class TopicMessageBusTest {
    @Test fun onlyNewRepliesAndStreamResetsTriggerChecksWhileEveryTargetMessageAdvancesTheCursor() {
        val cursor = TopicMessageBusCursors(71)
        assertEquals(mapOf("/topic/71" to -1L), cursor.values)
        assertTrue(cursor.accept(JSONArray("""[{"channel":"/__status","data":{"/topic/71":10}}]""")))
        assertFalse(cursor.accept(JSONArray("""[{"channel":"/topic/72","message_id":99,"data":{"type":"created"}},{"channel":"/topic/71","message_id":11,"data":{"type":"liked"}}]""")))
        assertEquals(11L, cursor.values["/topic/71"])
        assertFalse(cursor.accept(JSONArray("""[{"channel":"/topic/71","message_id":11,"data":{"type":"created"}}]""")))
        assertTrue(cursor.accept(JSONArray("""[{"channel":"/topic/71","message_id":12,"data":{"type":"created"}}]""")))
        assertFalse(cursor.accept(JSONArray("""[{"channel":"/__status","data":{"/topic/71":12}}]""")))
        assertTrue(cursor.accept(JSONArray("""[{"channel":"/__status","data":{"/topic/71":2}}]""")))
        assertTrue(cursor.accept(JSONArray("""[{"channel":"/topic/71","message_id":3,"data":{"reload_topic":true,"refresh_stream":true}}]""")))
    }

    @Test fun initialConnectionAndCreatedReplyUseTheTopicCursorAndPrivateCredentialWithoutCookies() = runBlocking {
        MockWebServer().use { main -> MockWebServer().use { push ->
            main.start(); push.start()
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="fixture-topic-key">"""))
            push.enqueue(MockResponse().setBody("""[{"channel":"/__status","data":{"/topic/71":10}}]"""))
            push.enqueue(MockResponse().setBody("""[{"channel":"/topic/71","message_id":11,"data":{"type":"created"}}]"""))
            val cookies = object : SessionCookies {
                override fun get(url: String) = "_t=fixture-private-cookie"
                override fun set(url: String, cookie: String) = Unit
            }
            val events = withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(cookies, main.url("/")), push.url("/"))
                    .observeTopic(Member(7, "fixture", "", ""), 71).take(2).toList()
            }
            assertEquals(2, events.size)
            val first = push.takeRequest()
            assertEquals("%2Ftopic%2F71=-1&__seq=1", first.body.readUtf8())
            assertEquals("fixture-topic-key", first.getHeader("X-Shared-Session-Key"))
            assertNull(first.getHeader("Cookie")); assertNull(first.getHeader("X-CSRF-Token"))
            assertEquals("%2Ftopic%2F71=10&__seq=2", push.takeRequest().body.readUtf8())
        } }
    }

    @Test fun reconnectChecksTheStreamEvenWithoutANewReplyAndCancellationClosesThePoll() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val events = java.util.concurrent.atomic.AtomicInteger()
            val job = launch {
                DiscourseMessageBus(DiscourseClient(NoCookies, server.url("/")), server.url("/"))
                    .observeTopic(null, 71).collect { events.incrementAndGet() }
            }
            try {
                withContext(Dispatchers.IO) { repeat(4) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) } }
                assertEquals(2, events.get())
                withTimeout(1000) { job.cancelAndJoin() }
            } finally { job.cancelAndJoin() }
        }
    }
}
