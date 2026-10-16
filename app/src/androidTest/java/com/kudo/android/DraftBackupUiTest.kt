package com.kudo.android

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.DraftsScreen
import com.kudo.android.ui.theme.KudoTheme
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class DraftBackupUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deletingLocalRecoveryCopyNeverDeletesCloudDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("draft_backup_fixture", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val fixtureContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = prefs
            override fun getApplicationContext(): Context = this
        }
        val settings = AppSettings(fixtureContext)
        val key = "42_cloud_topic_9"
        settings.saveDraft(key, Draft(raw = "本机恢复副本"))
        val writes = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.method != "GET") writes.incrementAndGet()
            val body = when (request.url.encodedPath) {
                "/drafts.json" -> """{"drafts":[],"has_more":false}"""
                "/session/csrf" -> """{"csrf":"fixture"}"""
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repository = CommunityRepository(DiscourseClient(object : SessionCookies {
            override fun get(url: String) = ""
            override fun set(url: String, cookie: String) {}
        }, "https://draft.test/".toHttpUrl(), http = http))
        try {
            compose.setContent { KudoTheme(DisplaySettings()) {
                DraftsScreen(Member(42, "fixture", "", ""), repository, settings, {}, {}, {})
            } }
            compose.onNodeWithText("本机恢复副本").assertIsDisplayed()
            compose.onNodeWithContentDescription("删除话题 #9 的回复").performClick()
            compose.onAllNodesWithText("删除草稿").onLast().performClick()
            compose.waitUntil(5000) { writes.get() > 0 || settings.draft(key).raw.isBlank() }
            compose.runOnIdle { assertEquals("删除本机副本不能对云端发写请求", 0, writes.get()); assertTrue(settings.draft(key).raw.isBlank()) }
        } finally { http.dispatcher.executorService.shutdown(); prefs.edit().clear().commit() }
    }
}
