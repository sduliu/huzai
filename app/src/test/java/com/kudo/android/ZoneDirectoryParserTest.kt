package com.kudo.android

import com.kudo.android.hupu.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批X9-1：官方专区目录（m.hupu.com/zone SSR __NEXT_DATA__）解析验证 */
class ZoneDirectoryParserTest {

    private fun page(json: String) =
        """<html><body><script id="__NEXT_DATA__" type="application/json">$json</script></body></html>"""

    private val sample = page(
        """{"props":{"pageProps":{"data":[
          {"categoryId":0,"name":"热门","sort":0,"topicList":[
            {"topicId":1,"topicName":"步行街主干道","cateId":1,"topicLogo":"https://l.png","count":"132.0万"},
            {"topicId":246,"topicName":"ACG圈","cateId":9,"topicLogo":"","count":"1577"}
          ]},
          {"categoryId":1,"name":"步行街","sort":1,"topicList":[
            {"topicId":1,"topicName":"步行街主干道","cateId":1,"topicLogo":"https://l.png","count":"132.0万"},
            {"topicId":34,"topicName":"破事水","cateId":1,"topicLogo":"","count":"4.6万"}
          ]}
        ]}}}""",
    )

    @Test
    fun `groups keep api order and hot group renames to recommend`() {
        val list = HupuParser.parseZoneDirectory(sample)
        assertEquals(2, list.size)
        assertEquals("z0", list[0].cateId)
        assertEquals("推荐", list[0].name) // 官方 UI 文案改写
        assertEquals("z1", list[1].cateId)
        assertEquals("步行街", list[1].name)
    }

    @Test
    fun `topics map count to followText and logo`() {
        val topics = HupuParser.parseZoneDirectory(sample)[0].topics
        assertEquals(2, topics.size)
        val bj = topics[0]
        assertEquals("1", bj.topicId)
        assertEquals("步行街主干道", bj.name)
        assertEquals("132.0万", bj.followText) // 服务端万制文案直出
        assertEquals("https://l.png", bj.logo)
        assertEquals("", bj.url) // url 由 repo 侧回填
        assertEquals("1577", topics[1].followText)
        assertEquals(null, topics[1].logo)
    }

    @Test
    fun `missing next data yields empty`() {
        assertTrue(HupuParser.parseZoneDirectory("<html><body/></html>").isEmpty())
        assertTrue(HupuParser.parseZoneDirectory(page("""{"props":{"pageProps":{}}}""")).isEmpty())
    }
}
