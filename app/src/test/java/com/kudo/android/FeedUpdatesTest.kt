package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.feed.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class FeedUpdatesTest {
    @Test fun fixtureCloseWaitsForCoroutineCleanupBeforeResettingMain() = runBlocking {
        val fixture = Fixture()
        fixture.ready()
        val started = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val cleaned = AtomicBoolean()
        val pending = fixture.vm.viewModelScope.launch(CoroutineExceptionHandler { _, _ -> }) {
            started.complete(Unit)
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { allowCleanup.await(); cleaned.set(true) } }
        }
        started.await()
        val closing = async(Dispatchers.IO) { fixture.close() }
        try {
            withTimeout(5000) { while (!pending.isCancelled) delay(1) }
            assertNull("夹具关闭必须等待取消清理，不能提前重置Main或关闭线程",
                withTimeoutOrNull(300) { closing.await(); true })
        } finally {
            allowCleanup.complete(Unit)
            withTimeout(5000) { closing.await(); pending.join() }
        }
        assertTrue(cleaned.get())
        assertTrue(fixture.vm.viewModelScope.coroutineContext.job.isCompleted)
    }

    @Test fun verificationFailureFromBackgroundUpdatesKeepsTheSuccessfulFeedAndCanResume() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[{"id":11,"title":"正常话题"}]}}"""))
        val subscriptions = AtomicInteger()
        val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))),
            updates = FeedUpdates {
                if (subscriptions.incrementAndGet() == 1) flow { throw ApiFailure(FailureKind.VERIFICATION, CloudflareVerificationMessage) }
                else flow { emit(Unit); awaitCancellation() }
            })
        try {
            withTimeout(5000) { while (vm.state.value.loading) delay(10) }
            vm.setUpdatesActive(true)
            assertEquals(1, subscriptions.get())
            assertNull("独立推送过盾不能遮盖正常列表", vm.state.value.error)
            assertEquals(listOf(11), vm.state.value.topics.map(Topic::id))
            vm.setUpdatesActive(true)
            withTimeout(5000) { while (subscriptions.get() < 2) delay(10) }
        } finally {
            vm.viewModelScope.cancel()
            vm.viewModelScope.coroutineContext.job.join()
            server.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test fun existingProbeUpdatesReadingBadgesWithoutReplacingOrReorderingThePage() = runBlocking {
        Fixture().use { f ->
            f.ready()
            f.content.set("""{"topic_list":{"topics":[{"id":2,"unread_posts":50},{"id":1,"unseen":true,"unread_posts":0}]}}""")
            f.message(); f.until { f.vm.state.value.topics.single().unseen }
            assertEquals(listOf(1), f.vm.state.value.topics.map(Topic::id))
            assertEquals("/latest.json?page=1", f.vm.state.value.next)
            f.content.set("""{"topic_list":{"topics":[{"id":1,"unseen":false,"unread_posts":3}]}}""")
            f.message(); f.until { f.vm.state.value.topics.single().unreadPosts == 3 }
            assertFalse(f.vm.state.value.topics.single().unseen)
        }
    }
    private class Fixture : AutoCloseable {
        val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val messages = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val subscribers = mutableListOf<Int?>()
        val pages = AtomicInteger()
        val content = AtomicReference(body(1))
        val nextResponse = AtomicReference<MockResponse?>(null)
        val server = MockWebServer()
        val vm: FeedViewModel
        init {
            Dispatchers.setMain(main)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/site.json" -> MockResponse().setBody("""{"categories":[{"id":1,"name":"分类"}]}""")
                    request.path!!.contains("topic-tracking") -> MockResponse().setBody("[]")
                    else -> { pages.incrementAndGet(); nextResponse.getAndSet(null) ?: MockResponse().setBody(content.get()) }
                }
            }
            server.start()
            vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))),
                updates = FeedUpdates { user -> messages.onStart { subscribers.add(user?.id) } }, updateIntervalMillis = 80)
        }
        suspend fun act(block: () -> Unit) = withContext(main) { block() }
        suspend fun until(condition: () -> Boolean) = withTimeout(5000) { while (!withContext(main) { condition() }) delay(10) }
        suspend fun ready() {
            until { !vm.state.value.loading && vm.state.value.categories.isNotEmpty() }
            act { vm.setUpdatesActive(true) }
            until { pages.get() >= 2 }
            delay(100)
        }
        suspend fun message() = act { messages.tryEmit(Unit); Unit }
        override fun close() {
            try {
                runBlocking { withContext(main) { vm.viewModelScope.coroutineContext.job.cancelAndJoin() } }
            } finally {
                server.shutdown()
                Dispatchers.resetMain()
                main.close()
            }
        }
        companion object {
            fun body(id: Int, replies: Int = 0, views: Int = 0) = """{"topic_list":{"topics":[{"id":$id,"title":"话题$id","posts_count":${replies + 1},"views":$views}],"more_topics_url":"/latest.json?page=1"}}"""
        }
    }

    @Test fun coalescesHintsChecksCurrentFilterAndPreservesListAndPagination() = runBlocking {
        Fixture().use { f ->
            f.ready()
            f.content.set(Fixture.body(1, views = 100))
            val before = f.pages.get()
            f.message(); f.until { f.pages.get() > before }; delay(100)
            assertFalse(f.vm.state.value.hasUpdates)
            f.content.set(Fixture.body(2))
            val checks = f.pages.get()
            f.act { repeat(20) { f.messages.tryEmit(Unit) } }
            f.until { f.vm.state.value.hasUpdates }
            delay(150)
            assertEquals(checks + 1, f.pages.get())
            assertEquals(listOf(1), f.vm.state.value.topics.map(Topic::id))
            assertEquals("/latest.json?page=1", f.vm.state.value.next)
            assertEquals(0L, f.vm.state.value.scrollToTopRequest)
            f.act { f.vm.select("hot") }
            f.until { !f.vm.state.value.loading }
            assertFalse(f.vm.state.value.hasUpdates)
            f.message(); delay(200)
            assertFalse(f.vm.state.value.hasUpdates)
        }
    }

    @Test fun failedRefreshRetainsPromptAndSuccessfulRetryAcknowledgesIt() = runBlocking {
        Fixture().use { f ->
            f.ready(); f.content.set(Fixture.body(2)); f.message()
            f.until { f.vm.state.value.hasUpdates }
            f.nextResponse.set(MockResponse().setResponseCode(503).setBody("{}"))
            f.act { f.vm.refreshFromNavigation() }
            f.until { !f.vm.state.value.loading }
            assertNotNull(f.vm.state.value.error)
            assertTrue(f.vm.state.value.hasUpdates)
            assertEquals(1L, f.vm.state.value.scrollToTopRequest)
            f.act { f.vm.refreshFromNavigation() }
            f.until { !f.vm.state.value.loading }
            assertFalse(f.vm.state.value.hasUpdates)
            assertEquals(listOf(2), f.vm.state.value.topics.map(Topic::id))
        }
    }

    @Test fun newerMessageDuringRefreshIsNotAcknowledgedByOlderResponse() = runBlocking {
        Fixture().use { f ->
            f.ready(); f.content.set(Fixture.body(2)); f.message()
            f.until { f.vm.state.value.hasUpdates }
            f.nextResponse.set(MockResponse().setBody(Fixture.body(2)).setBodyDelay(300, TimeUnit.MILLISECONDS))
            val before = f.pages.get()
            f.act { f.vm.refresh() }
            f.until { f.pages.get() > before }
            f.content.set(Fixture.body(3)); f.message()
            f.until { !f.vm.state.value.loading }
            assertTrue(f.vm.state.value.hasUpdates)
            f.until { f.pages.get() >= before + 2 }; delay(120)
            assertTrue(f.vm.state.value.hasUpdates)
            assertEquals(listOf(2), f.vm.state.value.topics.map(Topic::id))
            f.act { f.vm.refresh() }; f.until { !f.vm.state.value.loading }
            assertFalse(f.vm.state.value.hasUpdates)
        }
    }

    @Test fun refreshThatAlreadyIncludesConcurrentUpdateClearsAfterConfirmation() = runBlocking {
        Fixture().use { f ->
            f.ready(); f.content.set(Fixture.body(1, replies = 1)); f.message()
            f.until { f.vm.state.value.hasUpdates }
            f.nextResponse.set(MockResponse().setBody(f.content.get()).setBodyDelay(250, TimeUnit.MILLISECONDS))
            val before = f.pages.get()
            f.act { f.vm.refresh() }; f.until { f.pages.get() > before }; f.message()
            f.until { !f.vm.state.value.loading && !f.vm.state.value.hasUpdates }
            assertEquals(1, f.vm.state.value.topics.single().replies)
        }
    }

    @Test fun backgroundStopsChecksAndAccountSwitchDiscardsOldInFlightProbe() = runBlocking {
        Fixture().use { f ->
            f.ready()
            f.nextResponse.set(MockResponse().setBody(Fixture.body(99)).setBodyDelay(300, TimeUnit.MILLISECONDS))
            val before = f.pages.get()
            f.message(); f.until { f.pages.get() > before }
            f.act { f.vm.setUser(Member(7, "alice", "", "")) }
            f.until { !f.vm.state.value.loading && f.subscribers.last() == 7 }
            delay(400)
            assertFalse(f.vm.state.value.hasUpdates)
            assertEquals("unread", f.vm.state.value.filter)
            assertEquals(listOf(1), f.vm.state.value.topics.map(Topic::id))
            f.act { f.vm.setUpdatesActive(false) }
            val paused = f.pages.get()
            f.content.set(Fixture.body(2)); f.message(); delay(250)
            assertEquals(paused, f.pages.get())
            assertFalse(f.vm.state.value.hasUpdates)
            f.act { f.vm.setUpdatesActive(true) }
            f.until { f.vm.state.value.hasUpdates }
            f.act { f.vm.setUser(null) }
            assertFalse(f.vm.state.value.hasUpdates)
            assertNull(f.vm.state.value.accountId)
            f.until { !f.vm.state.value.loading && f.subscribers.last() == null }
        }
    }
}
