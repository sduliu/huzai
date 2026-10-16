package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostBoostsTest {
    private val post = Post(12, 8, Member(2, "user", "用户", ""), "<p>正文</p>", "", 2, false, null, 2, true, canBoost = true)
    private val json = """{"id":8,"cooked":"<p>加油<img class=\"emoji\" alt=\":grin:\" src=\"/grin.png\"></p>","user":{"id":3,"username":"booster","avatar_template":"/{size}.png"}}"""

    @Test fun countsWebsiteEmojiSequencesAndUtf16InsteadOfAllGraphemes() {
        assertEquals(BoostCount(2, 0), BoostRules.count(" 中文 ", emptySet()))
        listOf("😀", "🇨🇳", "👩‍💻", "👍🏽", "❤️‍🔥", "1️⃣").forEach {
            assertEquals(it, BoostCount(1, 1), BoostRules.count(it, emptySet()))
        }
        assertEquals(BoostCount(2, 0), BoostRules.count("𝕏", emptySet()))
        assertEquals(BoostCount(3, 3), BoostRules.count(":Grin::smile::custom:", setOf("custom")))
        assertEquals(BoostCount(10, 0), BoostRules.count(":not_real:", emptySet()))
        assertNull(BoostRules.count("字".repeat(16), emptySet()).problem)
        assertNotNull(BoostRules.count("字".repeat(17), emptySet()).problem)
        assertNull(BoostRules.count("😀".repeat(5), emptySet()).problem)
        assertNotNull(BoostRules.count("😀".repeat(6), emptySet()).problem)
        assertNotNull(BoostRules.count(" \n ", emptySet()).problem)
    }

    @Test fun parsesBoostsAndPostsWithoutBoostFields() {
        val parsed = DiscourseParser.post(JSONObject("""{"id":12,"post_number":8,"can_boost":true,"boosts":[$json]}"""))
        assertTrue(parsed.canBoost); assertEquals(8, parsed.boosts.single().id)
        assertEquals("booster", parsed.boosts.single().author.username)
        assertEquals("/{size}.png", parsed.boosts.single().author.avatar)
        assertTrue(DiscourseParser.post(JSONObject("""{"id":1}""")).boosts.isEmpty())
        assertFalse(DiscourseParser.post(JSONObject("""{"id":1}""")).canBoost)
    }

    @Test fun creationUsesTheDedicatedEndpointAndConfirmedObjectAndInvalidInputNeverWrites() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
            server.enqueue(MockResponse().setBody(json))
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            assertTrue(runCatching { repository.boost(post, "字".repeat(17), emptySet()) }.isFailure)
            assertTrue(runCatching { repository.boost(post.copy(canBoost = false), "加油", emptySet()) }.isFailure)
            assertEquals(0, server.requestCount)
            val result = repository.boost(post, " 加油😀 ", emptySet())
            assertEquals(post.number, result.number); assertEquals(post.likes, result.likes)
            assertEquals(8, result.boosts.single().id)
            assertEquals("/session/csrf", server.takeRequest().path)
            val write = server.takeRequest()
            assertEquals("POST", write.method); assertEquals("/discourse-boosts/posts/12/boosts", write.path)
            assertEquals("token", write.getHeader("X-CSRF-Token"))
            assertEquals("raw=%E5%8A%A0%E6%B2%B9%F0%9F%98%80", write.body.readUtf8())
            server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
            assertTrue(runCatching { repository.boost(post, "重试前确认", emptySet()) }.isFailure)
            assertEquals(3, server.requestCount)
        }
    }
}
