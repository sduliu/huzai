package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TopicActionsContractTest {
    private val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
    private val post = DiscourseParser.post(JSONObject("""{"id":10,"post_number":1,"version":4,"can_view_edit_history":true,"actions_summary":[{"id":2,"can_act":true},{"id":7,"can_act":true},{"id":10,"can_act":true},{"id":1002,"can_act":true},{"id":3,"acted":true}]}"""))
    private val other = FlagReason(7, "notify_moderators", "其他", "", true)
    private val illegal = FlagReason(10, "illegal", "非法", "", true)
    private val config = FlagConfiguration(listOf(other, illegal), 3)

    @Test fun historyNeedsBothRealEditsAndPermissionAndFlagsExcludeLikesAndActedReasons() {
        assertTrue(post.hasEditHistory)
        assertFalse(post.copy(version = 1).hasEditHistory)
        assertFalse(post.copy(canViewEditHistory = false).hasEditHistory)
        assertEquals(setOf(7, 10, 1002), post.availableFlags)
    }

    @Test fun siteReasonsAreDynamicOrderedAndFilteredAndSettingsAcceptBothPreloadFormats() {
        val site = JSONObject("""{"post_action_types":[{"id":1002,"name_key":"custom_aigc","name":"AIGC未截图","description":"<b>说明</b>","position":2,"is_flag":true,"enabled":true,"applies_to":["Post"]},{"id":6,"name":"联系作者","position":1,"is_flag":true,"enabled":true,"require_message":true,"applies_to":["Post"]},{"id":2,"is_flag":false,"enabled":true,"applies_to":["Post"]},{"id":8,"is_flag":true,"enabled":false,"applies_to":["Post"]},{"id":9,"is_flag":true,"enabled":true,"applies_to":["Topic"]}]}""")
        val pre = JSONObject().put("siteSettings", JSONObject().put("min_personal_message_post_length", 3).toString()).toString()
        val result = parseFlagConfiguration(site, "<script id=\"data-preloaded\" type=\"application/json\">$pre</script>")
        assertEquals(listOf(6, 1002), result.reasons.map { it.id })
        assertEquals("说明", result.reasons.last().description)
        assertEquals(listOf(1002), result.available(post).map { it.id })
        assertEquals(3, result.minimum)
        val attribute = pre.replace("&", "&amp;").replace("\"", "&quot;")
        assertEquals(result, parseFlagConfiguration(site, "<div data-preloaded=\"$attribute\"></div>"))
    }

    @Test fun messagesRespectSiteMinimumMaximumAndIllegalConfirmation() {
        assertFalse(config.valid(other, " a ", false))
        assertTrue(config.valid(other, "说明文", false))
        assertFalse("网站说明上限为500字", config.valid(other, "文".repeat(501), false))
        assertFalse(config.valid(other, "文".repeat(1001), false))
        assertFalse(config.valid(illegal, "说明文", false))
        assertTrue(config.valid(illegal, "说明文", true))
        assertFalse(config.valid(other.copy(id = 99), "说明文", true))
    }

    @Test fun reportUsesPostActionContractAndPreservesMessageWithoutStaffActions() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
            server.enqueue(MockResponse().setBody("""{"id":10}"""))
            CommunityRepository(DiscourseClient(cookies, server.url("/"))).flag(post, config, other, " 说明 & 文本 ", false)
            assertEquals("/session/csrf", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/post_actions", request.path)
            assertEquals("id=10&post_action_type_id=7&message=%E8%AF%B4%E6%98%8E%20%26%20%E6%96%87%E6%9C%AC", request.body.readUtf8())
        }
    }

    @Test fun forbiddenOrInvalidReportNeverStartsAWrite() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repo = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            assertTrue(runCatching { repo.flag(post.copy(availableFlags = emptySet()), config, other, "说明文", false) }.isFailure)
            assertTrue(runCatching { repo.flag(post, config, illegal, "说明文", false) }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun historyUsesServerPointersAndKeepsAddDeleteText() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"post_id":10,"current_revision":4,"previous_revision":1,"next_revision":7,"display_username":"editor","created_at":"2026-10-09","edit_reason":"修正","body_changes":{"inline":"<p>保留<del>旧文</del><ins>新文 &amp; 内容</ins></p>"}}"""))
            val revision = CommunityRepository(DiscourseClient(cookies, server.url("/"))).revision(post)
            assertEquals("/posts/10/revisions/latest.json", server.takeRequest().path)
            assertEquals(1, revision.previous)
            assertEquals(7, revision.next)
            assertEquals("editor", revision.author)
            assertTrue(revision.changes.single().parts.contains(RevisionPart("旧文", RevisionKind.REMOVED)))
            assertTrue(revision.changes.single().parts.contains(RevisionPart("新文 & 内容", RevisionKind.ADDED)))
        }
    }
    @Test fun historyRetainsTagArrayAndReplyAuthorChanges() {
        val revision = parsePostRevision(JSONObject("""{"post_id":10,"current_revision":2,"tags_changes":{"previous":["旧标签"],"current":["新标签"]},"reply_to_post_number_changes":{"previous":{"post_number":2},"current":{"post_number":3}},"user_changes":{"previous":{"display_username":"原作者"},"current":{"display_username":"新作者"}}}"""))
        assertEquals(listOf("标签", "作者", "回复楼层"), revision.changes.map { it.label })
        assertEquals("旧标签", revision.changes.first().parts.first().text)
        assertEquals("2", revision.changes.last().parts.first().text)
    }
}
