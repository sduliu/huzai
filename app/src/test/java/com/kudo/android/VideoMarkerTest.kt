package com.kudo.android

import com.kudo.android.data.toTopic
import com.kudo.android.hupu.HupuThread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批G-1：视频帖列表可达性——hasVideo 从 HupuThread 桥进 feed 侧 Topic，
 * 供 TopicCard/FeedItem/FlatPostRow 挂「▶图角标 / 无图『视频』chip」。
 * 真实样本佐证：/tmp/b-lol.html 板块页 46 帖含 9 帖 hasVideo:true，
 * 官方 SSR 标题行挂 iconshipin2 视频角标（对齐对象，非自创形态）。
 */
class VideoMarkerTest {

    @Test
    fun `toTopic carries hasVideo flag`() {
        assertTrue(
            HupuThread(tid = "643103358", title = "视频帖", hasVideo = true, video = "v.mp4")
                .toTopic().hasVideo,
        )
        assertFalse(HupuThread(tid = "1", title = "普通帖").toTopic().hasVideo)
    }

    @Test
    fun `video thread keeps image covers for badge overlay`() {
        // 视频帖 cover 即 videoCover——图区渲染依赖它叠 ▶ 角标，确认桥接不丢
        val t = HupuThread(
            tid = "643103358", title = "视频帖", hasVideo = true,
            cover = "poster.jpg", covers = listOf("poster.jpg"), video = "v.mp4",
        ).toTopic()
        assertTrue(t.hasVideo)
        assertTrue(t.images.isNotEmpty() || t.image.isNotEmpty())
    }
}
