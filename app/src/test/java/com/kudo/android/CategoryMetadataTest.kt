package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CategoryMetadataTest {
    @Test fun successfulDirectoryIsSharedAndFailureDoesNotEraseItOrCrossAccounts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            repository.useCategoryAccount(1)
            server.enqueue(MockResponse().setBody("""{"categories":[{"id":7,"name":"运营反馈"}]}"""))
            repository.categories()
            assertEquals("运营反馈", repository.categoryCatalog.value.single().name)
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            try { repository.categories(); fail("expected failure") } catch (_: ApiFailure) { }
            assertEquals(7, repository.categoryCatalog.value.single().id)
            repository.useCategoryAccount(2)
            assertTrue(repository.categoryCatalog.value.isEmpty())
        }
    }

    @Test fun categoryIconsFollowServerMetadataInBothResponseShapes() {
        val categories = """[{"id":4,"name":"开发调优","color":"32c3c3","icon":"code","description":"<p>开发与调优</p>"},{"id":98,"name":"国产替代","color":"D12C25","icon":"seedling"}]"""
        for (json in listOf("""{"categories":$categories}""", """{"category_list":{"categories":$categories}}""")) {
            val result = DiscourseParser.categories(JSONObject(json))
            assertEquals(listOf("code", "seedling"), result.map { it.icon })
            assertEquals("32c3c3", result.first().color)
            assertEquals("开发与调优", result.first().description)
        }
        val changed = DiscourseParser.categories(JSONObject("""{"categories":[{"id":4,"name":"开发调优","icon":"book"}]}"""))
        assertEquals("服务端换图标不依赖分类名称或ID", "book", changed.single().icon)
    }

    @Test fun missingNullAndMalformedIconsUseTheFallbackKey() {
        val result = DiscourseParser.categories(JSONObject("""{"categories":[{"id":1},{"id":2,"icon":null},{"id":3,"icon":{}},{"id":4,"icon":123},{"id":5,"icon":"  "},{"id":6,"icon":"future-icon"}]}"""))
        assertEquals(listOf("", "", "", "", "", "future-icon"), result.map { it.icon })
    }
}
