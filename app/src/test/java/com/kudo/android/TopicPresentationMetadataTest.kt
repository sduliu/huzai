package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TopicPresentationMetadataTest {
    @Test fun tagsRetainSlugAndExplicitIconWithoutRawJsonNames() {
        val page = DiscourseParser.topicPage(JSONObject("""{"topic_list":{"topics":[{"id":1,"tags":["普通",{"name":"纯水","slug":"1461-tag"},{"name":"自定义","icon":"faucet","color":"#f7941d"},null,{"name":7},{"name":""}]}]}}"""))
        val tags = page.topics.single().tagDetails
        assertEquals(listOf("普通", "纯水", "自定义"), page.topics.single().tags)
        assertEquals("1461-tag", tags[1].slug)
        assertEquals(TagIconStyle("faucet", "#f7941d"), tags[2].iconStyle(emptyMap()))
        assertNull(tags[0].iconStyle(emptyMap()))
    }

    @Test fun editedStateUsesVersionAndMissingUpdateTimeFallsBackToCreation() {
        val created = "2026-10-08T10:00:00Z"
        val post = JSONObject("""{"id":1,"post_number":1,"created_at":"$created","version":1}""")
        assertEquals(created, DiscourseParser.post(post).updatedAt)
        assertFalse(DiscourseParser.post(post).edited)
        post.put("updated_at", "2026-10-09T10:00:00Z").put("version", 2)
        assertTrue(DiscourseParser.post(post).edited)
        assertEquals("2026-10-09T10:00:00Z", DiscourseParser.post(post).updatedAt)
        post.remove("version")
        assertFalse("更新时间改变不能替代真实编辑版本", DiscourseParser.post(post).edited)
    }

    @Test fun themeConfigIsReadFromEscapedPreloadAndResolvesBySlugFirst() {
        val theme = JSONObject().put("49", JSONObject().put("settings", JSONObject().put("tag_icon_list",
            "1461-tag,faucet,#f7941d|人工智能,brain,#bd93f9|INVALID,<svg>,#ffffff|bad-color,book,red")))
        val preload = JSONObject().put("themeSettings", theme.toString()).toString()
        val html = "<div data-preloaded='${preload.replace("&", "&amp;").replace("'", "&#39;")}'></div>"
        val styles = parseTagIconSettings(html)
        assertEquals(TagIconStyle("faucet", "#f7941d"), TopicTag("改名后的标签", "1461-tag").iconStyle(styles))
        assertEquals(TagIconStyle("brain", "#bd93f9"), TopicTag("人工智能").iconStyle(styles))
        assertFalse(styles.containsKey("invalid"))
        assertEquals("", styles["bad-color"]?.color)
        assertTrue(parseTagIconSettings("<html>普通网页</html>").isEmpty())
    }

    @Test fun bundledCatalogIncludesActualWaterAiAndAnnouncementIcons() {
        assertEquals("faucet", TopicTag("纯水", "1461-tag").iconStyle(DefaultTagIcons)?.icon)
        assertEquals("brain", TopicTag("人工智能", "444-tag").iconStyle(DefaultTagIcons)?.icon)
        assertEquals("bullhorn", TopicTag("公告", "31-tag").iconStyle(DefaultTagIcons)?.icon)
    }

    @Test fun detailRetainsTagsAndMainPostEditMetadata() {
        val detail = DiscourseParser.detail(JSONObject("""{"id":1,"tags":[{"name":"公告","slug":"31-tag"}],"post_stream":{"stream":[10],"posts":[{"id":10,"post_number":1,"version":3,"updated_at":"2026-10-09T12:00:00Z"}]}}"""))
        assertEquals("31-tag", detail.tags.single().slug)
        assertTrue(detail.originalPost!!.edited)
    }

    @Test fun searchRetainsTagSlugsToo() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"topics":[{"id":1,"tags":[{"name":"纯水","slug":"1461-tag"}]}]}"""))
            val result = CommunityRepository(DiscourseClient(NoCookies, server.url("/"))).search("test").first.single()
            assertEquals("1461-tag", result.tagDetails.single().slug)
        }
    }
}
