package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.Options
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.UserSignature
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(coil3.annotation.DelicateCoilApi::class)
class SignatureSizingUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private val decoded = mutableSetOf<String>()

    @Before fun images() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://signature-size.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch(): FetchResult {
                            val (width, height) = when {
                                data.toString().contains("wide") -> 840 to 140
                                data.toString().contains("tall") -> 140 to 280
                                else -> 320 to 320
                            }
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.rgb(255, 180, 0))
                            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                            bitmap.recycle()
                            return SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY)
                        }
                    }
                }
            })
        }.eventListener(object : coil3.EventListener() {
            override fun onSuccess(request: coil3.request.ImageRequest, result: coil3.request.SuccessResult) {
                synchronized(decoded) { decoded += request.data.toString() }
            }
        }).build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After fun restore() { SingletonImageLoader.setUnsafe(previous); loader.shutdown() }

    @Test fun unknownBitmapSizesFillTheBodyAndUseTheirNaturalHeight() {
        var signature by mutableStateOf("https://signature-size.test/wide.png")
        val post = Post(70, 1, Member(1, "signature", "签名", ""), "正文", "", 0, false, null, null, false)
        compose.setContent { KudoTheme(DisplaySettings(showSignatures = true)) {
            Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).testTag("signature-body")) {
                UserSignature(post.copy(signature = signature), {})
            }
        } }
        for ((file, ratio) in listOf("wide" to 6f, "square" to 1f, "tall" to .5f)) {
            val url = "https://signature-size.test/$file.png"
            compose.runOnIdle { signature = url }
            compose.waitUntil(5000) { synchronized(decoded) { url in decoded } }
            compose.waitForIdle()
            val image = compose.onNodeWithContentDescription("用户签名图片").fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("signature-body").fetchSemanticsNode().boundsInRoot
            assertEquals("$file 应占满正文宽度", body.width, image.width, 2f)
            assertEquals("$file 应按真实比例决定高度", image.width / ratio, image.height, 3f)
            val bitmap = compose.onNodeWithTag("signature-body").captureToImage().asAndroidBitmap()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
            File(directory, "signature-natural-$file.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
