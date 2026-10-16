package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MessageBusTest {
    @Test fun cursorInitializesDeduplicatesAndRecoversFromReset() {
        val cursors = MessageBusCursors(7)
        assertEquals(setOf("/latest", "/new", "/unread", "/unread/7"), cursors.values.keys)
        assertTrue(cursors.values.values.all { it == -1L })
        assertTrue(cursors.accept(JSONArray("""[{"channel":"/__status","data":{"/latest":100,"/unread/7":8}}]""")))
        assertEquals(100L, cursors.values["/latest"])
        assertFalse(cursors.accept(JSONArray("""[{"channel":"/latest","message_id":100},{"channel":"/unread/8","message_id":999}]""")))
        assertTrue(cursors.accept(JSONArray("""[{"channel":"/latest","message_id":101},{"channel":"/latest","message_id":101}]""")))
        assertEquals(101L, cursors.values["/latest"])
        assertTrue(cursors.accept(JSONArray("""[{"channel":"/__status","data":{"/latest":2}}]""")))
        assertEquals(2L, cursors.values["/latest"])
        assertEquals(setOf("/latest"), MessageBusCursors(null).values.keys)
    }

    @Test fun privateSubscriptionUsesHtmlKeyButNeverForwardsCookies() = runBlocking {
        val main = MockWebServer()
        val push = MockWebServer()
        main.start(); push.start()
        try {
            main.enqueue(MockResponse().setBody("""<html><meta name="shared_session_key" content="fixture-shared"></html>"""))
            push.enqueue(MockResponse().setBody("""[{"channel":"/__status","data":{"/latest":10}}]"""))
            val cookies = object : SessionCookies {
                override fun get(url: String) = "_t=fixture-main-cookie"
                override fun set(url: String, cookie: String) = Unit
            }
            val bus = DiscourseMessageBus(DiscourseClient(cookies, main.url("/")), push.url("/"))
            withTimeout(5000) { bus.observe(Member(7, "fixture", "", "")).first() }
            val bootstrap = main.takeRequest()
            assertEquals("/latest", bootstrap.path)
            assertEquals("text/html", bootstrap.getHeader("Accept"))
            assertEquals("_t=fixture-main-cookie", bootstrap.getHeader("Cookie"))
            val request = push.takeRequest()
            assertEquals("fixture-shared", request.getHeader("X-Shared-Session-Key"))
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("X-CSRF-Token"))
            assertEquals("true", request.getHeader("Dont-Chunk"))
            assertTrue(request.path!!.endsWith("/poll?dlp=t"))
            assertTrue(request.body.readUtf8().contains("%2Funread%2F7=-1"))
        } finally { main.shutdown(); push.shutdown() }
    }

    @Test fun anonymousPollReusesCursorAndCancellationClosesPendingRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().setBody("""[{"channel":"/__status","data":{"/latest":12}}]"""))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = DiscourseClient(NoCookies, server.url("/"))
        val job = launch { DiscourseMessageBus(client, server.url("/")).observe(null).collect {} }
        try {
            val initial = withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) }!!
            assertNull(initial.getHeader("X-Shared-Session-Key"))
            assertEquals("%2Flatest=-1&__seq=1", initial.body.readUtf8())
            val next = withContext(Dispatchers.IO) { server.takeRequest(4, TimeUnit.SECONDS) }!!
            assertFalse(next.path!!.contains("dlp"))
            assertEquals("%2Flatest=12&__seq=2", next.body.readUtf8())
            withTimeout(1000) { job.cancelAndJoin() }
        } finally { job.cancelAndJoin(); server.shutdown() }
    }

    @Test fun rateLimitWaitsBeforeRetryAndRecovers() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
            server.enqueue(MockResponse().setBody("[]"))
            val job = async { DiscourseMessageBus(DiscourseClient(NoCookies, server.url("/")), server.url("/")).observe(null).first() }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            withContext(Dispatchers.IO) { assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS)) }
            withTimeout(4000) { job.await() }
            assertEquals(2, server.requestCount)
            assertEquals(30, retryAfterSeconds("30"))
            assertNotNull(retryAfterSeconds("Wed, 21 Oct 2037 07:28:00 GMT"))
        } finally { server.shutdown() }
    }

    @Test fun cloudflareChallengeStopsPollingWithVerificationFailure() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setBody("<html>Just a moment...</html>"))
            server.enqueue(MockResponse().setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setBody("<html>Just a moment...</html>"))
            val failure = runCatching {
                withTimeout(8000) {
                    DiscourseMessageBus(DiscourseClient(NoCookies, server.url("/")), server.url("/")).observe(null).first()
                }
            }.exceptionOrNull()
            assertTrue("challenge should be surfaced instead of retried forever", failure is ApiFailure)
            assertEquals(FailureKind.VERIFICATION, (failure as ApiFailure).kind)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun transientCloudflareChallengeIsRetriedWithoutSurfacing() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403)
                .setHeader("cf-mitigated", "challenge")
                .setBody("<html>Just a moment...</html>"))
            server.enqueue(MockResponse().setBody("[{\"channel\":\"/__status\",\"data\":{\"/latest\":12}}]"))
            withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(NoCookies, server.url("/")), server.url("/"))
                    .observe(null).first()
            }
            assertEquals("a transient challenge should be retried", 2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun redirectCannotForwardSharedKeyAndMissingBootstrapKeyIsRejected() = runBlocking {
        val main = MockWebServer(); val push = MockWebServer(); val other = MockWebServer()
        main.start(); push.start(); other.start()
        try {
            main.enqueue(MockResponse().setBody("<html>login</html>"))
            val client = DiscourseClient(NoCookies, main.url("/"))
            assertTrue(runCatching { client.sharedSessionKey() }.exceptionOrNull() is ApiFailure)
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="fixture">"""))
            push.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/poll")))
            val job = launch { DiscourseMessageBus(client, push.url("/")).observe(Member(7, "fixture", "", "")).collect {} }
            withContext(Dispatchers.IO) { assertNotNull(push.takeRequest(3, TimeUnit.SECONDS)); assertNull(other.takeRequest(300, TimeUnit.MILLISECONDS)) }
            job.cancelAndJoin()
        } finally { main.shutdown(); push.shutdown(); other.shutdown() }
    }
}
