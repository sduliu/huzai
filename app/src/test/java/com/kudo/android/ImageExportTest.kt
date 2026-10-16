package com.kudo.android

import com.kudo.android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ImageExportTest {
    @get:Rule val folder = TemporaryFolder()
    private val gif = "GIF89a".toByteArray() + ByteArray(100) { it.toByte() }
    private fun client(block: (Request) -> ByteArray = { gif }) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(block(chain.request()).toResponseBody("application/octet-stream".toMediaType())).build()
    }.build()

    @Test fun cachedOriginalCapacityDoesNotRequestTheNetwork() = runTest {
        val requests = AtomicInteger()
        val downloader = ImageDownloader(client { requests.incrementAndGet(); gif }, folder.root,
            cachedSize = { url -> if (url.endsWith("original.webp")) 2401362L else null })
        assertEquals(2401362L, downloader.capacity(OriginalImage("https://image.test/original.webp")))
        assertEquals(0, requests.get())
        assertNull(downloader.cachedCapacity(OriginalImage("https://image.test/thumbnail.webp")))
    }

    @Test fun headCapacityUsesOriginalResponseLengthAndReusesItWithoutDownloading() = runTest {
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("HEAD", chain.request().method)
            assertEquals("/original.webp", chain.request().url.encodedPath)
            assertEquals("identity", chain.request().header("Accept-Encoding"))
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Length", "2401362").header("Content-Type", "image/webp")
                .body(byteArrayOf().toResponseBody("image/webp".toMediaType())).build()
        }.build()
        val downloader = ImageDownloader(http, folder.root)
        repeat(2) { assertEquals(2401362L, downloader.capacity(OriginalImage("https://image.test/original.webp"))) }
        assertEquals(1, requests.get())
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun missingInvalidAndNonImageResponseLengthsStayUnknown() = runTest {
        for ((type, length, code, encoding) in listOf(
            listOf("image/webp", "", "200", ""), listOf("image/webp", "0", "200", ""),
            listOf("image/webp", "-1", "200", ""), listOf("image/webp", "overflow", "200", ""),
            listOf("text/html", "1234", "200", ""), listOf("image/webp", "1234", "405", ""),
            listOf("image/webp", "1234", "200", "gzip"))) {
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code.toInt()).message("fixture")
                    .header("Content-Length", length).header("Content-Encoding", encoding).header("Content-Type", type)
                    .body(byteArrayOf().toResponseBody(type.toMediaType())).build()
            }.build()
            assertNull(ImageDownloader(http, folder.root).capacity(OriginalImage("https://image.test/original.webp")))
        }
        assertNull(ImageDownloader(client(), folder.root).capacity(OriginalImage("http://image.test/original.webp")))
    }

    @Test fun exportedOriginalBytesProvideCapacityEvenAfterTheTemporaryFileIsRemoved() = runTest {
        val requests = AtomicInteger()
        val downloader = ImageDownloader(client { requests.incrementAndGet(); gif }, folder.root)
        val original = OriginalImage("https://image.test/original.gif")
        downloader.download(original).file.delete()
        assertEquals(gif.size.toLong(), downloader.capacity(original))
        assertEquals(1, requests.get())
    }

    @Test fun originalAnimationBytesAndContentBasedExtensionArePreserved() = runTest {
        val image = ImageDownloader(client(), folder.root).download(OriginalImage("https://image.test/photo.jpg", "../../wrong.jpg"))
        assertEquals("image/gif", image.mimeType)
        assertTrue(image.displayName.endsWith(".gif"))
        assertFalse(image.displayName.contains('/'))
        assertArrayEquals(gif, image.file.readBytes())
    }

    @Test fun webpageAndOversizedResponsesDoNotLeaveFiles() = runTest {
        for (content in listOf("<html>verification</html>".toByteArray(), gif)) {
            try {
                ImageDownloader(client { content }, folder.root, maxBytes = 32).download(OriginalImage("https://image.test/test"))
                fail("Invalid response accepted")
            } catch (_: IOException) { }
            assertTrue(folder.root.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun partialBatchContinuesAfterDownloadAndStorageFailures() = runTest {
        val stored = mutableListOf<ByteArray>()
        val progress = mutableListOf<Int>()
        var attempts = 0
        val downloader = ImageDownloader(client { request -> if (request.url.encodedPath == "/2") "<html>fail</html>".toByteArray() else gif }, folder.root)
        val result = ImageSaver(downloader, ImageStore {
            attempts++
            if (attempts == 2) throw IOException("disk full")
            stored += it.file.readBytes()
        }).save((1..4).map { OriginalImage("https://image.test/$it") }) { count, _ -> progress += count }
        assertEquals(ImageSaveResult(2, 4), result)
        assertEquals(listOf(1, 2, 3, 4), progress)
        assertEquals(2, stored.size)
        stored.forEach { assertArrayEquals(gif, it) }
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationDoesNotPublishOrLeaveDownloadedFiles() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val downloader = ImageDownloader(client { entered.countDown(); release.await(5, TimeUnit.SECONDS); gif }, folder.root)
        var stored = false
        val task = launch(Dispatchers.Default) { ImageSaver(downloader, ImageStore { stored = true }).save(listOf(OriginalImage("https://image.test/test"))) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        task.cancel()
        release.countDown()
        task.join()
        assertFalse(stored)
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }
}
