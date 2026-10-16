package com.kudo.android

import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.UserSignature
import com.kudo.android.ui.topic.SignatureImageWebView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class SvgSignatureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val views = CopyOnWriteArrayList<FixtureWebView>()
    private val requests = CopyOnWriteArrayList<String>()
    private var fixtureWidth = 380
    private var fixtureHeight = 100
    private val sample = Post(60, 1, Member(1, "svg", "SVG", "", avatarDetailsLoaded = true), "正文", "", 0, false, null, null, false,
        signature = "https://svg.test/signature.svg?version=1&theme=light")

    private fun create(context: Context): WebView = FixtureWebView(context).also { views += it }

    @Test fun svgWithoutHtmlSizeFillsWidthAndUsesItsOwnAspectRatio() {
        fixtureWidth = 320; fixtureHeight = 240
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = true)) {
            Column(Modifier.width(280.dp).testTag("signature-body")) { UserSignature(sample, {}, ::create) }
        } }
        compose.waitUntil(10_000) { requests.isNotEmpty() }
        compose.onNodeWithTag("svg-signature-content").assertIsDisplayed()
        compose.waitUntil(5000) {
            val bounds = compose.onNodeWithContentDescription("用户签名图片").fetchSemanticsNode().boundsInRoot
            kotlin.math.abs(bounds.height - bounds.width * .75f) < 4f
        }
        val image = compose.onNodeWithContentDescription("用户签名图片").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("signature-body").fetchSemanticsNode().boundsInRoot
        assertEquals(body.width, image.width, 2f)
        assertEquals("SVG 应保持4:3真实比例", image.width * .75f, image.height, 4f)
    }

    @Test fun failedSvgRemovesTheSignatureAndReleasesItsWebView() {
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = true)) {
            Column(Modifier.fillMaxWidth()) { UserSignature(sample, {}, ::create) }
        } }
        compose.waitUntil(5000) { views.isNotEmpty() }
        compose.runOnIdle {
            val view = views.last()
            view.webViewClient.onReceivedHttpError(view, request(sample.signature),
                WebResourceResponse("image/svg+xml", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream()))
        }
        compose.onNodeWithTag("user-signature-60").assertDoesNotExist()
        compose.waitUntil(3000) { views.all { it.released } }
    }

    @Test fun cssAnimatedSvgMakesNoRequestsWhenDisabledRendersFramesAndReleasesWhenDisabled() {
        var settings by mutableStateOf(DisplaySettings(showSignatures = false))
        compose.setContent { KudoTheme(settings) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) { UserSignature(sample, {}, ::create) }
        } }
        compose.onNodeWithTag("user-signature-60").assertDoesNotExist()
        assertTrue(views.isEmpty())
        assertTrue(requests.isEmpty())
        compose.runOnIdle { settings = settings.copy(showSignatures = true) }
        val colors = mutableSetOf<Int>()
        compose.waitUntil(10_000) {
            val pixels = compose.onNodeWithContentDescription("用户签名图片").captureToImage().toPixelMap()
            val p = pixels[pixels.width / 2, pixels.height / 2]
            if (p.red > p.blue + 0.2f) colors += 1
            if (p.blue > p.red + 0.2f) colors += 2
            colors.size == 2
        }
        compose.runOnIdle {
            val view = views.single()
            assertFalse(view.settings.javaScriptEnabled)
            assertFalse(view.settings.domStorageEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(view))
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request("https://linux.do/t/topic/1", true)))
            assertNotNull(view.webViewClient.shouldInterceptRequest(view, request("file:///data/private.svg")))
            assertNotNull(view.webViewClient.shouldInterceptRequest(view, request("https://unrelated.test/extra")))
            assertEquals(listOf(sample.signature), requests.toList())
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(views.any { it.pauses > 0 })
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("svg-signature-content").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(showSignatures = false) }
        compose.onNodeWithTag("user-signature-60").assertDoesNotExist()
        compose.waitUntil(5_000) { views.all { it.released } }
    }

    @Test fun draggingOnSvgScrollsTheNativePageAndOffscreenViewsAreReleased() {
        lateinit var observedScroll: ScrollState
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = true)) {
            val scroll = rememberScrollState()
            SideEffect { observedScroll = scroll }
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll).testTag("signature-scroll")) {
                Spacer(Modifier.height(200.dp))
                UserSignature(sample, {}, ::create)
                repeat(25) { Text("原生正文第 $it 行", Modifier.padding(16.dp)) }
            }
        } }
        compose.waitUntil(10_000) { requests.isNotEmpty() }
        compose.onNodeWithContentDescription("用户签名图片").performTouchInput {
            swipe(Offset(width / 2f, height * 0.8f), Offset(width / 2f, -height.toFloat()), durationMillis = 500)
        }
        compose.runOnIdle { assertTrue("SVG 不能拦截正文滚动", observedScroll.value > 0) }
        compose.onNodeWithTag("signature-scroll").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitUntil(5_000) { views.all { it.released } }
    }

    private fun request(url: String, main: Boolean = false) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private inner class FixtureWebView(context: Context) : SignatureImageWebView(context) {
        var released = false
        var pauses = 0
        override fun onPause() { pauses++; super.onPause() }
        override fun destroy() { released = true; super.destroy() }
        override fun setWebViewClient(client: WebViewClient) {
            super.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = client.shouldOverrideUrlLoading(view, request)
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.toString() == sample.signature) {
                        requests += request.url.toString()
                        return WebResourceResponse("image/svg+xml", "UTF-8", """<svg xmlns="http://www.w3.org/2000/svg" width="$fixtureWidth" height="$fixtureHeight" viewBox="0 0 $fixtureWidth $fixtureHeight">
                            <style>@keyframes frames{0%,49%{fill:#e53935}50%,100%{fill:#1e88e5}}rect{animation:frames .6s steps(1,end) infinite}</style>
                            <rect width="$fixtureWidth" height="$fixtureHeight"/><script>fetch('https://svg.test/script-ran')</script>
                            </svg>""".byteInputStream())
                    }
                    return client.shouldInterceptRequest(view, request)
                }
                override fun onPageFinished(view: WebView, url: String) = client.onPageFinished(view, url)
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) = client.onReceivedError(view, request, error)
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) = client.onReceivedHttpError(view, request, response)
            })
        }
    }

    @Test fun skyrockSvgSignatureDisplaysItsTerminalArtwork() {
        val member = Member(1, "skyrock", "签名示例", "", avatarDetailsLoaded = true)
        val post = Post(50, 1, member, "正文", "", 0, false, null, null, false,
            signature = "https://cdn3.ldstatic.com/original/4X/2/5/4/2547b34bf920c67adbb09b38fdcde10042ab0345.svg")
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = true)) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                Text("SVG 个性签名")
                UserSignature(post, {})
            }
        } }
        try {
            compose.waitUntil(20_000) {
                val pixels = compose.onNodeWithContentDescription("用户签名图片").captureToImage().toPixelMap()
                // The real signature is a dark terminal containing green and blue artwork.
                var colored = 0
                for (x in 0 until pixels.width step 3) for (y in 0 until pixels.height step 3) {
                    val p = pixels[x, y]
                    if (p.green > p.red + 0.12f || p.blue > p.red + 0.12f) colored++
                }
                colored > 60
            }
            // Let the real signature finish its 2.37-second name typing before visual review.
            android.os.SystemClock.sleep(3_000)
        } finally {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "svg-signature-live.png")
                .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
