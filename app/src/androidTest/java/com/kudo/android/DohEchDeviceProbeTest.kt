package com.kudo.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.isCloudflareChallenge
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import java.time.Instant
import java.util.concurrent.TimeUnit

/** This class and its JNI library belong to the opt-in test APK, never the app. */
internal object DohEchNativeProbe {
    init { System.loadLibrary("kudo_doh_probe") }
    @JvmStatic external fun exchange(input: String): String
}

class DohEchDeviceProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext
    private val app get() = (context.applicationContext as KudoApplication).container

    @Test fun nativeRejectsCredentialsAndWriteTargetsWithoutNetwork() {
        assumeTrue(arguments.getString("dohNativeProbe") == "true")
        fun call(input: JSONObject) = JSONObject(DohEchNativeProbe.exchange(input.toString()))
        val secret = "fixture-secret-never-send"
        val dns = call(JSONObject().put("mode", "dns").put("cookie", secret))
        assertEquals("dns_credentials_forbidden", dns.getString("error"))
        assertFalse(dns.toString().contains(secret))
        val writeTarget = call(JSONObject().put("mode", "ech").put("url", "https://linux.do/posts.json").put("cookie", secret))
        assertEquals("target_not_allowed", writeTarget.getString("error"))
        assertFalse(writeTarget.toString().contains(secret))
        val foreign = call(JSONObject().put("mode", "ech").put("url", "https://example.com/latest.json").put("cookie", secret))
        assertEquals("target_not_allowed", foreign.getString("error"))
    }

    @Test fun compareExistingSessionListAndImageOnDirectWifi() {
        assumeTrue(arguments.getString("dohLiveProbe") == "true")
        val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = network.getNetworkCapabilities(network.activeNetwork)
        val vpn = network.allNetworks.any { network.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val systemProxy = network.defaultProxy?.host?.isNotBlank() == true
        val rows = JSONArray()
        val report = JSONObject().put("timeUtc", Instant.now().toString()).put("wifi", wifi)
            .put("vpnDetected", vpn).put("systemProxyConfigured", systemProxy)
            .put("httpProxyDisabledInAllProbeClients", true).put("records", rows)
        val output = File(context.filesDir, "doh-device-probe.json")
        fun save() { output.writeText(report.toString(2)) }
        val root = "https://linux.do/"
        val originalCookie = app.cookies.get(root)
        report.put("hasLoginCookie", originalCookie.split(';').any { it.trim().startsWith("_t=") })
        val sessionIds = mutableMapOf<String, Long>()
        val modes = listOf("system", "doh", "rust_tls", "ech")
        var imageUrl: String? = null
        try {
            save()
            assertTrue("This experiment requires the user's direct Wi-Fi", wifi)
            assertFalse("VPN detected: DoH experiment paused", vpn)
            assertFalse("System HTTP proxy detected: experiment paused", systemProxy)
            val dns = native(JSONObject().put("mode", "dns"))
            report.put("dns", JSONObject().put("ok", dns.optBoolean("ok"))
                .put("echConfigBytes", dns.optInt("echConfigBytes")).put("ttl", dns.optInt("ttl"))
                .put("ipv4Count", dns.optJSONArray("addresses")?.strings()?.count { ':' !in it } ?: 0)
                .put("ipv6Count", dns.optJSONArray("addresses")?.strings()?.count { ':' in it } ?: 0)
                .put("records", dns.optJSONObject("dnsRecords"))
                .put("stage", dns.optString("stage")).put("error", dns.optString("error")))
            save()
            for (mode in modes) {
                for ((operation, path) in listOf("session" to "/session/current.json", "list" to "/latest.json")) {
                    val url = root.toHttpUrl().resolve(path)!!.toString()
                    val result = read(mode, operation, url)
                    rows.put(result.record)
                    val json = if (result.record.optInt("status") == 200) runCatching { JSONObject(result.body.toString(Charsets.UTF_8)) }.getOrNull() else null
                    if (operation == "session") {
                        val user = json?.optJSONObject("current_user")
                        val id = user?.optLong("id", 0) ?: 0
                        sessionIds[mode] = id
                        result.record.put("authenticated", id > 0)
                        if (id > 0) imageUrl = imageUrl ?: avatarUrl(user?.optString("avatar_template").orEmpty())
                    } else {
                        val topics = json?.optJSONObject("topic_list")?.optJSONArray("topics")
                        result.record.put("topicCount", topics?.length() ?: 0)
                        imageUrl = imageUrl ?: avatarUrl(json?.optJSONArray("users")?.optJSONObject(0)?.optString("avatar_template").orEmpty())
                    }
                    save()
                    check(result.record.optInt("status") != 429) { "Rate limit: stopped the read-only experiment" }
                    SystemClock.sleep(500)
                }
            }
            imageUrl?.let { url ->
                for (mode in modes) {
                    val result = read(mode, "image", url)
                    val bitmap = if (result.record.optInt("status") == 200) BitmapFactory.decodeByteArray(result.body, 0, result.body.size) else null
                    result.record.put("decodedImage", bitmap != null)
                    bitmap?.recycle()
                    rows.put(result.record)
                    save()
                    check(result.record.optInt("status") != 429) { "Rate limit: stopped the image experiment" }
                    SystemClock.sleep(500)
                }
            }
            val knownSession = sessionIds.values.firstOrNull { it > 0 }
            report.put("allSuccessfulSessionsMatch", knownSession != null && sessionIds.values.filter { it > 0 }.all { it == knownSession })
            report.put("existingLoginVerifiedViaEch", (sessionIds["ech"] ?: 0) > 0)
            val ech = rows.objects().filter { it.optString("mode") == "ech" }
            assertTrue("ECH must be accepted by an actual TLS connection", ech.any { it.optString("ech") == "Accepted" })
            assertTrue("ECH must load an actual topic list", ech.any { it.optString("operation") == "list" && it.optInt("topicCount") > 0 })
            assertTrue("ECH must decode a same-site image", ech.any { it.optString("operation") == "image" && it.optBoolean("decodedImage") })
            if (report.optBoolean("hasLoginCookie")) {
                assertTrue("Existing login cookie must produce an authenticated ECH session", (sessionIds["ech"] ?: 0) > 0)
                assertTrue("Successful transports must preserve the same account", report.getBoolean("allSuccessfulSessionsMatch"))
            }
            report.put("accepted", true)
        } finally {
            report.put("cookieStorageUnchanged", app.cookies.get(root) == originalCookie)
            save()
        }
    }

    private fun native(input: JSONObject) = JSONObject(DohEchNativeProbe.exchange(input.toString()))

    private data class Result(val record: JSONObject, val body: ByteArray = byteArrayOf())

    private fun read(mode: String, operation: String, url: String): Result {
        val start = SystemClock.elapsedRealtime()
        val row = JSONObject().put("mode", mode).put("operation", operation)
        return try {
            val cookie = app.cookies.get(url)
            val result: Result
            if (mode == "ech" || mode == "rust_tls") {
                val raw = native(JSONObject().put("mode", if (mode == "ech") "ech" else "tls")
                    .put("url", url).put("cookie", cookie).put("userAgent", app.userAgent))
                if (!raw.optBoolean("ok")) {
                    row.put("stage", raw.optString("stage")).put("error", raw.optString("error"))
                    result = Result(row)
                } else {
                    val bytes = Base64.decode(raw.getString("bodyBase64"), Base64.DEFAULT)
                    row.put("status", raw.getInt("status")).put("ech", raw.getString("ech"))
                        .put("alpn", raw.getString("alpn")).put("ipv6", raw.getBoolean("ipv6"))
                        .put("dnsMillis", raw.getLong("dnsMillis"))
                    result = response(row, url, bytes, raw.optString("contentType"), raw.optString("cfMitigated"))
                }
            } else {
                val dns = if (mode == "system") Dns.SYSTEM else object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        require(hostname == "linux.do")
                        val resolved = native(JSONObject().put("mode", "dns"))
                        check(resolved.optBoolean("ok")) { "DoH resolution failed" }
                        return resolved.getJSONArray("addresses").strings().map(InetAddress::getByName)
                    }
                }
                val client = OkHttpClient.Builder().dns(dns).proxy(Proxy.NO_PROXY).cookieJar(CookieJar.NO_COOKIES)
                    .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
                try {
                    val request = Request.Builder().url(url).header("User-Agent", app.userAgent)
                        .header("Referer", "https://linux.do/").header("Accept-Encoding", "identity")
                        .header("Accept", if (operation == "image") "image/*" else "application/json")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
                    result = client.newCall(request).execute().use {
                        row.put("status", it.code).put("alpn", it.protocol.toString())
                        response(row, url, boundedBody(it), it.header("Content-Type").orEmpty(), it.header("cf-mitigated").orEmpty())
                    }
                } finally {
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
            }
            result
        } catch (e: Exception) {
            Result(row.put("stage", "android_network").put("error", e.javaClass.simpleName))
        } finally {
            row.put("elapsedMillis", SystemClock.elapsedRealtime() - start)
        }
    }

    private fun response(row: JSONObject, url: String, bytes: ByteArray, contentType: String, cf: String): Result {
        val response = Response.Builder().request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_2).code(row.getInt("status")).message("")
            .header("Content-Type", contentType).header("cf-mitigated", cf).build()
        val text = if (contentType.contains("html", true) || contentType.contains("json", true)) bytes.toString(Charsets.UTF_8) else ""
        row.put("challenge", isCloudflareChallenge(response, text)).put("contentType", contentType).put("bodyBytes", bytes.size)
        return Result(row, bytes)
    }

    private fun boundedBody(response: Response): ByteArray {
        val out = ByteArrayOutputStream()
        response.body?.byteStream()?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                check(out.size() + read <= 8 * 1024 * 1024) { "Probe response exceeded limit" }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun avatarUrl(template: String): String? {
        val url = "https://linux.do/".toHttpUrl().resolve(template.replace("{size}", "48")) ?: return null
        return url.takeIf { template.isNotBlank() && it.isHttps && it.host == "linux.do" && it.port == 443 &&
            (it.encodedPath.startsWith("/user_avatar/") || it.encodedPath.startsWith("/uploads/")) }?.toString()
    }

    private fun JSONArray.strings() = (0 until length()).map(::getString)
    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
}
