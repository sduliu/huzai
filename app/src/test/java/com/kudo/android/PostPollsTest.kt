package com.kudo.android

import com.kudo.android.ui.topic.parseArticle
import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostPollsTest {
    private val pollJson = """{"name":"poll","type":"regular","status":"open","results":"always","voters":414,"options":[{"id":"a","html":"睡衣","votes":95},{"id":"b","html":"裸睡","votes":319}]}"""
    private fun post() = DiscourseParser.post(JSONObject("""{"id":12,"polls":[$pollJson],"polls_votes":{"poll":["a"]}}"""))
    @Test fun pollContainerIsOneInteractiveBlockInsteadOfOptionsAndStaleZeroVoters() {
        val blocks = parseArticle("""<div class="poll" data-poll-name="poll"><div class="poll-container"><ul><li data-poll-option-id="a">睡衣</li><li data-poll-option-id="b">裸睡</li></ul></div><div class="poll-info"><span>0</span><span>投票人</span></div></div>""")
        assertEquals("投票必须整体渲染，不能把静态占位票数当真实结果", 1, blocks.size)
        assertEquals("Poll", blocks.single()::class.simpleName)
    }
    @Test fun dynamicCountsAndSavedChoicesComeFromPostData() {
        val post = post()
        assertEquals(414, post.polls.single().voters)
        assertEquals(95, post.polls.single().options.first().votes)
        assertEquals(listOf("a"), post.pollVotes["poll"])
        assertTrue(DiscourseParser.post(JSONObject("""{"id":1}""")).polls.isEmpty())
        val hidden = post.polls.single().copy(results = "on_close")
        assertFalse(hidden.resultsVisible(true)); assertTrue(hidden.copy(status = "closed").resultsVisible(false))
        assertFalse(hidden.copy(results = "staff_only").resultsVisible(true, true))
        assertFalse(hidden.copy(options = listOf(PollOption("a", "A", null)), results = "always").resultsVisible(false))
        val multiple = hidden.copy(type = "multiple", min = 2, max = 2)
        assertTrue(multiple.validSelection(listOf("a", "b")))
        assertFalse(multiple.validSelection(listOf("a", "a"))); assertFalse(multiple.validSelection(listOf("a")))
    }
    @Test fun voteAndRetractionUseWebsiteProtocolAndConfirmedCounts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            assertTrue(runCatching { repository.vote(post(), "poll", listOf("bad")) }.isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
            server.enqueue(MockResponse().setBody("""{"poll":$pollJson}"""))
            val voted = repository.vote(post(), "poll", listOf("b"))
            assertEquals(listOf("b"), voted.pollVotes["poll"])
            assertEquals("/session/csrf", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("PUT", request.method); assertEquals("/polls/vote", request.path)
            assertEquals("post_id=12&poll_name=poll&options%5B%5D=b", request.body.readUtf8())
            server.enqueue(MockResponse().setBody("""{"poll":$pollJson}"""))
            assertTrue(repository.vote(voted, "poll", emptyList()).pollVotes.isEmpty())
            assertEquals("DELETE", server.takeRequest().method)
            server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
            assertTrue(runCatching { repository.vote(voted, "poll", listOf("a")) }.isFailure)
            assertEquals(listOf("b"), voted.pollVotes["poll"])
        }
    }
}
