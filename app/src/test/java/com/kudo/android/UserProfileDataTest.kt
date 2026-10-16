package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UserProfileDataTest {
    @Test fun summaryResolvesPopularRepliesOutsideTheTopCategoryStatistics() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = MockResponse().setBody(
                    if (request.path == "/site.json") """{"categories":[{"id":7,"name":"运营反馈","icon":"comments"}]}"""
                    else """{"user_summary":{"topic_ids":[10],"top_categories":[{"id":4,"name":"开发调优"}],"replies":[{"topic_id":10,"post_number":108}]},"topics":[{"id":10,"category_id":7,"title":"回复来源"}]}""")
            }
            server.start()
            val result = CommunityRepository(DiscourseClient(NoCookies, server.url("/"))).profileSummary("fixture")
            assertEquals("运营反馈", result.topics.single().category?.name)
            assertEquals("运营反馈", result.replies.single().category?.name)
        }
    }

    @Test fun actualSummaryTopicIdsExcludeSideloadedReplyOriginsFromPopularTopics() {
        val result = parseProfileSummary(JSONObject("""{"user_summary":{"topic_ids":[10],"replies":[{"topic_id":9,"post_number":8}]},"topics":[{"id":9,"title":"回复来源"},{"id":10,"title":"热门话题"}]}"""))
        assertEquals(listOf(10), result.topics.map { it.topicId })
        assertEquals("回复来源", result.replies.single().title)
    }
    @Test fun unresolvedSummaryCategoriesKeepTheirIdentityWithoutExpandingTopStatistics() {
        val json = JSONObject("""{"user_summary":{"topic_ids":[10],"top_categories":[{"id":4,"name":"开发调优"}],"replies":[{"topic_id":10,"post_number":3}]},"topics":[{"id":10,"category_id":7}]}""")
        assertEquals(7, parseProfileSummary(json).replies.single().categoryId)
        val resolved = parseProfileSummary(json, listOf(Category(7, "运营反馈", "feedback", "", "", 0)))
        assertEquals(7, resolved.replies.single().category?.id)
        assertEquals(listOf(4), resolved.categories.map(Category::id))
    }
    @Test fun recipientSpecificMessageDraftIsVisibleAndKeepsItsKey() {
        val drafts = localAccountDrafts(mapOf("draft_7_message_some.user_title" to "私信草稿", "draft_7_message_some.user_raw" to "未发送内容",
            "draft_7_message_some.user_recipients" to "some.user"), 7)
        assertEquals("7_message_some.user", drafts.single().key)
        assertTrue(drafts.single().privateMessage)
    }
    @Test fun messagePermissionUsesTargetUserPermissionRatherThanInventedSingularField() {
        val profile = parsePublicProfile(JSONObject("""{"user":{"id":1,"username":"a","can_send_private_messages":true,"can_send_private_message_to_user":true}}"""))
        assertTrue(profile.canMessage)
        assertFalse(parsePublicProfile(JSONObject("""{"user":{"id":1,"username":"a","can_send_private_messages":true,"can_send_private_message_to_user":false}}""")).canMessage)
    }
    @Test fun summaryKeepsUnknownCountsAndJoinsRepliesToSideloadedTopics() {
        val result = parseProfileSummary(JSONObject("""{"user_summary":{"likes_received":107,"topic_count":12,"post_count":58,"days_visited":null,"replies":[{"topic_id":9,"post_number":8,"like_count":4}],"topics":[{"id":10}],"most_liked_users":[{"id":2,"username":"other","count":7}]},"topics":[{"id":9,"title":"回复来源"},{"id":10,"title":"热门主题"}]}"""))
        assertEquals(107, result.counts["likes_received"])
        assertNull(result.counts["days_visited"])
        assertNull(result.counts["posts_read_count"])
        assertEquals("热门主题", result.topics.single().title)
        assertEquals("回复来源", result.replies.single().title)
        assertTrue(result.replies.single().url.endsWith("/9/8"))
        assertEquals(7, result.users["most_liked_users"]!!.single().count)
    }
    @Test fun summaryEntriesKeepAvailableCategoryTagsAndCover() {
        val result = parseProfileSummary(JSONObject("""{"user_summary":{"topic_ids":[10],"replies":[{"topic_id":10,"post_number":3}]},"topics":[{"id":10,"title":"带封面的主题","category_id":4,"tags":[{"name":"Android","slug":"android"}],"image_url":"https://cdn.test/topic.png"}],"top_categories":[{"id":4,"name":"开发调优","slug":"dev","color":"35a9b7","description_text":"","topic_count":2}]}"""))
        assertEquals(4, result.topics.single().category?.id)
        assertEquals(listOf("Android"), result.topics.single().tags.map(TopicTag::name))
        assertEquals("https://cdn.test/topic.png", result.topics.single().image)
        assertEquals(4, result.replies.single().category?.id)
        assertEquals("Android", result.replies.single().tags.single().name)
    }
    @Test fun offsetAdvancesByRawRowsEvenWhenInvalidOrDuplicateRowsAreRemoved() {
        val rows = (1..30).joinToString(",") { """{"topic_id":1,"post_number":2,"action_type":5}""" }
        val result = parseProfilePage(JSONObject("""{"user_actions":[$rows]}"""), ProfileTab.REPLIES, "30")
        assertEquals(1, result.entries.size)
        assertEquals("60", result.next)
    }
    @Test fun boostUsesNestedPostUrlAndReactionUsesItsOwnCursor() {
        val boost = parseProfilePage(JSONObject("""{"boosts":[{"id":88,"cooked":"<p>加油</p>","post":{"topic_id":9,"url":"/t/topic/9/8","topic_title":"主题"}}]}"""), ProfileTab.BOOSTS, null).entries.single()
        assertEquals(8, boost.number)
        assertEquals("加油", boost.excerpt)
        val rows = (1..20).joinToString(",") { """{"id":$it,"post":{"topic_id":9,"post_number":$it},"reaction":{"reaction_value":"heart"}}""" }
        assertEquals("20", parseProfilePage(JSONObject("""{"items":[$rows]}"""), ProfileTab.REACTIONS, null).next)
    }
    @Test fun missingCollectionsAreErrorsNotEmptySuccess() {
        assertThrows(ApiFailure::class.java) { parseProfilePage(JSONObject(), ProfileTab.BOOSTS, null) }
        assertThrows(ApiFailure::class.java) { parseProfileSummary(JSONObject()) }
    }
    @Test fun userCardAndActivityEndpointsPreserveUsernameAndPagination() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"user":{"card_background_upload_url":"/card.png"}}"""))
            assertEquals("/card.png", repository.profileCardBackground("test.name"))
            assertEquals("/u/test.name/card.json", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"user_actions":[]}"""))
            assertNull(repository.profilePage("test.name", ProfileTab.ACTIVITY, "30").next)
            val request = server.takeRequest().requestUrl!!
            assertEquals("4,5", request.queryParameter("filter"))
            assertEquals("30", request.queryParameter("offset"))
            server.enqueue(MockResponse().setBody("""[{"id":1,"username":"a"}]"""))
            assertEquals("a", repository.profileFollows("test.name", true).single().username)
            assertEquals("/u/test.name/follow/followers", server.takeRequest().path)
        }
    }
}
