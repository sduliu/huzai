package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TopicPresenceTest {
    private val name = "/discourse-presence/reply/71"
    private val channel = "/presence$name"
    private val viewer = Member(1, "self", "", "")

    @Test fun snapshotAndDeltasDeduplicateUsersExcludeSelfAndRemoveDepartedUsers() {
        val state = TopicReplyPresence(71, viewer.id)
        state.replace(snapshot(5, 1, 2, 2))
        assertTrue(state.subscribed)
        assertEquals(listOf(2), state.users.map(Member::id))
        assertFalse(state.accept(JSONArray("""[
            {"channel":"/presence/discourse-presence/reply/72","message_id":99,"data":{"entering_users":[{"id":9}]}},
            {"channel":"$channel","message_id":6,"data":{"entering_users":[{"id":1},{"id":2},{"id":3,"username":"three","avatar_template":"/avatar/{size}.png"}]}},
            {"channel":"$channel","message_id":6,"data":{"leaving_user_ids":[3]}},
            {"channel":"$channel","message_id":7,"data":{"leaving_user_ids":[2]}}
        ]""")))
        assertEquals(listOf(3), state.users.map(Member::id))
        assertEquals("/avatar/{size}.png", state.users.single().avatar)
        assertEquals(7L, state.cursor)
        assertFalse(state.accept(JSONArray("""[{"channel":"/__status","data":{"$channel":7}}]""")))
        assertTrue(state.accept(JSONArray("""[{"channel":"/__status","data":{"$channel":2}}]""")))
        assertTrue(state.accept(JSONArray("""[{"channel":"$channel","message_id":9,"data":{"leaving_user_ids":[3]}}]""")))
        assertEquals(listOf(3), state.users.map(Member::id))
        assertEquals(7L, state.cursor)
    }

    @Test fun emptyHistoryUsesZeroAndMissingOrCountOnlySnapshotsDisableAvatars() {
        val state = TopicReplyPresence(71, 1)
        state.replace(snapshot(null, 2))
        assertEquals(0L, state.cursor)
        assertTrue(state.accept(JSONArray("""[{"channel":"$channel","message_id":1,"data":{"count_delta":1}}]""")))
        state.replace(JSONObject("""{"count":2,"last_message_id":5}"""))
        assertFalse(state.subscribed)
        assertTrue(state.users.isEmpty())
        state.replace(null)
        assertFalse(state.subscribed)
        assertTrue(state.users.isEmpty())
    }

    @Test fun initialPresenceAndReplyEventsShareOneAuthenticatedPollWithoutLeakingCookies() = runBlocking {
        MockWebServer().use { main -> MockWebServer().use { push ->
            main.start(); push.start()
            main.enqueue(response(snapshot(5, 1, 2)))
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="fixture-presence-key">"""))
            push.enqueue(MockResponse().setBody("""[
                {"channel":"/topic/71","message_id":10,"data":{"type":"created"}},
                {"channel":"$channel","message_id":6,"data":{"entering_users":[{"id":3,"username":"three"}]}}
            ]"""))
            push.enqueue(MockResponse().setBody("""[{"channel":"$channel","message_id":7,"data":{"leaving_user_ids":[2]}}]"""))
            val events = withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(Cookies, main.url("/")), push.url("/"))
                    .observeActivity(viewer, 71).take(3).toList()
            }
            assertEquals(listOf(listOf(2), listOf(2, 3), listOf(3)), events.map { it.replyingUsers!!.map(Member::id) })
            assertEquals(listOf(false, true, false), events.map(TopicActivity::checkReplies))
            val initial = main.takeRequest()
            assertEquals(name, initial.requestUrl!!.queryParameter("channels[]"))
            assertEquals("_t=fixture-cookie", initial.getHeader("Cookie"))
            val first = push.takeRequest()
            assertTrue(first.body.readUtf8().contains("%2Fpresence%2Fdiscourse-presence%2Freply%2F71=5"))
            assertEquals("fixture-presence-key", first.getHeader("X-Shared-Session-Key"))
            assertNull(first.getHeader("Cookie")); assertNull(first.getHeader("X-CSRF-Token"))
            assertTrue(push.takeRequest().body.readUtf8().contains("%2Fpresence%2Fdiscourse-presence%2Freply%2F71=6"))
        } }
    }

    @Test fun missingDeltaResyncsBeforeAcceptingLaterMembershipChanges() = runBlocking {
        MockWebServer().use { main -> MockWebServer().use { push ->
            main.start(); push.start()
            main.enqueue(response(snapshot(5, 2)))
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="fixture-key">"""))
            main.enqueue(response(snapshot(8, 4)))
            push.enqueue(MockResponse().setBody("""[{"channel":"$channel","message_id":7,"data":{"entering_users":[{"id":3}]}}]"""))
            push.enqueue(MockResponse().setBody("""[
                {"channel":"$channel","message_id":7,"data":{"entering_users":[{"id":3}]}},
                {"channel":"$channel","message_id":9,"data":{"leaving_user_ids":[4]}}
            ]"""))
            val events = withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(Cookies, main.url("/")), push.url("/"))
                    .observeActivity(viewer, 71).take(3).toList()
            }
            assertEquals(listOf(listOf(2), listOf(4), emptyList()), events.map { it.replyingUsers!!.map(Member::id) })
            assertEquals(3, main.requestCount)
            push.takeRequest()
            assertTrue(push.takeRequest().body.readUtf8().contains("%2Fpresence%2Fdiscourse-presence%2Freply%2F71=8"))
        } }
    }

    @Test fun disconnectClearsAvatarsAndFirstSuccessfulConnectionFetchesANewSnapshot() = runBlocking {
        MockWebServer().use { main -> MockWebServer().use { push ->
            main.start(); push.start()
            main.enqueue(response(snapshot(5, 2)))
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="fixture-key">"""))
            main.enqueue(response(snapshot(8, 3)))
            push.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
            push.enqueue(MockResponse().setBody("[]"))
            val events = withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(Cookies, main.url("/")), push.url("/"))
                    .observeActivity(viewer, 71).take(3).toList()
            }
            assertEquals(listOf(listOf(2), emptyList(), listOf(3)), events.map { it.replyingUsers!!.map(Member::id) })
            assertTrue(events.last().checkReplies)
            assertEquals(3, main.requestCount)
        } }
    }

    @Test fun unavailablePresenceKeepsReplyChecksWorkingAndAnonymousDoesNotReadPresence() = runBlocking {
        MockWebServer().use { main -> MockWebServer().use { push ->
            main.start(); push.start()
            main.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
            main.enqueue(MockResponse().setBody("""<meta name="shared_session_key" content="fixture-key">"""))
            push.enqueue(MockResponse().setBody("[]"))
            val events = withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(Cookies, main.url("/")), push.url("/"))
                    .observeActivity(viewer, 71).take(2).toList()
            }
            assertTrue(events.first().replyingUsers!!.isEmpty())
            assertTrue(events.last().checkReplies)
            assertFalse(push.takeRequest().body.readUtf8().contains("presence"))
            push.enqueue(MockResponse().setBody("[]"))
            val anonymous = withTimeout(5000) {
                DiscourseMessageBus(DiscourseClient(Cookies, main.url("/")), push.url("/"))
                    .observeActivity(null, 71).take(1).toList()
            }
            assertTrue(anonymous.single().checkReplies)
            assertNull(anonymous.single().replyingUsers)
            assertEquals(2, main.requestCount)
        } }
    }

    private fun snapshot(cursor: Long?, vararg ids: Int) = JSONObject().put("last_message_id", cursor ?: JSONObject.NULL)
        .put("count", ids.size).put("users", JSONArray(ids.map { JSONObject().put("id", it).put("username", "user$it") }))
    private fun response(snapshot: JSONObject) = MockResponse().setBody(JSONObject().put(name, snapshot).toString())
    private object Cookies : SessionCookies {
        override fun get(url: String) = "_t=fixture-cookie"
        override fun set(url: String, cookie: String) = Unit
    }
}
