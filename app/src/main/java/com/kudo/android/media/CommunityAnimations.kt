package com.kudo.android.media

import android.os.Build
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Scale
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.apng.decode.APNGDecoder
import com.github.penfeizhou.animation.loader.Loader
import com.github.penfeizhou.animation.loader.StreamLoader
import com.github.penfeizhou.animation.webp.WebPDrawable
import com.github.penfeizhou.animation.webp.decode.WebPDecoder
import kotlinx.coroutines.runInterruptible
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

fun ComponentRegistry.Builder.communityAnimations(legacyWebP: Boolean = Build.VERSION.SDK_INT < 28): ComponentRegistry.Builder = apply {
    add(AvifAnimationDecoder.Factory())
    add(FrameAnimationDecoder.Factory(legacyWebP))
    if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
}

/** APNG needs a separate decoder; Android's ImageDecoder only animates GIF/WebP/HEIF. */
internal class FrameAnimationDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val header: AnimationHeader,
) : Decoder {
    override suspend fun decode(): DecodeResult? = runInterruptible {
        val buffered = source.source()
        // Leave the source untouched so oversized animations can use the static decoder.
        if (buffered.request(MAX_BYTES + 1)) return@runInterruptible null
        val bytes = buffered.readByteArray()
        val loader = object : StreamLoader() {
            override fun getInputStream() = ByteArrayInputStream(bytes)
        }
        val width = ((options.size.width as? Dimension.Pixels)?.px ?: header.width).coerceAtLeast(1)
        val height = ((options.size.height as? Dimension.Pixels)?.px ?: header.height).coerceAtLeast(1)
        val ratio = if (options.scale == Scale.FILL) min(header.width / width, header.height / height)
            else max(header.width / width, header.height / height)
        var sample = 1
        while (sample <= ratio / 2) sample *= 2
        val drawable = if (header.png) {
            val decoder = SampledApngDecoder(loader, sample)
            requireFrames(decoder.bounds.width(), decoder.bounds.height(), decoder.frameCount)
            APNGDrawable(decoder)
        } else {
            val decoder = SampledWebPDecoder(loader, sample)
            requireFrames(decoder.bounds.width(), decoder.bounds.height(), decoder.frameCount)
            WebPDrawable(decoder)
        }
        drawable.setAutoPlay(false)
        DecodeResult(drawable.asImage(shareable = false), isSampled = sample > 1)
    }

    private fun requireFrames(width: Int, height: Int, frames: Int) {
        if (width <= 0 || height <= 0 || frames !in 1..1000) throw IOException("无效的头像动画")
    }

    private class SampledApngDecoder(loader: Loader, sample: Int) : APNGDecoder(loader, null) {
        init { sampleSize = sample }
    }
    private class SampledWebPDecoder(loader: Loader, sample: Int) : WebPDecoder(loader, null) {
        init { sampleSize = sample }
    }

    class Factory(private val legacyWebP: Boolean) : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val header = animationHeader(result.source.source(), legacyWebP) ?: return null
            return FrameAnimationDecoder(result.source, options, header)
        }
    }

    companion object { private const val MAX_BYTES = 16L * 1024 * 1024 }
}

internal data class AnimationHeader(val png: Boolean, val width: Int, val height: Int)

/** Reads PNG chunks without consuming the source or trusting the URL/MIME type. */
internal fun animationHeader(source: BufferedSource, includeWebP: Boolean): AnimationHeader? {
    val png = source.rangeEquals(0, "89504e470d0a1a0a".decodeHex())
    return source.peek().use { input ->
        if (png) {
            input.skip(8)
            var scanned = 8L
            var width = 0
            var height = 0
            while (scanned < 1024 * 1024 && input.request(8)) {
                val length = input.readInt().toLong() and 0xffffffffL
                val type = input.readUtf8(4)
                if (length > 1024 * 1024 || !input.request(length + 4)) return null
                when (type) {
                    "IHDR" -> {
                        if (length != 13L) return null
                        width = input.readInt(); height = input.readInt()
                        if (!boundedCanvas(width, height)) return null
                        input.skip(length - 8 + 4)
                    }
                    "acTL" -> {
                        if (length != 8L || !boundedCanvas(width, height) || input.readInt() !in 1..1000) return null
                        return AnimationHeader(true, width, height)
                    }
                    "IDAT", "IEND" -> return null
                    else -> input.skip(length + 4)
                }
                scanned += length + 12
            }
            null
        } else if (includeWebP && input.request(30)) {
            if (input.readUtf8(4) != "RIFF") return null
            input.skip(4)
            if (input.readUtf8(4) != "WEBP" || input.readUtf8(4) != "VP8X") return null
            if (input.readIntLe() != 10 || input.readByte().toInt() and 2 == 0) return null
            input.skip(3)
            fun int24(): Int = (input.readByte().toInt() and 255) or
                ((input.readByte().toInt() and 255) shl 8) or ((input.readByte().toInt() and 255) shl 16)
            val width = int24() + 1
            val height = int24() + 1
            if (boundedCanvas(width, height)) AnimationHeader(false, width, height) else null
        } else null
    }
}

private fun boundedCanvas(width: Int, height: Int): Boolean = width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304
