package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BadgeDataTest {
    private val catalog = """{"badge_groupings":[{"id":1,"position":0}],"badges":[
        {"id":1,"name":"基本用户","description":"基础功能","long_description":"<p>达到信任级别1</p>","icon":"user","badge_type_id":3,"badge_grouping_id":1},
        {"id":2,"name":"未获得","has_badge":true,"icon":"heart","image_url":"https://cdn.test/badge.png"},
        {"id":3,"name":"停用隐藏","enabled":false}, {"id":4,"name":"不公开","listable":false}]}"""
    private val personal = """{"badges":[{"id":9,"name":"特别奖励","enabled":false}],"user_badges":[
        {"id":10,"badge_id":1,"count":2,"granted_at":"2026-01-01T00:00:00Z"},
        {"id":11,"badge_id":1,"granted_at":"2026-02-01T00:00:00Z"},
        {"id":12,"badge_id":9,"granted_at":"2026-02-02T00:00:00Z"}]}"""

    @Test fun combinesCatalogAndPersonalAwardsWithoutTrustingAnotherSessionsHasBadge() {
        val badges = parseUserBadges(JSONObject(catalog), JSONObject(personal))
        assertEquals(listOf(1, 9, 2), badges.map { it.id })
        assertTrue(badges.first().earned)
        assertEquals(3, badges.first().count)
        assertEquals("2026-02-01T00:00:00Z", badges.first().grantedAt)
        assertEquals("<p>达到信任级别1</p>", badges.first().longDescription)
        assertFalse(badges.last().earned)
        assertEquals("https://cdn.test/badge.png", badges.last().imageUrl)
        assertEquals("特别奖励", badges[1].name)
    }

    @Test fun emptyAwardsKeepTheWholeVisibleCatalogLockedAndMissingOptionalFieldsStayUsable() {
        val badges = parseUserBadges(JSONObject(catalog), JSONObject("""{"user_badges":[]}"""))
        assertEquals(listOf(1, 2), badges.map { it.id })
        assertTrue(badges.none { it.earned })
        assertEquals(0, badges.last().count)
        assertEquals("", badges.last().longDescription)
    }

    @Test fun missingAwardArrayFailsInsteadOfClaimingEveryBadgeIsLocked() {
        assertThrows(ApiFailure::class.java) { parseUserBadges(JSONObject(catalog), JSONObject("{}")) }
        assertThrows(ApiFailure::class.java) { parseUserBadges(JSONObject("{}"), JSONObject(personal)) }
    }

    @Test fun readsTheCatalogAndRequestedUsersGroupedAwardsUsingGetOnly() = runBlocking {
        val server = MockWebServer()
        try {
            val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
            val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            server.enqueue(MockResponse().setBody(catalog))
            server.enqueue(MockResponse().setBody(personal))
            assertEquals(3, repository.userBadges("Member").size)
            val first = server.takeRequest(); val second = server.takeRequest()
            assertEquals("/badges.json", first.path)
            assertEquals("/user-badges/member.json?grouped=true", second.path)
            assertEquals("GET", first.method); assertEquals("GET", second.method)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
