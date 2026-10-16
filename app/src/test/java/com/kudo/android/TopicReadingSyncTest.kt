package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

class TopicReadingSyncTest {
    @Test fun sendsCsrfProtectedMillisecondFormOnceAndOnlyConfirmsSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val confirmed = CompletableDeferred<Int>()
            val sync = TopicReadingSync(scope, CommunityRepository(DiscourseClient(NoCookies, server.url("/")))) { confirmed.complete(it) }
            try {
                sync.setAccount(7)
                server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
                server.enqueue(MockResponse().setResponseCode(204))
                sync.submit(7, TopicReadingBatch(12, 1200, mapOf(8 to 1200, 11 to 1000)))
                assertEquals(7, withTimeout(5000) { confirmed.await() })
                assertEquals("/session/csrf", server.takeRequest().path)
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/topics/timings", request.path)
                assertEquals("token", request.getHeader("X-CSRF-Token"))
                val form = java.net.URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                assertEquals(setOf("topic_id=12", "topic_time=1200", "timings[8]=1200", "timings[11]=1000"), form.split('&').toSet())
                assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
            } finally { sync.close(); scope.cancel() }
        }
    }

    @Test fun failuresAreNotRetriedOrConfirmedAndGuestsCannotSubmit() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var confirmations = 0
            val sync = TopicReadingSync(scope, CommunityRepository(DiscourseClient(NoCookies, server.url("/")))) { confirmations++ }
            try {
                val batch = TopicReadingBatch(12, 1000, mapOf(3 to 1000))
                sync.submit(null, batch)
                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
                sync.setAccount(7)
                server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
                server.enqueue(MockResponse().setResponseCode(500))
                sync.submit(7, batch)
                assertEquals("/session/csrf", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                assertEquals("/topics/timings", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
                assertEquals(0, confirmations)
                sync.setAccount(8)
                sync.submit(7, batch)
                assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
            } finally { sync.close(); scope.cancel() }
        }
    }

    @Test fun changingAccountsCancelsInflightAndQueuedOldReading() = runBlocking {
        val gate = CountDownLatch(1)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/session/csrf") gate.await(5, TimeUnit.SECONDS)
                    return MockResponse().setBody("""{"csrf":"token"}""")
                }
            }
            server.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val sync = TopicReadingSync(scope, CommunityRepository(DiscourseClient(NoCookies, server.url("/")))) { fail("旧会话不能确认") }
            try {
                sync.setAccount(7)
                val batch = TopicReadingBatch(12, 1000, mapOf(3 to 1000))
                sync.submit(7, batch); sync.submit(7, batch)
                assertEquals("/session/csrf", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                sync.setAccount(8)
                gate.countDown()
                assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
            } finally { gate.countDown(); sync.close(); scope.cancel() }
        }
    }
}
