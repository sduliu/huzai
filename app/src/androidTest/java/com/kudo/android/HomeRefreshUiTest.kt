package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.components.GlassNavigation
import com.kudo.android.ui.RefreshFeedOnResume
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.cancel
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeRefreshUiTest {
    @get:Rule val compose = createComposeRule()
    private fun initialState() = FeedState(loading = false, topics = (1..30).map {
        Topic(it, "更新测试话题 $it", "收到推送时继续阅读，点击刷新后回到顶部。", null, 1, emptyList(), 0, 0, 0, "", "")
    })

    @Test fun categoryReturnKeepsItsLoadedListWithoutRefreshing() = scopedReturn(tag = false)
    @Test fun tagReturnKeepsItsLoadedListWithoutRefreshing() = scopedReturn(tag = true)

    private fun scopedReturn(tag: Boolean) {
        val requests = AtomicInteger()
        val cookies = object : SessionCookies {
            override fun get(url: String) = ""
            override fun set(url: String, value: String) {}
        }
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath == "/site.json") """{"categories":[{"id":7,"name":"分类"}]}""" else {
                requests.incrementAndGet()
                """{"topic_list":{"topics":[${(1..30).joinToString { """{"id":$it,"title":"范围话题 $it"}""" }}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        lateinit var vm: FeedViewModel
        lateinit var nav: NavHostController
        compose.runOnUiThread {
            vm = FeedViewModel(CommunityRepository(DiscourseClient(cookies, "https://feed-fixture.test/".toHttpUrl(), http = http)),
                categoryId = if (tag) null else 7, tagSlug = if (tag) "Android" else null)
        }
        try {
            compose.setContent { KudoTheme(DisplaySettings()) {
                nav = rememberNavController()
                NavHost(nav, "feed") {
                    composable("feed") {
                        RefreshFeedOnResume(vm, null)
                        val state by vm.state.collectAsState()
                        if (tag) TagFeedScreen("Android", state, null, vm::select, vm::refresh, vm::more,
                            { nav.navigate("detail") }, {}, {}, {}, {}, TopicListStyle.TITLE, emptyMap())
                        else FeedScreen(state, null, vm::select, vm::refresh, vm::more,
                            { nav.navigate("detail") }, {}, {}, {}, {}, category = Category(7, "分类", "", "FFB003", "", 0),
                            listStyle = TopicListStyle.TITLE)
                    }
                    composable("detail") { Text("离线详情") }
                }
            } }
            compose.waitUntil(5000) { !vm.state.value.loading }
            compose.onNodeWithTag("feed-list-latest").performScrollToIndex(12)
            val before = requests.get()
            compose.onNodeWithText("范围话题 13").performClick()
            compose.onNodeWithText("离线详情").assertIsDisplayed()
            compose.runOnIdle { nav.popBackStack() }
            compose.onNodeWithText("范围话题 13").assertIsDisplayed()
            compose.runOnIdle {
                assertFalse("返回不能出现列表刷新", vm.state.value.loading)
                assertEquals("返回不能重新请求列表", before, requests.get())
            }
        } finally { compose.runOnUiThread { vm.viewModelScope.cancel() }; http.dispatcher.executorService.shutdown() }
    }

    @Test fun incomingPromptPreservesScrollAndClickReturnsToTopUntilRefreshSucceeds() {
        var state by mutableStateOf(initialState())
        var refreshes = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    FeedScreen(state, null, {}, {}, {}, {}, {}, {}, {}, {}, Modifier.layerBackdrop(backdrop))
                    GlassNavigation(0, {
                        if (it == 0 && state.hasUpdates) {
                            refreshes++
                            state = state.copy(scrollToTopRequest = state.scrollToTopRequest + 1)
                        }
                    }, backdrop, GlassMode.LIQUID, Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth(), state.hasUpdates)
                }
            }
        }
        val list = compose.onNodeWithTag("feed-list-latest")
        list.performTouchInput { swipeUp(durationMillis = 800) }
        val position = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(position > 0f)
        compose.runOnIdle { state = state.copy(hasUpdates = true) }
        compose.onAllNodesWithText("刷新").assertCountEquals(1)
        compose.onNodeWithText("首页").assertDoesNotExist()
        assertEquals(position, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.1f)
        capture("home-refresh-scrolled.png")
        compose.onNodeWithText("刷新").performTouchInput { click() }
        compose.onNodeWithText("更新测试话题 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Linux.do 社区 Logo").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText("刷新").assertIsDisplayed()
        capture("home-refresh-top.png")
        compose.runOnIdle { state = state.copy(hasUpdates = false) }
        compose.onNodeWithText("首页").assertIsDisplayed()
        compose.onNodeWithText("刷新").assertDoesNotExist()
    }

    @Test fun refreshEntryRemainsUniqueYellowAndClickableFromAnotherTabInAllMaterials() {
        var selected by mutableIntStateOf(2)
        var mode by mutableStateOf(GlassMode.LIQUID)
        var clicks = 0
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Text("首页有更新", Modifier.layerBackdrop(backdrop))
                    GlassNavigation(selected, { selected = it; clicks++ }, backdrop, mode,
                        Modifier.align(Alignment.BottomCenter).padding(20.dp).width(280.dp), homeHasUpdates = true)
                }
            }
        }
        for ((index, material) in GlassMode.entries.withIndex()) {
            compose.runOnIdle { mode = material; selected = 2 }
            compose.onAllNodesWithText("刷新").assertCountEquals(1)
            compose.onNodeWithText("刷新").performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(index + 1, clicks); assertEquals(0, selected) }
            val pixels = compose.onNodeWithText("刷新").captureToImage().toPixelMap()
            val yellow = (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y ->
                val pixel = pixels[x, y]
                pixel.red > 0.8f && pixel.green in 0.5f..0.8f && pixel.blue < 0.2f
            } }
            assertTrue("刷新文字应为 LogoYellow：$material", yellow > 10)
            capture("home-refresh-${material.name.lowercase()}-dark.png")
        }
    }

    @Test fun returningToSavedFeedDoesNotResetReadingPosition() {
        var visible by mutableStateOf(true)
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val holder = rememberSaveableStateHolder()
                if (visible) holder.SaveableStateProvider("feed") {
                    FeedScreen(initialState(), null, {}, {}, {}, {}, {}, {}, {}, {})
                } else Text("帖子详情")
            }
        }
        compose.onNodeWithTag("feed-list-latest").performTouchInput { swipeUp(durationMillis = 600) }
        compose.onNodeWithTag("feed-list-latest").performScrollToIndex(12)
        val collapsedTop = compose.onNodeWithTag("feed-list-latest").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("更新测试话题 13").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.onNodeWithText("帖子详情").assertIsDisplayed()
        compose.runOnIdle { visible = true }
        assertEquals("返回应保留折叠页头", collapsedTop,
            compose.onNodeWithTag("feed-list-latest").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("更新测试话题 13").assertIsDisplayed()
        compose.onNodeWithText("更新测试话题 1").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
