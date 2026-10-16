package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.ObserveTopicUpdates
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class TopicLiveRepliesUiTest {
    @get:Rule val compose = createComposeRule()

    /** Opt-in read-only probe; never posts replies or reports reading timings. */
    @Test fun liveTopicSubscriptionConnectsWithTheInstalledSession() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveReadOnlyProbe") == "true")
        val topicId = arguments.getString("liveTopicId")?.toInt() ?: 2977660
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var app: AppContainer
        instrumentation.runOnMainSync { app = AppContainer(instrumentation.targetContext) }
        withTimeout(45_000) {
            val user = withContext(Dispatchers.IO) { app.repository.currentUser() }
            val detail = withContext(Dispatchers.IO) { app.repository.detail(topicId) }
            assertEquals(topicId, detail.id)
            assertTrue(detail.stream.isNotEmpty())
            DiscourseMessageBus(app.client, userAgent = app.userAgent, route = app.network::client)
                .observeTopic(user, topicId).first()
            println("Live topic: authenticated=${user != null}, topic=$topicId, floors=${detail.stream.size}, subscription=connected")
        }
    }

    @Test fun appendingRepliesKeepsTheVisibleFloorAndClosingThePageStopsTheSubscription() {
        Fixture().use { f ->
            var visible by mutableStateOf(true)
            compose.setContent { if (visible) Screen(f) else Text("话题列表") }
            ready(f)
            compose.onNodeWithTag("topic-posts").performScrollToNode(hasTestTag("post-10"))
            val before = compose.onNodeWithTag("post-10").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            capture("topic-live-before.png")
            compose.runOnIdle { f.total.set(21); f.hints.tryEmit(Unit) }
            compose.waitUntil(5000) { f.vm.state.value.detail?.posts?.size == 21 }
            val after = compose.onNodeWithTag("post-10").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("追加不能移动正在阅读的楼层", before.top, after.top, 2f)
            capture("topic-live-after.png")
            compose.onNodeWithTag("topic-posts").performScrollToNode(hasTestTag("post-21"))
            compose.onNodeWithText("回复第21楼").assertIsDisplayed()
            compose.runOnIdle { visible = false }
            compose.waitUntil(3000) { f.active.get() == 0 }
            compose.onNodeWithText("话题列表").assertIsDisplayed()
            assertTrue(f.vm.state.value.readNumbers.isEmpty())
        }
    }

    @Test fun pausedPagesStopChecksAndResumeWithoutSkippingUnloadedFloors() {
        Fixture(initialTotal = 100).use { f ->
            lateinit var owner: ScreenOwner
            compose.runOnUiThread { owner = ScreenOwner() }
            compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { Screen(f) } }
            ready(f)
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
            compose.waitUntil(3000) { f.active.get() == 0 }
            val pausedChecks = f.checks.get()
            compose.runOnIdle { f.total.set(101); f.hints.tryEmit(Unit) }
            compose.runOnIdle { assertEquals(100, f.vm.state.value.detail!!.highestPostNumber) }
            assertEquals(pausedChecks, f.checks.get())
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
            compose.waitUntil(5000) { f.vm.state.value.detail?.highestPostNumber == 101 }
            assertEquals((1..20).toList(), f.vm.state.value.detail!!.posts.map(Post::number))
            assertTrue(f.postRequests.isEmpty())
            compose.onNodeWithTag("topic-posts").performScrollToIndex(21)
            compose.waitUntil(5000) { f.vm.state.value.detail?.posts?.size ?: 0 > 20 }
            assertEquals((21..40).toList(), f.postRequests.first())
            assertFalse(f.vm.state.value.detail!!.posts.any { it.number == 101 })
        }
    }

    private fun ready(f: Fixture) {
        compose.waitUntil(5000) { !f.vm.state.value.loading && f.checks.get() >= 2 && f.active.get() == 1 }
        compose.waitForIdle()
    }

    @Composable private fun Screen(f: Fixture) {
        val state by f.vm.state.collectAsState()
        KudoTheme(DisplaySettings(theme = ThemeMode.DARK)) {
            ObserveTopicUpdates(f.vm)
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, f.vm::refresh, f.vm::more,
                {}, {}, {}, {}, {}, {}, {}, {}, readingEnabled = false, jumpTo = f.vm::jumpTo, previous = f.vm::previous)
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class ScreenOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private class Fixture(initialTotal: Int = 20) : AutoCloseable {
        val total = AtomicInteger(initialTotal)
        val checks = AtomicInteger()
        val active = AtomicInteger()
        val hints = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
        val postRequests = CopyOnWriteArrayList<List<Int>>()
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/t/5.json" -> {
                    checks.incrementAndGet()
                    JSONObject().put("id", 5).put("title", "实时回复测试").put("highest_post_number", total.get())
                        .put("post_stream", JSONObject().put("stream", JSONArray((1..total.get()).toList()))
                            .put("posts", JSONArray((1..20).map(::post)))).toString()
                }
                "/t/5/posts.json" -> {
                    val ids = request.url.queryParameterValues("post_ids[]").map { it!!.toInt() }
                    postRequests += ids
                    JSONObject().put("post_stream", JSONObject().put("posts", JSONArray(ids.map(::post)))).toString()
                }
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toResponseBody()).build()
        }.build()
        val vm: TopicViewModel
        init {
            var created: TopicViewModel? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                created = TopicViewModel(CommunityRepository(DiscourseClient(NoFixtureCookies, http = http)), 5, viewerId = 1,
                    updates = TopicUpdates { _, _ -> hints.onStart { active.incrementAndGet() }.onCompletion { active.decrementAndGet() } },
                    updateIntervalMillis = 100)
            }
            vm = checkNotNull(created)
        }
        private fun post(number: Int) = JSONObject().put("id", number).put("post_number", number)
            .put("user_id", 1).put("username", "fixture").put("name", "离线作者")
            .put("cooked", "<p>回复第${number}楼</p>")
        override fun close() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.viewModelScope.cancel() }
        }
        private object NoFixtureCookies : SessionCookies {
            override fun get(url: String) = ""
            override fun set(url: String, cookie: String) = Unit
        }
    }
}
