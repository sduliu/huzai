package com.kudo.android

import com.kudo.android.data.*
import com.kudo.android.ui.LinuxDoLink
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class UnavailableTopicTest {
    private fun check(response: MockResponse, second: MockResponse? = null, expected: String = "NOT_FOUND") = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(response); second?.let(server::enqueue)
            if (second == null && expected == "VERIFICATION") server.enqueue(response.clone())
            val client = DiscourseClient(NoCookies, server.url("/"))
            val failure = runCatching { client.get("/t/9.json") }.exceptionOrNull() as ApiFailure
            assertEquals(expected, failure.kind.name)
        }
    }
    @Test fun json404IsUnavailableRatherThanTemporaryServerError() = check(MockResponse().setResponseCode(404).setBody("""{"errors":["找不到"],"error_type":"not_found"}"""))
    @Test fun html404IsUnavailableRatherThanWebVerification() = check(MockResponse().setResponseCode(404).setBody("<html>404</html>"))
    @Test fun redirectedSuccessful404PageIsUnavailable() = check(MockResponse().setResponseCode(302).setHeader("Location", "/404"), MockResponse().setBody("<html>Page not found</html>"))
    @Test fun successWrappedNotFoundIsNotAcceptedAsTopic() = check(MockResponse().setBody("""{"error_type":"not_found","errors":["not found"]}"""))
    @Test fun explicitCloudflareAndLoginErrorsKeepTheirMeaning() {
        check(MockResponse().setResponseCode(403).setHeader("cf-mitigated", "challenge").setBody("<html>verify</html>"), expected = "VERIFICATION")
        check(MockResponse().setResponseCode(403).setBody("""{"error_type":"not_logged_in"}"""), expected = "SIGN_IN")
        check(MockResponse().setResponseCode(403).setBody("{}"), expected = "FORBIDDEN")
    }
    @Test fun direct404ContentLinkUsesUnavailableDestination() {
        assertEquals("Unavailable", LinuxDoLink.parseContent("https://linux.do/404")?.toString())
    }
}
