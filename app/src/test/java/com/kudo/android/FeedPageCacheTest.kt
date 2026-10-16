package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.feed.FeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class FeedPageCacheTest {
    @Test fun confirmedReadingKeepsLoadedUnreadPagesUntilExplicitRefresh() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(when {
                    request.path == "/site.json" -> """{"categories":[{"id":1,"name":"分类"}]}"""
                    request.path!!.contains("topic-tracking") -> "[]"
                    request.path!!.contains("page=1") -> """{"topic_list":{"topics":[{"id":2}],"more_topics_url":"/unseen.json?page=2"}}"""
                    else -> """{"topic_list":{"topics":[{"id":1}],"more_topics_url":"/unseen.json?page=1"}}"""
                })
            }
            server.start()
            val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), initialUser = Member(7, "seven", "七", ""))
            suspend fun idle() = withContext(Dispatchers.Default) { withTimeout(5000) { vm.viewModelScope.coroutineContext.job.children.toList().joinAll() } }
            try {
                idle(); vm.more(); idle()
                val before = vm.state.value
                assertEquals(listOf(1, 2), before.topics.map(Topic::id))
                vm.refreshReadListsIfRequested(1, 7)
                assertFalse(vm.state.value.loading)
                idle()
                assertEquals(before.topics, vm.state.value.topics)
                assertEquals("/unseen.json?page=2", vm.state.value.next)
                assertEquals(before.scrollToTopRequest, vm.state.value.scrollToTopRequest)
                assertTrue(vm.state.value.hasUpdates)
                vm.refreshFromNavigation(); idle()
                assertEquals(listOf(1), vm.state.value.topics.map(Topic::id))
                assertFalse(vm.state.value.hasUpdates)
                assertEquals(1L, vm.state.value.scrollToTopRequest)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun categoryPersonalPagesKeepScopeCacheAndGuestBoundaryWithoutGlobalCounts() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val paths = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path!!
                    return MockResponse().setBody(if (request.path == "/site.json") """{"categories":[]}"""
                        else """{"topic_list":{"topics":[{"id":7,"category_id":7}]}}""")
                }
            }
            server.start()
            val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), categoryId = 7,
                initialUser = Member(1, "one", "一", ""))
            suspend fun idle() = withContext(Dispatchers.Default) { withTimeout(5000) { vm.viewModelScope.coroutineContext.job.children.toList().joinAll() } }
            try {
                idle()
                assertEquals("latest", vm.state.value.filter)
                for (filter in listOf("posted", "read", "bookmarks")) {
                    vm.select(filter); idle()
                    assertEquals(filter, vm.state.value.filter)
                    assertTrue(paths.contains("/c/7/l/$filter.json"))
                }
                val count = paths.size
                vm.select("posted"); idle()
                assertEquals(count, paths.size)
                assertFalse(paths.any { it.contains("topic-tracking") })
                assertNull(vm.state.value.newCount)
                vm.setUser(null); idle()
                assertTrue(vm.pages.value.isEmpty())
                assertEquals("latest", vm.state.value.filter)
                val guestCount = paths.size
                listOf("posted", "read", "bookmarks").forEach(vm::select)
                idle()
                assertEquals(guestCount, paths.size)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun confirmedReadingInvalidatesOnlyReadDependentPagesAndKeepsScrollRequests() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val paths = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path!!
                    return MockResponse().setBody(when {
                        request.path == "/site.json" -> """{"categories":[{"id":1,"name":"分类"}]}"""
                        request.path!!.contains("topic-tracking") -> "[]"
                        else -> """{"topic_list":{"topics":[{"id":3,"title":"离线话题"}]}}"""
                    })
                }
            }
            server.start()
            val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), initialUser = Member(7, "seven", "七", ""))
            suspend fun idle() = withContext(Dispatchers.Default) { withTimeout(5000) { vm.viewModelScope.coroutineContext.job.children.toList().joinAll() } }
            try {
                idle()
                for (filter in listOf("new", "read", "bookmarks", "latest")) { vm.select(filter); idle() }
                val count = paths.count { it == "/latest.json" }
                vm.refreshReadListsIfRequested(1, 7); idle()
                assertFalse(vm.pages.value.containsKey("unread"))
                assertFalse(vm.pages.value.containsKey("new"))
                assertFalse(vm.pages.value.containsKey("read"))
                assertTrue(vm.pages.value.containsKey("bookmarks"))
                assertEquals(count, paths.count { it == "/latest.json" })
                assertEquals(0L, vm.state.value.scrollToTopRequest)
                vm.select("unread"); idle()
                val unreadCount = paths.count { it == "/unseen.json" }
                vm.refreshReadListsIfRequested(1, 7); idle()
                vm.refreshReadListsIfRequested(2, 8); idle()
                assertEquals(unreadCount, paths.count { it == "/unseen.json" })
                val beforeReading = vm.state.value
                vm.refreshReadListsIfRequested(2, 7)
                assertFalse("阅读确认不能触发返回后的刷新动画", vm.state.value.loading)
                idle()
                assertEquals("阅读确认不能重新请求当前未读列表", unreadCount, paths.count { it == "/unseen.json" })
                assertEquals(beforeReading.topics, vm.state.value.topics)
                assertEquals(beforeReading.next, vm.state.value.next)
                assertTrue("列表变化由用户主动刷新", vm.state.value.hasUpdates)
                assertEquals(0L, vm.state.value.scrollToTopRequest)
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }

    @Test fun revisitingPagesKeepsPaginationWithoutRequestsAndAccountSwitchClearsAllCaches() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val paths = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path!!
                    val body = when {
                        request.path == "/site.json" -> """{"categories":[{"id":1,"name":"分类"}]}"""
                        request.path!!.contains("topic-tracking") -> "[]"
                        request.path == "/latest.json" -> """{"topic_list":{"topics":[{"id":1,"title":"最新"}],"more_topics_url":"/latest.json?page=1"}}"""
                        request.path!!.contains("page=1") -> """{"topic_list":{"topics":[{"id":2,"title":"第二页"}]}}"""
                        else -> """{"topic_list":{"topics":[{"id":3,"title":"另一个筛选"}]}}"""
                    }
                    return MockResponse().setBody(body)
                }
            }
            server.start()
            val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))))
            suspend fun idle() = withContext(Dispatchers.Default) { withTimeout(5000) { vm.viewModelScope.coroutineContext.job.children.toList().joinAll() } }
            try {
                idle(); vm.more(); idle()
                vm.select("hot"); idle()
                val count = paths.size
                vm.select("latest"); idle()
                assertEquals(count, paths.size)
                assertEquals(listOf(1, 2), vm.state.value.topics.map(Topic::id))
                assertNull(vm.state.value.next)
                assertTrue(vm.pages.value.containsKey("hot"))
                vm.setUser(Member(7, "seven", "用户七", "")); idle()
                assertTrue(vm.pages.value.isEmpty())
                assertEquals("unread", vm.state.value.filter)
                vm.select("bookmarks"); idle()
                vm.setUser(null); idle()
                assertTrue(vm.pages.value.isEmpty())
                assertEquals("latest", vm.state.value.filter)
                assertFalse(paths.contains("/read.json"))
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
}
