package com.kudo.android.data

import java.time.Instant
import java.time.ZoneOffset

enum class IgnoreDuration(val label: String, private val months: Long = 0, private val days: Long = 0, private val years: Long = 0) {
    DAY("一天", days = 1), TWO_WEEKS("两周", days = 14), MONTH("一个月", months = 1),
    SIX_MONTHS("六个月", months = 6), YEAR("一年", years = 1), FOREVER("永久", years = 1000);
    fun expiresAt(now: Instant = Instant.now()): Instant = now.atZone(ZoneOffset.UTC).plusYears(years).plusMonths(months).plusDays(days).toInstant()
}

suspend fun CommunityRepository.ignoreUser(viewer: Member, username: String, until: Instant?) {
    require(until == null || viewer.canIgnoreUsers) { "当前账号不能忽略用户" }
    require(username.isNotBlank() && !username.equals(viewer.username, ignoreCase = true)) { "不能忽略自己" }
    require(until == null || until.isAfter(Instant.now())) { "请选择未来的忽略期限" }
    val url = client.baseUrl.newBuilder().addPathSegment("u").addPathSegment(username).addPathSegment("notification_level.json").build()
    val fields = mutableMapOf("notification_level" to if (until == null) "normal" else "ignore")
    if (until != null) fields["expiring_at"] = until.toString()
    val result = client.write("PUT", url.toString(), fields)
    if (result.text("success") != "OK") throw ApiFailure(FailureKind.SERVER, "忽略状态尚未确认，请刷新网站设置后查看")
}
