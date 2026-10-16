package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.*
import com.kudo.android.ui.theme.KudoTheme
import kotlinx.coroutines.cancel
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

class ChatReadUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun anchoredChatAcknowledgesVisibleMessagesInsteadOfUnreadLoadedTail() {
        val reads = CopyOnWriteArrayList<Int>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/chat/api/channels/7" -> """{"channel":{"title":"离线聊天"}}"""
                "/chat/api/channels/7/messages" -> """{"messages":[${(1..50).joinToString(",") { """{"id":$it,"message":"消息 $it","user":{"id":42,"username":"fixture"}}""" }}],"meta":{}}"""
                "/session/csrf" -> """{"csrf":"fixture"}"""
                "/chat/api/channels/7/read" -> { reads += request.url.queryParameter("message_id")!!.toInt(); "{}" }
                else -> error("Unexpected offline endpoint ${request.url.encodedPath}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val cookies = object : SessionCookies {
            override fun get(url: String) = ""
            override fun set(url: String, cookie: String) {}
        }
        val repo = CommunityRepository(DiscourseClient(cookies, "https://chat.test/".toHttpUrl(), http = http))
        val vm = ChatViewModel(repo, 7, initialMessage = 10)
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            compose.setContent { KudoTheme(DisplaySettings()) {
                Box(Modifier.width(360.dp).height(500.dp)) {
                    ChatScreen(vm, Member(42, "fixture", "夹具", ""), AppSettings(context), {}, {}, {}, {}, {})
                }
            } }
            compose.waitUntil(5000) { reads.isNotEmpty() }
            assertTrue("必须按可见位置标读，不能直接推进到已加载的第50条：$reads", reads.all { it in 10..20 })
            compose.onNodeWithText("消息 10").assertIsDisplayed()
        } finally { vm.viewModelScope.cancel(); http.dispatcher.executorService.shutdown() }
    }
}
