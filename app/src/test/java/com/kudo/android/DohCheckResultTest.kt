package com.kudo.android

import com.kudo.android.network.dohCheckResult
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test

class DohCheckResultTest {
    private fun reply(type: String = "application/json", code: Int = 200) = Response.Builder()
        .request(Request.Builder().url("https://linux.do/latest.json").build()).protocol(Protocol.HTTP_2)
        .code(code).message("fixture").header("Content-Type", type).build()
    @Test fun onlyActualTopicDataCanClaimSiteSuccess() {
        assertTrue(dohCheckResult(reply(), """{"topic_list":{"topics":[]}}""", true).contains("站点访问成功"))
        assertFalse(dohCheckResult(reply("text/html"), "<html>unavailable</html>", true).contains("访问成功"))
        assertTrue(dohCheckResult(reply(), "{}", false).contains("ECH 未协商"))
        assertTrue(dohCheckResult(reply(code = 503), "{}", true).contains("HTTP 503"))
    }
    @Test fun successfulHtmlChallengeDoesNotMasqueradeAsSiteSuccess() {
        val html = "<html><script>window._cf_chl_opt={};</script></html>"
        assertTrue(dohCheckResult(reply("text/html"), html, true).contains("网页验证"))
    }
}
