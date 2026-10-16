package com.kudo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class SvgImageDecoderTest {
    @Test fun applicationLoaderDecodesSvgByContentAndRendersNestedShapes() = runBlocking<Unit> {
        val bitmap = render(ByteBuffer.wrap(SvgSample.toByteArray()), 1200, 420)
        try {
            assertEquals(Color.RED, bitmap.getPixel(150, 200))
            assertEquals(Color.BLUE, bitmap.getPixel(900, 200))
            assertEquals(Color.WHITE, bitmap.getPixel(600, 20))
        } finally { bitmap.recycle() }
    }

    @Test fun actualTopicSvgDecodesWithTheProductionNetworkAndLoader() = runBlocking<Unit> {
        assumeTrue("实际CDN探针需显式启用", InstrumentationRegistry.getArguments().getString("svgLiveProbe") == "true")
        val bitmap = render(TopicSvgUrl, 1200, 420)
        try {
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            assertNotEquals("图形区域必须实际渲染", Color.WHITE, bitmap.getPixel(40, 40))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "svg-topic-3015506-decoded.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private suspend fun render(data: Any, width: Int, height: Int): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(data).size(width, height).build())
        assertTrue("SVG应由生产加载器成功解码：$result", result is SuccessResult)
        val drawable = (result as SuccessResult).image.asDrawable(context.resources)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    companion object {
        const val TopicSvgUrl = "https://cdn3.ldstatic.com/original/4X/4/3/d/43d3e73009239f4d2813bcc7cad64b30d4f7a9e7.svg"
        val SvgSample = """<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg" width="1200" height="420" viewBox="0 0 1200 420">
            <rect width="1200" height="420" fill="white"/>
            <svg x="40" y="40" width="480" height="340" viewBox="0 0 480 340"><rect width="480" height="340" fill="red"/></svg>
            <svg x="680" y="40" width="480" height="340" viewBox="0 0 480 340"><path d="M0 0H480V340H0Z" fill="blue"/></svg>
            </svg>"""
    }
}
