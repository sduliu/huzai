package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountExtrasTest {
    private val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
    @Test fun invitationListAcceptsArrayContractAndDoesNotVisitOrRedeemLinks() = runBlocking {
        val server = MockWebServer()
        try {
            val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""[{"id":5,"invite_key":"secret-example","description":"邀请备注","redemption_count":0,"max_redemptions_allowed":1}]"""))
            val record = repository.accountRecords(AccountDestination.INVITES, "member").single()
            assertEquals("邀请备注", record.title)
            assertEquals("https://linux.do/invites/secret-example", record.url)
            assertEquals("/u/member/invited/pending", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun pendingKeepsRawTextAndItsOriginalTopic() = runBlocking {
        val server = MockWebServer()
        try {
            val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"pending_posts":[{"id":9,"topic_id":3,"raw_text":"审核原文"}]}"""))
            val pending = repository.accountRecords(AccountDestination.PENDING, "member").single()
            assertEquals("审核原文", pending.body); assertEquals(3, pending.topicId)
        } finally { server.shutdown() }
    }
    @Test fun invitationParsingPreservesServerLimitAndExpiry() {
        val invite = parseAccountInvites(JSONObject("""{"invites":[{"invite":{"id":8,"expires_at":"2026-11-01","redemption_count":1,"max_redemptions_allowed":2},"invite_link":"https://linux.do/invites/example"}]}""")).single()
        assertEquals("已使用 1 / 2 次", invite.body)
        assertEquals("2026-11-01", invite.time)
    }
}
