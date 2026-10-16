package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.account.LikedPostsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.*
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class LikedPostsViewModelTest {
    private fun action(id: Int, floor: Int = 8) =
        """{"id":$id,"topic_id":99,"post_number":$floor,"title":"点赞内容","action_type":1}"""
    private fun page(rows: List<String>) = MockResponse().setBody("""{"user_actions":[${rows.joinToString(",")}]}""")
    private suspend fun TestScope.finish(vm: LikedPostsViewModel) {
        runCurrent()
        vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
    }

    @Test fun usesCurrentAccountGivenLikesAndRawOffsetWithoutMergingDifferentFloors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(List(29) { action(1) } + "{}"))
            server.enqueue(page(listOf(action(1), action(2, 9))))
            server.enqueue(page(listOf(action(3, 10))))
            val vm = LikedPostsViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "current-member")
            try {
                finish(vm)
                assertEquals(1, vm.state.value.entries.size)
                assertEquals("30", vm.state.value.next)
                vm.load(true); finish(vm)
                assertEquals(listOf(8, 9), vm.state.value.entries.map { it.number })
                assertEquals("https://linux.do/t/topic/99/9", vm.state.value.entries.last().url)
                assertNull(vm.state.value.next)
                vm.load(); finish(vm)
                assertEquals(listOf(10), vm.state.value.entries.map { it.number })
                val requests = List(3) { server.takeRequest() }
                assertEquals(listOf("0", "30", "0"), requests.map { it.requestUrl!!.queryParameter("offset") })
                requests.forEach {
                    assertEquals("GET", it.method)
                    assertEquals("/user_actions.json", it.requestUrl!!.encodedPath)
                    assertEquals("current-member", it.requestUrl!!.queryParameter("username"))
                    assertEquals("1", it.requestUrl!!.queryParameter("filter"))
                }
                assertEquals(3, server.requestCount)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun retriesFailedPaginationAndRefreshUsingTheirOwnCursorsWhileKeepingEntries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(List(30) { action(it + 1) }))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(page(List(30) { action(it + 31) }))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(page(listOf(action(100, 20))))
            val vm = LikedPostsViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "member")
            try {
                finish(vm)
                vm.load(true); finish(vm)
                assertNotNull(vm.state.value.error)
                assertEquals(30, vm.state.value.entries.size)
                assertEquals("30", vm.state.value.next)
                vm.retry(); finish(vm)
                assertNull(vm.state.value.error)
                assertEquals(60, vm.state.value.entries.size)
                vm.load(); finish(vm)
                assertNotNull(vm.state.value.error)
                assertEquals(60, vm.state.value.entries.size)
                vm.retry(); finish(vm)
                assertEquals(listOf(20), vm.state.value.entries.map { it.number })
                assertNull(vm.state.value.error)
                assertEquals(listOf("0", "30", "30", "0", "0"),
                    List(5) { server.takeRequest().requestUrl!!.queryParameter("offset") })
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun refreshDiscardsDelayedResponseAndSeparateAccountKeepsItsOwnFeed() = runTest {
        val first = CompletableDeferred<ControlledCall>()
        val refreshed = CompletableDeferred<ControlledCall>()
        val second = CompletableDeferred<ControlledCall>()
        val firstAccountRequests = AtomicInteger()
        val requests = CopyOnWriteArrayList<Request>()
        val calls = Call.Factory { request -> ControlledCall(request) { call ->
            requests += request
            check(request.url.encodedPath == "/user_actions.json")
            check(request.url.queryParameter("filter") == "1")
            check(request.url.queryParameter("offset") == "0")
            when (request.url.queryParameter("username")) {
                "first" -> if (firstAccountRequests.incrementAndGet() == 1) first.complete(call) else refreshed.complete(call)
                "second" -> second.complete(call)
                else -> error("Unexpected account in likes fixture")
            }
        } }
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { main ->
            Dispatchers.setMain(main)
            val repository = CommunityRepository(DiscourseClient(NoCookies, "https://likes.test/".toHttpUrl(), route = { calls }))
            val vm = withContext(main) { LikedPostsViewModel(repository, "first") }
            var other: LikedPostsViewModel? = null
            suspend fun awaitCall(pending: CompletableDeferred<ControlledCall>) = withContext(Dispatchers.IO) {
                withTimeout(5000) { pending.await() }
            }
            suspend fun awaitFloor(model: LikedPostsViewModel, floor: Int) = withContext(Dispatchers.Default) {
                withTimeout(5000) { model.state.first { !it.loading && it.entries.singleOrNull()?.number == floor } }
            }
            try {
                val stale = awaitCall(first)
                withContext(main) { vm.load() }
                val latest = awaitCall(refreshed)
                assertTrue("刷新必须取消旧请求", stale.isCanceled())
                withContext(Dispatchers.IO) { latest.respond("""{"user_actions":[${action(2, 3)}]}""") }
                awaitFloor(vm, 3)
                assertEquals(listOf(3), vm.state.value.entries.map { it.number })
                // The response callback may already be in flight when cancellation occurs.
                withContext(Dispatchers.IO) { stale.respond("""{"user_actions":[${action(1, 2)}]}""") }
                withContext(main) { yield() }
                assertEquals(listOf(3), vm.state.value.entries.map { it.number })
                val currentAccount = withContext(main) { LikedPostsViewModel(repository, "second") }.also { other = it }
                val current = awaitCall(second)
                withContext(Dispatchers.IO) { current.respond("""{"user_actions":[${action(3, 4)}]}""") }
                awaitFloor(currentAccount, 4)
                assertEquals(listOf(4), currentAccount.state.value.entries.map { it.number })
                assertEquals(listOf(3), vm.state.value.entries.map { it.number })
                assertEquals(listOf("first", "first", "second"), requests.map { it.url.queryParameter("username") })
            } finally {
                withContext(main) {
                    vm.viewModelScope.coroutineContext.job.cancelAndJoin()
                    other?.viewModelScope?.coroutineContext?.job?.cancelAndJoin()
                }
                Dispatchers.resetMain()
            }
        }
    }

    private class ControlledCall(private val request: Request, private val onEnqueue: (ControlledCall) -> Unit) : Call {
        private var callback: Callback? = null
        @Volatile private var cancelled = false
        override fun request() = request
        override fun enqueue(responseCallback: Callback) {
            check(callback == null)
            callback = responseCallback
            onEnqueue(this)
        }
        fun respond(body: String) {
            checkNotNull(callback).onResponse(this, Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build())
        }
        override fun cancel() { cancelled = true }
        override fun isCanceled() = cancelled
        override fun isExecuted() = callback != null
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = ControlledCall(request, onEnqueue)
        override fun execute(): Response = error("Fixture expects asynchronous HTTP calls")
    }

    @Test fun emptyPageStopsPaginationAndRemainsSuccessfulEmptyState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(emptyList()))
            val vm = LikedPostsViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "member")
            try {
                finish(vm)
                vm.load(true); runCurrent()
                assertTrue(vm.state.value.loaded)
                assertFalse(vm.state.value.loading)
                assertTrue(vm.state.value.entries.isEmpty())
                assertNull(vm.state.value.next)
                assertNull(vm.state.value.error)
                assertEquals(1, server.requestCount)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }
}
