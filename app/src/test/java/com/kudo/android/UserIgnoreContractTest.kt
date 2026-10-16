package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UserIgnoreContractTest {
    private val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
    private val viewer = Member(1, "me", "", "", canIgnoreUsers = true)
    @Test fun ignoreAndRestoreUseWebsiteNotificationLevelsAndOnlyIgnoreHasExpiration() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
            repeat(2) { server.enqueue(MockResponse().setBody("""{"success":"OK"}""")) }
            val repo = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            val expires = Instant.parse("2030-01-01T00:00:00Z")
            repo.ignoreUser(viewer, "author.name", expires)
            repo.ignoreUser(viewer, "author.name", null)
            server.takeRequest()
            val ignore = server.takeRequest()
            assertEquals("PUT", ignore.method)
            assertEquals("/u/author.name/notification_level.json", ignore.path)
            assertEquals("notification_level=ignore&expiring_at=2030-01-01T00%3A00%3A00Z", ignore.body.readUtf8())
            assertEquals("notification_level=normal", server.takeRequest().body.readUtf8())
        }
    }
    @Test fun accountParsesWebsiteIgnoreSetAndPermission() {
        val user = DiscourseParser.member(JSONObject("""{"id":1,"username":"me","can_ignore_users":true,"ignored_users":["author","other"]}"""))
        assertTrue(user.canIgnoreUsers)
        assertEquals(setOf("author", "other"), user.ignoredUsers)
    }
    @Test fun selfAndInsufficientPermissionNeverWrite() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repo = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            assertTrue(runCatching { repo.ignoreUser(viewer, "Me", IgnoreDuration.DAY.expiresAt()) }.isFailure)
            assertTrue(runCatching { repo.ignoreUser(viewer.copy(canIgnoreUsers = false), "author", IgnoreDuration.DAY.expiresAt()) }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun foreverMatchesWebsiteThousandYearShortcut() {
        assertEquals(Instant.parse("3026-10-09T00:00:00Z"), IgnoreDuration.FOREVER.expiresAt(Instant.parse("2026-10-09T00:00:00Z")))
    }
    @Test fun restoreRemainsAvailableWhenIgnorePermissionWasLost() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"csrf":"token"}"""))
            server.enqueue(MockResponse().setBody("""{"success":"OK"}"""))
            CommunityRepository(DiscourseClient(cookies, server.url("/"))).ignoreUser(viewer.copy(canIgnoreUsers = false), "author", null)
            assertEquals(2, server.requestCount)
        }
    }
}
