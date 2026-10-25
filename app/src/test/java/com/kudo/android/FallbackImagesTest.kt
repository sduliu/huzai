package com.kudo.android

import com.kudo.android.hupuui.components.HupuImageFallback
import com.kudo.android.hupuui.components.fallbackIcon
import com.kudo.android.hupuui.components.fallbackTag
import org.junit.Assert.*
import org.junit.Test

/**
 * 批E-2：三类图片兜底的「失败分支渲什么」契约。
 * error 槽内可测面 = `fallbackIcon`/`fallbackTag` 纯函数映射；
 * coil SubcomposeAsyncImage 何时进入 error 槽是库行为（真机侧由
 * androidTest `FallbackImagesUiTest` 以坏地址验证兜底节点出现）。
 */
class FallbackImagesTest {

    @Test fun fallbackIcon_mapsAllThreeKindsToDistinctIcons() {
        val avatar = fallbackIcon(HupuImageFallback.Avatar)
        val icon = fallbackIcon(HupuImageFallback.Icon)
        val thumb = fallbackIcon(HupuImageFallback.Thumb)
        assertNotSame(avatar, icon)
        assertNotSame(icon, thumb)
        assertNotSame(avatar, thumb)
        assertEquals("Rounded.Person", avatar.name)
        assertEquals("Rounded.SportsBasketball", icon.name)
        assertEquals("Rounded.Image", thumb.name)
    }

    @Test fun fallbackTag_givesEachKindALocatableNode() {
        assertEquals("hupu-fallback-avatar", fallbackTag(HupuImageFallback.Avatar))
        assertEquals("hupu-fallback-icon", fallbackTag(HupuImageFallback.Icon))
        assertEquals("hupu-fallback-thumb", fallbackTag(HupuImageFallback.Thumb))
        assertEquals(3, HupuImageFallback.entries.map { fallbackTag(it) }.distinct().size)
    }

    @Test fun fallbackKinds_exhaustiveMappingCoversEveryEntry() {
        // 新增兜底种类时这里强制补映射断言，防止漏接
        assertEquals(3, HupuImageFallback.entries.size)
        HupuImageFallback.entries.forEach { kind ->
            assertTrue(fallbackIcon(kind).name.isNotBlank())
            assertTrue(fallbackTag(kind).startsWith("hupu-fallback-"))
        }
    }
}
