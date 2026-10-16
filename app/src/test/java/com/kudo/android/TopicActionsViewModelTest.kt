package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.topic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TopicActionsViewModelTest {
    private val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
    private val post = Post(10, 1, Member(2, "author", "", ""), "<p>正文</p>", "", 0, false, null, null, false,
        version = 3, canViewEditHistory = true, availableFlags = setOf(7))
    private val viewer = Member(1, "me", "", "", canIgnoreUsers = true)
    private suspend fun await(predicate: () -> Boolean) = withContext(Dispatchers.IO) { withTimeout(5000) { while (!predicate()) delay(10) } }
    private val flags = """{"post_action_types":[{"id":7,"name_key":"notify_moderators","name":"其他","description":"说明","position":1,"require_message":true,"enabled":true,"is_flag":true,"applies_to":["Post"]}]}"""
    private val html = """<script type="application/json" id="data-preloaded">{"siteSettings":{"min_personal_message_post_length":3}}</script>"""

    @Test fun replyMenuRetainsItsExactTargetForLinksHistoryReportsAndBookmarks() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            val writes = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, String>>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/site.json" -> MockResponse().setBody(flags)
                    "/latest" -> MockResponse().setBody(html)
                    "/session/csrf" -> MockResponse().setBody("""{"csrf":"test"}""")
                    "/posts/81/revisions/latest.json" -> MockResponse().setBody("""{"post_id":81,"current_revision":3}""")
                    "/post_actions", "/bookmarks.json" -> {
                        writes += request.path!! to request.body.readUtf8()
                        MockResponse().setBody("""{"id":99}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val vm = TopicActionsViewModel(CommunityRepository(DiscourseClient(cookies, server.url("/"))), viewer)
            val reply = post.copy(id = 81, number = 8)
            try {
                vm.open(5, true, reply, isReply = true)
                assertEquals(81, vm.state.post!!.id)
                assertEquals("https://linux.do/t/topic/5/8", vm.state.link)
                assertEquals(0, server.requestCount)
                vm.history(); await { !vm.state.loading }
                assertEquals(81, vm.state.revision!!.postId)
                vm.report(); await { !vm.state.loading }
                vm.chooseFlag(vm.state.flags!!.reasons.single()); vm.message("举报说明"); vm.submitReport()
                await { !vm.state.busy }
                assertTrue(writes.single().second.startsWith("id=81&"))
                vm.open(5, true, reply, isReply = true); vm.bookmark(); await { !vm.state.busy }
                assertTrue(writes.last().second.contains("bookmarkable_id=81"))
                vm.open(5, true, post)
                assertEquals("https://linux.do/t/topic/5", vm.state.link)
                assertEquals(10, vm.state.post!!.id)
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }

    @Test fun metadataIsFetchedOnlyOnOpenAndLateReadCannotReplaceNewTarget() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.start()
            val repo = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            val vm = TopicActionsViewModel(repo, viewer)
            try {
                assertEquals(0, server.requestCount)
                server.enqueue(MockResponse().setBody("""{"id":5,"post_stream":{"posts":[{"id":10,"post_number":1}],"stream":[10]}}""").setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                vm.open(5)
                await { server.requestCount == 1 }
                vm.open(6, true, post.copy(id = 11))
                delay(300)
                assertEquals(6, vm.state.topicId)
                assertEquals(11, vm.state.post!!.id)
                assertFalse(vm.state.loading)
                assertEquals(1, server.requestCount)
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }

    @Test fun doubleSubmitWritesOnceAndServerFailureKeepsTheDraft() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            val writes = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when(request.path) {
                    "/site.json" -> MockResponse().setBody(flags)
                    "/latest" -> MockResponse().setBody(html)
                    "/session/csrf" -> MockResponse().setBody("""{"csrf":"test"}""")
                    "/post_actions" -> { writes.incrementAndGet(); MockResponse().setResponseCode(429).setBody("""{"errors":["稍后再试"]}""").setBodyDelay(150, java.util.concurrent.TimeUnit.MILLISECONDS) }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val vm = TopicActionsViewModel(CommunityRepository(DiscourseClient(cookies, server.url("/"))), viewer)
            try {
                vm.open(5, true, post); vm.report()
                await { !vm.state.loading }
                vm.chooseFlag(vm.state.flags!!.reasons.single()); vm.message("举报说明")
                vm.submitReport(); vm.submitReport(); vm.dismiss()
                assertTrue(vm.state.busy)
                await { !vm.state.busy }
                assertEquals(1, writes.get())
                assertEquals("举报说明", vm.state.message)
                assertEquals(FailureKind.RATE_LIMIT, (vm.state.error as ApiFailure).kind)
                assertFalse(vm.state.writeLocked)
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }

    @Test fun ignoreRequiresDurationAndOnlySuccessfulWriteEmitsAccountChange() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"csrf":"test"}"""))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"errors":["不能忽略此用户"]}"""))
            val vm = TopicActionsViewModel(CommunityRepository(DiscourseClient(cookies, server.url("/"))), viewer)
            try {
                vm.open(5, false, post); vm.ignore(); vm.submitIgnore()
                assertEquals(0, server.requestCount)
                vm.duration(IgnoreDuration.MONTH); vm.submitIgnore(); vm.submitIgnore()
                await { !vm.state.busy }
                assertEquals(2, server.requestCount)
                assertNotNull(vm.state.error)
                assertEquals(TopicActionPanel.IGNORE, vm.state.panel)
                assertEquals(IgnoreDuration.MONTH, vm.state.duration)
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
    @Test fun successfulReportCannotBeSubmittedAgainFromStaleDetailMetadata() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when(request.path) {
                    "/site.json" -> MockResponse().setBody(flags)
                    "/latest" -> MockResponse().setBody(html)
                    "/session/csrf" -> MockResponse().setBody("""{"csrf":"test"}""")
                    "/post_actions" -> MockResponse().setBody("""{"id":10}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val vm = TopicActionsViewModel(CommunityRepository(DiscourseClient(cookies, server.url("/"))), viewer)
            try {
                vm.open(5, true, post); vm.report(); await { !vm.state.loading }
                vm.chooseFlag(vm.state.flags!!.reasons.single()); vm.message("举报说明"); vm.submitReport()
                await { vm.state.topicId == null }
                vm.open(5, true, post); vm.report(); await { !vm.state.loading }
                assertTrue("成功提交的原因不能被旧主帖状态再次启用", vm.state.flags!!.reasons.isEmpty())
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
    @Test fun unconfirmedReportKeepsDraftAndBlocksResubmissionEvenAfterReopening() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            val writes = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when(request.path) {
                    "/site.json" -> MockResponse().setBody(flags)
                    "/latest" -> MockResponse().setBody(html)
                    "/session/csrf" -> MockResponse().setBody("""{"csrf":"test"}""")
                    "/post_actions" -> { writes.incrementAndGet(); MockResponse().setBody("{}") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val vm = TopicActionsViewModel(CommunityRepository(DiscourseClient(cookies, server.url("/"))), viewer)
            try {
                vm.open(5, true, post); vm.report(); await { !vm.state.loading }
                vm.chooseFlag(vm.state.flags!!.reasons.single()); vm.message("举报说明"); vm.submitReport()
                await { !vm.state.busy }
                assertTrue(vm.state.writeLocked)
                assertEquals("举报说明", vm.state.message)
                vm.submitReport()
                vm.dismiss(); vm.open(5, true, post); vm.report(); await { !vm.state.loading }
                vm.chooseFlag(vm.state.flags!!.reasons.single()); vm.message("举报说明"); vm.submitReport()
                assertTrue(vm.state.writeLocked)
                assertEquals(1, writes.get())
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
}
