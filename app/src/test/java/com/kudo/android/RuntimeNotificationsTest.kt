package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.account.AccountViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeNotificationsTest {
    @Test fun cloudflareNotificationStreamPausesQuietlyAndResumesOnSessionCheck() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().setBody("""{"current_user":{"id":42,"username":"fixture"}}"""))
        val subscriptions = AtomicInteger()
        val vm = AccountViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), {}, {},
            NotificationUpdates {
                if (subscriptions.incrementAndGet() == 1) flow { throw ApiFailure(FailureKind.VERIFICATION, CloudflareVerificationMessage) }
                else flow { emit(NotificationBatch(emptyList())); awaitCancellation() }
            })
        suspend fun awaitIdle() { repeat(20) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } } }
        try {
            awaitIdle()
            assertEquals(42, vm.user?.id)
            assertNull("推送域名失败不应反复要求主站过盾", withTimeoutOrNull(200) { vm.events.first() })
            server.enqueue(MockResponse().setBody("""{"current_user":{"id":42,"username":"fixture"}}"""))
            assertTrue(vm.checkSession())
            awaitIdle()
            assertEquals(2, subscriptions.get())
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }

    @Test fun freshPageReadStatusSupersedesOlderRealtimeStatus() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer(); server.start()
        var pageRead = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(if (request.path!!.startsWith("/session/"))
                """{"current_user":{"id":42,"username":"fixture"}}"""
            else """{"notifications":[{"id":1,"read":$pageRead,"data":{"title":"已读夹具"}}],"total_rows_notifications":1}""")
        }
        val events = MutableSharedFlow<NotificationBatch>(extraBufferCapacity = 8)
        val vm = AccountViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), {}, {}, NotificationUpdates { events })
        suspend fun idle() { repeat(15) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } } }
        try {
            idle()
            events.emit(NotificationBatch(listOf(NotificationSnapshot(1, null, mapOf(1 to false)))))
            runCurrent(); vm.loadNotices(); idle(); assertFalse(vm.notices.single().read)
            pageRead = true
            vm.loadNotices(); idle()
            assertTrue("新分页结果不能被更旧的实时未读覆盖", vm.notices.single().read)
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }
    @Test fun runtimeCountsAndWebReadsUpdateWithoutOpeningPageAndFailedReadKeepsUnread() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer(); server.start()
        var failRead = true
        val writes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/session/current.json" -> MockResponse().setBody("""{"current_user":{"id":42,"username":"fixture","all_unread_notifications_count":3}}""")
                "/session/csrf" -> MockResponse().setBody("""{"csrf":"fixture"}""")
                "/notifications/mark-read" -> { writes.incrementAndGet(); if (failRead) MockResponse().setResponseCode(500).setBody("{}") else MockResponse().setBody("{}") }
                "/notifications" -> MockResponse().setBody("""{"notifications":[{"id":1,"read":false,"topic_id":10,"post_number":8,"data":{"title":"夹具"}}],"total_rows_notifications":1}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val events = MutableSharedFlow<NotificationBatch>(extraBufferCapacity = 8)
        val shown = mutableListOf<NotificationAlert>()
        val read = mutableSetOf<Int>()
        val delivery = object : NotificationDelivery {
            override fun show(alert: NotificationAlert) { shown += alert }
            override fun readForum(ids: Set<Int>, all: Boolean) { read += ids }
        }
        val vm = AccountViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), {}, {}, NotificationUpdates { events }, delivery)
        suspend fun awaitIdle() { repeat(20) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } } }
        try {
            awaitIdle(); assertEquals(42, vm.user?.id); assertEquals(3, vm.unreadCount)
            assertFalse(vm.noticesLoaded)
            events.emit(NotificationBatch(listOf(NotificationSnapshot(4, Notice(1, "夹具", "回复", 10, 8, false, ""), mapOf(1 to false)))))
            runCurrent()
            assertEquals(4, vm.unreadCount); assertEquals(1, shown.size); assertEquals(0, writes.get())
            vm.loadNotices(); awaitIdle(); assertFalse(vm.notices.single().read)
            vm.markRead(1); awaitIdle()
            assertFalse(vm.notices.single().read); assertEquals(4, vm.unreadCount)
            failRead = false
            vm.markRead(1); awaitIdle()
            assertTrue(vm.notices.single().read); assertTrue(1 in read)
            events.emit(NotificationBatch(listOf(NotificationSnapshot(0, null, mapOf(1 to true)))))
            runCurrent(); assertEquals(0, vm.unreadCount)
            val before = shown.size
            events.emit(NotificationBatch(listOf(NotificationSnapshot(0, Notice(1, "旧消息", "回复", 10, 8, true, ""), mapOf(1 to true))), reconnect = true))
            runCurrent(); assertEquals(before, shown.size)
            assertEquals(2, writes.get())
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }
    @Test fun chatAnchorAndReadReceiptUseServerContract() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val repo = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"messages":[],"meta":{}}"""))
            repo.chatMessages(7, target = 123)
            assertEquals("/chat/api/channels/7/messages?page_size=50&target_message_id=123", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"csrf":"fixture"}""")); server.enqueue(MockResponse().setBody("{}"))
            repo.readChat(7, 123)
            assertEquals("/session/csrf", server.takeRequest().path)
            val read = server.takeRequest()
            assertEquals("PUT", read.method)
            assertEquals("/chat/api/channels/7/read?message_id=123", read.path)
        } finally { server.shutdown() }
    }
}
