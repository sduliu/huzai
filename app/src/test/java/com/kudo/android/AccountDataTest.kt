package com.kudo.android

import com.kudo.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class AccountDataTest {
    @Test fun categoryNewDraftKeepsItsContextWithoutBecomingAReply() {
        val drafts = localAccountDrafts(mapOf("draft_7_new_0_raw" to "通用草稿", "draft_7_new_4_raw" to "分类草稿",
            "draft_7_new_4_category" to 98, "draft_8_new_4_raw" to "其他账号"), 7).associateBy { it.key }
        assertEquals(2, drafts.size)
        assertNull(drafts.getValue("7_new_0").categoryContext)
        val category = drafts.getValue("7_new_4")
        assertEquals(4, category.categoryContext)
        assertEquals(98, category.draft.categoryId)
        assertNull(category.topicId)
        assertNull(category.replyTo)
        assertFalse(category.remote)
    }

    @Test fun summaryKeepsRealCountsAndUnknownValuesSeparateFromZero() {
        val summary = parseUserSummary(JSONObject("""{"user_summary":{"days_visited":825,"posts_read_count":357012,"likes_received":0,"post_count":3102}}"""))
        assertEquals(825, summary.daysVisited)
        assertEquals(357012, summary.postsRead)
        assertEquals(0, summary.likesReceived)
        assertEquals(3102, summary.posts)
        val missing = parseUserSummary(JSONObject("""{"user_summary":{"days_visited":null,"posts_read_count":"unknown","likes_received":-1}}"""))
        assertNull(missing.daysVisited); assertNull(missing.postsRead); assertNull(missing.likesReceived); assertNull(missing.posts)
    }
    @Test fun draftsStayAccountScopedAndKeepReplyTargetsAndCloudBackups() {
        val drafts = localAccountDrafts(mapOf("draft_7_300_8_raw" to "回复草稿", "draft_8_new_0_raw" to "其他账号",
            "draft_7_cloud_new_private_message_raw" to "私信副本", "draft_7_cloud_new_private_message_title" to "私信标题"), 7)
        assertEquals(2, drafts.size)
        assertEquals(300, drafts[0].topicId); assertEquals(8, drafts[0].replyTo)
        assertTrue(drafts[1].remote); assertTrue(drafts[1].privateMessage)
    }
    @Test fun cloudDraftEditingPreservesTagsRecipientsAndUnknownComposerFields() {
        val cloud = parseCloudDraft(JSONObject("""{"draft_key":"new_private_message","draft_sequence":9,"data":{"reply":"原文","title":"标题","recipients":["recipient"],"tags":["tag"],"unknown_plugin":{"value":1}}}"""))
        val updated = updatedCloudData(cloud, cloud.draft.copy(raw = "新正文"))
        assertEquals("recipient", updated.getJSONArray("recipients").getString(0))
        assertEquals("tag", updated.getJSONArray("tags").getString(0))
        assertEquals(1, updated.getJSONObject("unknown_plugin").getInt("value"))
        assertEquals(9, cloud.sequence)
        assertEquals("新正文", updated.getString("reply"))
    }
    @Test(expected = ApiFailure::class) fun invalidSummaryIsAnErrorInsteadOfFakeEmptyStatistics() {
        parseUserSummary(JSONObject("""{"error":"unavailable"}"""))
    }
    @Test fun personalListsUseCreatedTopicsRatherThanParticipatedTopicsAndKeepServerPagination() = runBlocking {
        val server = MockWebServer()
        val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
        val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
        try {
            server.enqueue(MockResponse().setBody("""{"users":[],"topic_list":{"topics":[],"more_topics_url":"/topics/created-by/member.json?page=1"}}"""))
            val page = repository.personalTopics("a/b", AccountDestination.TOPICS)
            assertEquals("/topics/created-by/a%2Fb.json", server.takeRequest().path)
            assertEquals("/topics/created-by/member.json?page=1", page.next)
            server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[]}}"""))
            repository.personalTopics("member", AccountDestination.MESSAGES, mailbox = "archive")
            assertEquals("/topics/private-messages-archive/member.json", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[]}}"""))
            repository.personalTopics("member", AccountDestination.HISTORY)
            assertEquals("/read.json", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
