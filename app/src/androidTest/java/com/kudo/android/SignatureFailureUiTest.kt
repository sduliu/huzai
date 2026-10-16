package com.kudo.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.Options
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.UserSignature
import kotlinx.coroutines.CompletableDeferred
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(coil3.annotation.DelicateCoilApi::class)
class SignatureFailureUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private val release = CompletableDeferred<Unit>()
    private val requests = AtomicInteger()
    private val post = Post(2, 2, Member(1, "gonethen", "作者", ""), "正文", "", 0, false, null, null, false,
        signature = "https://signature.test/broken.gif")

    @Before fun images() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        previous = SingletonImageLoader.get(instrumentation.targetContext)
        loader = ImageLoader.Builder(instrumentation.targetContext).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://signature.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch(): FetchResult {
                            requests.incrementAndGet(); release.await()
                            if (data.toString().contains("broken")) throw IOException("模拟签名图片不可访问")
                            return SourceFetchResult(ImageSource(Buffer().write(instrumentation.context.assets.open("avatars/static.png").use { it.readBytes() }), options.fileSystem), "image/png", DataSource.MEMORY)
                        }
                    }
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
    }
    @After fun restore() { release.complete(Unit); SingletonImageLoader.setUnsafe(previous); loader.shutdown() }

    @Test fun failedImageOnlySignatureRemovesTheEntireRegionAndItsDivider() {
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = true)) {
            Column(Modifier.fillMaxWidth()) { UserSignature(post, {}) }
        } }
        compose.waitUntil { requests.get() == 1 }
        compose.onNodeWithTag("user-signature-2").assertExists()
        release.complete(Unit)
        compose.waitUntil(3000) { compose.onAllNodesWithTag("user-signature-2").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("签名图片").assertDoesNotExist()
        assertEquals(1, requests.get())
    }

    @Test fun failedPictureKeepsRealTextAndOtherValidPicturesAndDisabledMakesNoRequest() {
        var enabled by mutableStateOf(false)
        val mixed = post.copy(signature = "<p>真实文字签名</p><img src='https://signature.test/broken.gif'><img src='https://signature.test/valid.png'>")
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = enabled)) {
            Column(Modifier.fillMaxWidth()) { UserSignature(mixed, {}) }
        } }
        compose.onNodeWithTag("user-signature-2").assertDoesNotExist()
        assertEquals(0, requests.get())
        compose.runOnIdle { enabled = true }
        compose.waitUntil { requests.get() == 2 }; release.complete(Unit)
        compose.waitUntil(3000) { compose.onAllNodesWithText("签名图片").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("真实文字签名").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("用户签名图片").assertCountEquals(1)
    }
}
