package com.kudo.android.data

import org.json.JSONObject
import java.util.Locale

data class UserBadge(
    val id: Int, val name: String, val description: String, val longDescription: String,
    val icon: String, val imageUrl: String?, val type: Int, val groupPosition: Int,
    val earned: Boolean, val count: Int = 0, val grantedAt: String = "",
) {
    val url: String get() = "https://linux.do/badges/$id"
}

suspend fun CommunityRepository.userBadges(username: String): List<UserBadge> {
    val catalog = client.get("/badges.json")
    val path = client.baseUrl.newBuilder().addPathSegment("user-badges")
        .addPathSegment("${username.lowercase(Locale.ROOT)}.json").build()
    val grants = client.get(path.toString(), mapOf("grouped" to "true"))
    return parseUserBadges(catalog, grants)
}

internal fun parseUserBadges(catalog: JSONObject, personal: JSONObject): List<UserBadge> {
    val directory = catalog.optJSONArray("badges")?.objects()
        ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回徽章目录")
    val grants = personal.optJSONArray("user_badges")?.objects()
        ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回个人徽章状态")
    val byBadge = grants.distinctBy { it.toString() }.groupBy { it.optInt("badge_id") }
    val positions = catalog.optJSONArray("badge_groupings").objects()
        .associate { it.optInt("id") to it.optInt("position", Int.MAX_VALUE) }
    val all = directory + personal.optJSONArray("badges").objects().filter { badge -> directory.none { it.optInt("id") == badge.optInt("id") } }
    return all.distinctBy { it.optInt("id") }.mapNotNull { badge ->
        val id = badge.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
        val awards = byBadge[id].orEmpty()
        if (awards.isEmpty() && (!badge.optBoolean("enabled", true) || !badge.optBoolean("listable", true))) return@mapNotNull null
        UserBadge(id, badge.text("name").ifBlank { "徽章 #$id" }, badge.text("description"), badge.text("long_description"),
            badge.text("icon"), badge.text("image_url").takeIf(String::isNotBlank), badge.optInt("badge_type_id", 3),
            positions[badge.optInt("badge_grouping_id")] ?: Int.MAX_VALUE, awards.isNotEmpty(),
            awards.sumOf { it.optInt("count", 1).coerceAtLeast(1) }, awards.map { it.text("granted_at") }.maxOrNull().orEmpty())
    }.sortedWith(compareByDescending<UserBadge> { it.earned }.thenBy { it.groupPosition }.thenBy { it.id })
}
