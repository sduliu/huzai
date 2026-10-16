package com.kudo.android

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

class TopicPresenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "author", "楼主", "", avatarDetailsLoaded = true)
    private val first = Post(10, 1, author, "<p>正文</p>", "", 0, false, null, null, true)
    private val detail = TopicDetail(3, "正在回复头像布局", 0, listOf(first), listOf(10), false)
    private val users = (2..6).map { Member(it, "user$it", "用户$it", "https://avatar.test/$it.png",
        flair = AvatarFlair("身份角标", "https://avatar.test/flair.png"), status = UserStatus("smile", "状态角标")) }
    private lateinit var originalImages: ImageLoader
    private lateinit var fixtureImages: ImageLoader
    private val loadedImages = CopyOnWriteArraySet<String>()

    @Before fun imageFixtures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        originalImages = SingletonImageLoader.get(context)
        fixtureImages = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://avatar.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch(): ImageFetchResult {
                            val id = data.toString().substringAfterLast('/').substringBefore('.').toIntOrNull() ?: 0
                            val colors = listOf(0xFF4E79A7.toInt(), 0xFF59A14F.toInt(), 0xFFE15759.toInt())
                            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(colors[(id + 1) % colors.size])
                            return ImageFetchResult(BitmapDrawable(context.resources, bitmap).asImage(shareable = false), false, DataSource.MEMORY)
                        }
                    }
                }
            })
        }.eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loadedImages += request.data.toString() }
        }).build()
        SingletonImageLoader.setUnsafe(fixtureImages)
    }

    @After fun restoreImages() {
        SingletonImageLoader.setUnsafe(originalImages)
        fixtureImages.shutdown()
    }

    @Test fun oneToFivePeopleReplaceTheTextWithAtMostThreeOverlappingPlainAvatars() {
        var replying by mutableStateOf(emptyList<Member>())
        var clicks = 0
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
            Bar(replying, reply = { clicks++ })
        } }
        compose.onNodeWithText("写回复", useUnmergedTree = true).assertIsDisplayed()
        val originalButton = compose.onNodeWithTag("topic-reply").fetchSemanticsNode().boundsInRoot
        val originalCounter = compose.onNodeWithTag("topic-floor-index").fetchSemanticsNode().boundsInRoot
        (1..5).forEach { count ->
            compose.runOnIdle { replying = users.take(count) }
            val button = compose.onNodeWithTag("topic-reply").fetchSemanticsNode().boundsInRoot
            val counter = compose.onNodeWithTag("topic-floor-index").fetchSemanticsNode().boundsInRoot
            assertEquals("头像切换只隐藏文字，按钮宽度不变：$count 人", originalButton.width, button.width, 1f)
            assertEquals("头像不能挤动楼层按钮：$count 人", originalCounter.left, counter.left, 1f)
            compose.onNodeWithText("写回复", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("topic-reply").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${count}人正在回复"))
            users.forEachIndexed { index, member ->
                val node = compose.onNodeWithTag("topic-replying-avatar-${member.id}", useUnmergedTree = true)
                if (index < minOf(count, 3)) node.assertIsDisplayed() else node.assertDoesNotExist()
            }
            if (count > 3) compose.onNodeWithTag("topic-replying-overflow", useUnmergedTree = true).assertTextEquals("+${count - 3}")
            else compose.onNodeWithTag("topic-replying-overflow", useUnmergedTree = true).assertDoesNotExist()
            compose.onAllNodes(hasContentDescription("的资质：", substring = true), useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription("的状态：", substring = true), useUnmergedTree = true).assertCountEquals(0)
        }
        val one = compose.onNodeWithTag("topic-replying-avatar-2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val two = compose.onNodeWithTag("topic-replying-avatar-3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val three = compose.onNodeWithTag("topic-replying-avatar-4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(one.left < two.left && two.left < one.right)
        assertTrue(two.left < three.left && three.left < two.right)
        compose.waitUntil(5000) { (2..4).all { "https://avatar.test/$it.png" in loadedImages } }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val firstAvatar = pixels[(one.left + one.width * .25f).toInt(), one.center.y.toInt()]
        assertEquals("头像图片完整显示，不能被按钮内容裁掉", 0x4E / 255f, firstAvatar.red, .04f)
        assertFalse(loadedImages.any { it.contains("flair") })
        capture("topic-presence-five-light.png")
        compose.onNodeWithTag("topic-reply").performClick()
        compose.runOnIdle { assertEquals(1, clicks); replying = emptyList() }
        compose.onNodeWithText("写回复", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("topic-replying-avatars", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(originalButton.width, compose.onNodeWithTag("topic-reply").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test fun narrowDarkLargeTextKeepsAllButtonsAndTheOverflowVisible() {
        var mode by mutableStateOf(GlassMode.TRANSLUCENT)
        var replying by mutableStateOf(emptyList<Member>())
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Box(Modifier.width(280.dp).fillMaxHeight()) { Bar(replying, mode, highest = 120000) }
        } }
        val originalButton = compose.onNodeWithTag("topic-reply").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { replying = users }
        compose.waitUntil(5000) { (2..4).all { "https://avatar.test/$it.png" in loadedImages } }
        GlassMode.entries.forEach { glass ->
            compose.runOnIdle { mode = glass }
            val reply = compose.onNodeWithTag("topic-reply").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("窄屏头像切换也保持原按钮宽度", originalButton.width, reply.width, 1f)
            val counter = compose.onNodeWithTag("topic-floor-index").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("topic-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(reply.right < counter.left && counter.right < actions.left)
            compose.onNodeWithTag("topic-replying-overflow", useUnmergedTree = true).assertIsDisplayed().assertTextEquals("+2")
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("1 / 120000").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow)
            capture("topic-presence-five-${glass.name.lowercase()}-narrow-dark.png")
        }
    }

    @Test fun closedTopicsRemainDisabledWhileShowingWhoIsReplying() {
        var clicks = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            Bar(users.take(1), closed = true, reply = { clicks++ })
        } }
        compose.onNodeWithTag("topic-reply").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    /** Explicitly enabled read-only check; does not publish presence or reading timings. */
    @Test fun livePresenceSnapshotAndTopicPollAreAvailableToTheInstalledAccount() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveReadOnlyProbe") == "true")
        val topicId = arguments.getString("liveTopicId")?.toInt() ?: 2977660
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var app: AppContainer
        instrumentation.runOnMainSync { app = AppContainer(instrumentation.targetContext) }
        withTimeout(45_000) {
            val user = withContext(Dispatchers.IO) { app.repository.currentUser() }
            assertNotNull(user)
            val name = "/discourse-presence/reply/$topicId"
            val snapshot = withContext(Dispatchers.IO) { app.client.get("/presence/get", mapOf("channels[]" to name)) }.optJSONObject(name)
            assertNotNull("Presence 频道应有权限", snapshot)
            assertNotNull(snapshot!!.optJSONArray("users"))
            val event = DiscourseMessageBus(app.client, userAgent = app.userAgent, route = app.network::client)
                .observeActivity(user, topicId).first { it.checkReplies }
            assertNotNull(event.replyingUsers)
            assertFalse(event.replyingUsers!!.any { it.id == user!!.id })
            println("Live presence: topic=$topicId, users=${event.replyingUsers.size}, combinedPoll=connected")
        }
    }

    @Composable private fun Bar(replying: List<Member>, glass: GlassMode = GlassMode.TRANSLUCENT,
        highest: Int = 1, closed: Boolean = false, reply: () -> Unit = {}) {
        Box(Modifier.fillMaxSize()) {
            TopicBottomBar(TopicState(detail.copy(highestPostNumber = highest, closed = closed), false,
                viewerId = 1, replyingUsers = replying), rememberLayerBackdrop(), glass, true, reply, {}, {}, {}, {},
                Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
