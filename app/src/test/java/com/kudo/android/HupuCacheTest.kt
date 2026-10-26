package com.kudo.android

import com.kudo.android.hupu.HupuCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 批X-1：[HupuCache.remove]——详情错误 SSR 毒化条目的定向剔除。
 * 之前只有 clear() 全清；解析失败时不能波及同目录其他缓存。
 */
class HupuCacheTest {

    @Test
    fun `remove evicts memory and disk entry only for the given path`() {
        val dir = Files.createTempDirectory("hupu-cache-test").toFile()
        try {
            HupuCache.init(dir)
            HupuCache.put("/643318532.html", "page-a")
            HupuCache.put("/643318533.html", "page-b")
            HupuCache.remove("/643318532.html")
            assertNull(HupuCache.get("/643318532.html"))
            assertEquals("page-b", HupuCache.get("/643318533.html"))
        } finally {
            HupuCache.clear()
            dir.deleteRecursively()
        }
    }
}
