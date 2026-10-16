package com.kudo.android.media

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class OriginalImage(val url: String, val filename: String = "")
internal data class OriginalImageFile(val file: File, val mimeType: String, val displayName: String)
internal data class ImageSaveResult(val saved: Int, val total: Int)
internal fun interface ImageStore { fun save(image: OriginalImageFile) }

/** Exports the original bytes, never the sampled/decoded preview. */
internal class ImageDownloader(private val http: Call.Factory, private val directory: File,
    private val maxBytes: Long = 64L * 1024 * 1024, private val cachedSize: (String) -> Long? = { null }) {
    private val capacities = ConcurrentHashMap<String, Long>()

    private fun rememberCapacity(url: String, bytes: Long): Long {
        if (capacities.size < 256 || capacities.containsKey(url)) capacities[url] = bytes
        return bytes
    }

    suspend fun cachedCapacity(image: OriginalImage): Long? = withContext(Dispatchers.IO) {
        runCatching { cachedSize(image.url) }.getOrNull()?.takeIf { it > 0 }
            ?.let { rememberCapacity(image.url, it) } ?: capacities[image.url]
    }

    suspend fun capacity(image: OriginalImage): Long? = withContext(Dispatchers.IO) {
        val url = image.url.toHttpUrlOrNull()
        if (url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return@withContext null
        cachedCapacity(image)?.let { return@withContext it }
        try {
            withTimeoutOrNull(5000) {
                suspendCancellableCoroutine<Long?> { continuation ->
                    val call = http.newCall(Request.Builder().url(url).head().header("Accept-Encoding", "identity").build())
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            val bytes = response.use {
                                val type = it.header("Content-Type").orEmpty().substringBefore(';').trim()
                                val encoding = it.header("Content-Encoding").orEmpty()
                                if (it.code != 200 || !type.startsWith("image/", ignoreCase = true) ||
                                    (encoding.isNotBlank() && !encoding.equals("identity", ignoreCase = true))) null
                                else it.header("Content-Length")?.toLongOrNull()?.takeIf { value -> value > 0 }
                            }
                            if (continuation.isActive) continuation.resume(bytes?.let { rememberCapacity(image.url, it) })
                        }
                    })
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    suspend fun download(image: OriginalImage): OriginalImageFile = withContext(Dispatchers.IO) {
        val url = image.url.toHttpUrlOrNull()
        require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "图片地址无效" }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建图片缓存")
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach(File::delete)
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(Request.Builder().url(url).build())
            val partial = AtomicReference<File?>(null)
            continuation.invokeOnCancellation { call.cancel(); partial.get()?.delete() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) throw IOException("图片下载失败（${it.code}）")
                            val body = it.body ?: throw IOException("图片内容为空")
                            if (body.contentLength() > maxBytes) throw IOException("图片超过 64 MB")
                            body.byteStream().use { input ->
                                val header = ByteArray(32)
                                var headSize = 0
                                while (headSize < header.size) {
                                    val read = input.read(header, headSize, header.size - headSize)
                                    if (read < 0) break
                                    headSize += read
                                }
                                val format = imageFormat(header.copyOf(headSize)) ?: throw IOException("返回内容不是支持的图片")
                                val file = File.createTempFile("image-", ".${format.second}", directory)
                                partial.set(file)
                                if (!continuation.isActive) throw IOException("下载已取消")
                                file.outputStream().use { output ->
                                    output.write(header, 0, headSize)
                                    var total = headSize.toLong()
                                    val buffer = ByteArray(32 * 1024)
                                    while (true) {
                                        if (!continuation.isActive) throw IOException("下载已取消")
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        total += read
                                        if (total > maxBytes) throw IOException("图片超过 64 MB")
                                        output.write(buffer, 0, read)
                                    }
                                    if (total > maxBytes) throw IOException("图片超过 64 MB")
                                }
                                rememberCapacity(image.url, file.length())
                                val name = exportFilename(image.filename, format.second)
                                continuation.resume(OriginalImageFile(file, format.first, name)) { _, value, _ -> value.file.delete() }
                            }
                        }
                    } catch (error: Exception) {
                        partial.get()?.delete()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}

internal class ImageSaver(private val downloader: ImageDownloader, private val store: ImageStore) {
    suspend fun save(images: List<OriginalImage>, progress: (Int, Int) -> Unit = { _, _ -> }): ImageSaveResult {
        var saved = 0
        images.forEachIndexed { index, image ->
            currentCoroutineContext().ensureActive()
            try {
                val downloaded = downloader.download(image)
                try {
                    withContext(Dispatchers.IO) { ensureActive(); store.save(downloaded) }
                    saved++
                } finally { downloaded.file.delete() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Continue the batch; report the actual success count. */ }
            progress(index + 1, images.size)
        }
        return ImageSaveResult(saved, images.size)
    }
}

internal fun exportFilename(proposed: String, extension: String): String {
    val stem = proposed.substringBeforeLast('.', proposed).replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim(' ', '.').take(96).ifBlank { "酷DO_${System.currentTimeMillis()}" }
    return "$stem.$extension"
}

internal fun imageFormat(header: ByteArray): Pair<String, String>? {
    fun ascii(start: Int, count: Int) = if (header.size >= start + count) String(header, start, count, Charsets.US_ASCII) else ""
    fun starts(vararg values: Int) = header.size >= values.size && values.indices.all { header[it].toInt() and 255 == values[it] }
    return when {
        starts(0xFF, 0xD8, 0xFF) -> "image/jpeg" to "jpg"
        starts(137, 80, 78, 71, 13, 10, 26, 10) -> "image/png" to "png"
        ascii(0, 6) in listOf("GIF87a", "GIF89a") -> "image/gif" to "gif"
        ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "image/webp" to "webp"
        ascii(0, 2) == "BM" -> "image/bmp" to "bmp"
        ascii(4, 4) == "ftyp" && ascii(8, 4) in listOf("avif", "avis") -> "image/avif" to "avif"
        ascii(4, 4) == "ftyp" && ascii(8, 4) in listOf("heic", "heix", "hevc", "hevx") -> "image/heic" to "heic"
        else -> null
    }
}
