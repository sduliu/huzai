package com.kudo.android

import com.kudo.android.network.dohRequestWithoutReplay
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DohRequestPolicyTest {
    @Test fun server503RetryAfterZeroCannotReplayAWritingRequest() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("replayed"))
            val http = OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor { it.proceed(dohRequestWithoutReplay(it.request())) }.build()
            val request = Request.Builder().url(server.url("/posts")).post(FormBody.Builder().add("raw", "fixture").build()).build()
            http.newCall(request).execute().use { assertEquals(503, it.code) }
            assertEquals(1, server.requestCount)
            http.connectionPool.evictAll()
        }
    }
    @Test fun readRequestsKeepTheirExistingSemantics() {
        val request = Request.Builder().url("https://linux.do/latest.json").get().build()
        assertSame(request, dohRequestWithoutReplay(request))
    }
}
