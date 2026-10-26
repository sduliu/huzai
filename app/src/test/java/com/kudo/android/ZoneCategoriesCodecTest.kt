package com.kudo.android

import com.kudo.android.hupu.HupuCategory
import com.kudo.android.hupu.HupuTopicInfo
import com.kudo.android.hupu.decodeZoneCategoriesJson
import com.kudo.android.hupu.encodeZoneCategoriesJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批Q-1：专区目录持久化编解码（SWR 缓存载体）。 */
class ZoneCategoriesCodecTest {
    @Test
    fun roundTrip() {
        val list = listOf(
            HupuCategory(
                cateId = "9", name = "国际足球", logo = "https://x/logo.png",
                topics = listOf(
                    HupuTopicInfo(topicId = "184", name = "足球话题区", url = "/topic",
                        desc = "激辩包容", hotText = "15.8w", logo = "https://x/t.png"),
                    HupuTopicInfo(topicId = "60", name = "影视区", url = "/ent"),
                ),
            ),
            HupuCategory(cateId = "1", name = "篮球", topics = emptyList()),
        )
        val decoded = decodeZoneCategoriesJson(encodeZoneCategoriesJson(list))
        assertEquals(2, decoded.size)
        assertEquals("9", decoded[0].cateId)
        assertEquals("国际足球", decoded[0].name)
        assertEquals("https://x/logo.png", decoded[0].logo)
        assertEquals(2, decoded[0].topics.size)
        assertEquals(HupuTopicInfo(topicId = "184", name = "足球话题区", url = "/topic",
            desc = "激辩包容", hotText = "15.8w", logo = "https://x/t.png"), decoded[0].topics[0])
        assertEquals("60", decoded[0].topics[1].topicId)
        assertTrue(decoded[1].topics.isEmpty())
    }

    @Test
    fun dropsBadEntries() {
        val json = """[
            {"cateId":"9","name":"国际足球","topics":[
                {"topicId":"184","name":"足球话题区","url":"/topic"},
                {"topicId":"0","name":"无链接","url":""}
            ]},
            {"cateId":"","name":"无id","topics":[]}
        ]"""
        val decoded = decodeZoneCategoriesJson(json)
        assertEquals(1, decoded.size)
        assertEquals(1, decoded[0].topics.size)
    }

    @Test
    fun badJsonReturnsEmpty() {
        assertTrue(decodeZoneCategoriesJson("not-json").isEmpty())
        assertTrue(decodeZoneCategoriesJson("").isEmpty())
        assertTrue(decodeZoneCategoriesJson("{}").isEmpty())
    }
}
