package com.kudo.android.media

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.github.penfeizhou.animation.avif.AVIFDrawable
import com.github.penfeizhou.animation.avif.decode.AVIFDecoder
import com.github.penfeizhou.animation.loader.StreamLoader
import kotlinx.coroutines.runInterruptible
import okio.BufferedSource
import org.aomedia.avif.android.AvifDecoder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Claim animated AVIF by its container brand, including files with misleading extensions. */
internal fun isAnimatedAvif(source: BufferedSource): Boolean = source.peek().use { input ->
    if (!input.request(16)) return false
    val size = input.readInt()
    if (size !in 16..256 || size % 4 != 0 || input.readUtf8(4) != "ftyp" || !input.request((size - 8).toLong())) return false
    val major = input.readUtf8(4)
    input.skip(4)
    var animated = major == "avis"
    repeat((size - 16) / 4) { if (input.readUtf8(4) == "avis") animated = true }
    animated
}

internal class AvifAnimationDecoder(private val source: ImageSource) : Decoder {
    override suspend fun decode(): DecodeResult? = runInterruptible {
        val input = source.source()
        // Do not consume an oversized file: leave the existing static fallback available.
        if (input.request(16L * 1024 * 1024 + 1)) return@runInterruptible null
        val bytes = input.readByteArray()
        val direct = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
        val metadata = AvifDecoder.create(direct) ?: throw IOException("无法读取 AVIF 动画")
        try {
            val w = metadata.width; val h = metadata.height
            if (w !in 1..4096 || h !in 1..4096 || w.toLong() * h > 4_194_304 || metadata.frameCount !in 1..1000) {
                throw IOException("AVIF 动画尺寸过大")
            }
        } finally { metadata.release() }
        val loader = object : StreamLoader() {
            override fun getInputStream() = ByteArrayInputStream(bytes)
        }
        // Upstream AVIF always renders at full size, but the inherited setDesiredSize
        // shrinks its pixel buffer. Keep both at 1x to avoid transparent frames.
        val decoder = object : AVIFDecoder(loader, null) {
            override fun setDesiredSize(width: Int, height: Int) = 1
        }
        val drawable = AVIFDrawable(decoder).apply { setAutoPlay(false) }
        DecodeResult(drawable.asImage(shareable = false), isSampled = false)
    }
    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
            if (isAnimatedAvif(result.source.source())) AvifAnimationDecoder(result.source) else null
    }
}
