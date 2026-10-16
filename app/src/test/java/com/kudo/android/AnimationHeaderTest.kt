package com.kudo.android

import com.kudo.android.media.animationHeader
import com.kudo.android.media.isAnimatedAvif
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class AnimationHeaderTest {
    private fun fixture(name: String) = File("src/androidTest/assets/avatars/$name").readBytes()
    @Test fun avifAnimationUsesTheContainerBrandWithoutConsumingTheSource() {
        val bytes = fixture("three-frame.avif")
        val source = Buffer().write(bytes)
        assertTrue(isAnimatedAvif(source))
        assertArrayEquals(bytes, source.readByteArray())
        assertFalse(isAnimatedAvif(Buffer().write(fixture("static.png"))))
        assertFalse(isAnimatedAvif(Buffer().write(bytes.copyOf(12))))
        val bad = bytes.copyOf(); ByteBuffer.wrap(bad).putInt(0, Int.MAX_VALUE)
        assertFalse(isAnimatedAvif(Buffer().write(bad)))
    }

    @Test fun animationDetectionPreservesTheInputForFollowingDecoders() {
        val bytes = fixture("three-frame.png")
        val source = Buffer().write(bytes)
        val header = animationHeader(source, includeWebP = false)!!
        assertTrue(header.png)
        assertEquals(64, header.width)
        assertEquals(64, header.height)
        assertArrayEquals(bytes, source.readByteArray())
        assertFalse(animationHeader(Buffer().write(fixture("three-frame.webp")), true)!!.png)
        assertNull(animationHeader(Buffer().write(fixture("three-frame.webp")), false))
    }

    @Test fun staticAndTruncatedFilesAreNotClaimedByTheAnimationDecoder() {
        for (bytes in listOf(fixture("static.png"), fixture("three-frame.gif"), byteArrayOf(), fixture("three-frame.png").copyOf(31))) {
            val input = Buffer().write(bytes)
            assertNull(animationHeader(input, true))
            assertArrayEquals(bytes, input.readByteArray())
        }
    }

    @Test fun OversizedCanvasAndInvalidChunkLengthAreRejectedBeforeAllocation() {
        val oversized = fixture("three-frame.png")
        ByteBuffer.wrap(oversized).putInt(16, 8192)
        assertNull(animationHeader(Buffer().write(oversized), false))
        val invalid = fixture("three-frame.png")
        ByteBuffer.wrap(invalid).putInt(8, Int.MAX_VALUE)
        assertNull(animationHeader(Buffer().write(invalid), false))
    }
}
