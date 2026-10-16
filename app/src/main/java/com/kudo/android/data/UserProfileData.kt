package com.kudo.android.data

import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class PublicProfile(val member: Member, val followers: Int?, val following: Int?,
    val lastSeen: String, val joined: String, val location: String, val website: String,
    val title: String, val canMessage: Boolean)

enum class ProfileTab(val label: String, val filter: String = "") {
    SUMMARY("总结"), ACTIVITY("动态", "4,5"), TOPICS("话题", "4"), REPLIES("回复", "5"),
    LIKES("赞", "1"), REACTIONS("回应"), BOOSTS("Boost"), VOTES("投票"), SOLVED("已解决")
}

data class ProfileEntry(val key: String, val topicId: Int, val number: Int?, val title: String,
    val excerpt: String = "", val time: String = "", val annotation: String = "", val count: Int? = null,
    val category: Category? = null, val tags: List<TopicTag> = emptyList(), val image: String = "",
    val categoryId: Int = category?.id ?: 0) {
    val url get() = "https://linux.do/t/topic/$topicId" + (number?.takeIf { it > 1 }?.let { "/$it" } ?: "")
}
data class ProfilePage(val entries: List<ProfileEntry>, val next: String?)
data class ProfileUserCount(val member: Member, val count: Int?)
data class ProfileLink(val title: String, val url: String, val clicks: Int?)
data class ProfileSummary(val counts: Map<String, Int?>, val topics: List<ProfileEntry>, val replies: List<ProfileEntry>,
    val links: List<ProfileLink>, val users: Map<String, List<ProfileUserCount>>, val categories: List<Category>, val badges: List<UserBadge>)

internal fun JSONObject.profileCount(key: String): Int? = (opt(key) as? Number)?.toLong()
    ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

internal fun parsePublicProfile(json: JSONObject): PublicProfile {
    val user = json.optJSONObject("user") ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回用户资料")
    return PublicProfile(DiscourseParser.member(user, avatarDetailsLoaded = true), user.profileCount("total_followers"),
        user.profileCount("total_following"), user.stringField("last_seen_at"), user.stringField("created_at"),
        user.stringField("location"), user.stringField("website"), user.stringField("title"), user.optBoolean("can_send_private_message_to_user"))
}

internal fun parseProfileSummary(json: JSONObject, catalog: List<Category> = emptyList()): ProfileSummary {
    val summary = json.optJSONObject("user_summary") ?: throw ApiFailure(FailureKind.SERVER, "暂时无法读取个人统计")
    val topics = json.optJSONArray("topics").objects().associateBy { it.optInt("id") }
    val categoryRows = summary.optJSONArray("top_categories") ?: json.optJSONArray("top_categories")
    val topCategories = DiscourseParser.categories(JSONObject().put("categories", categoryRows))
    val categories = (catalog + topCategories).associateBy(Category::id)
    fun entry(item: JSONObject, reply: Boolean): ProfileEntry? {
        val nested = item.optJSONObject("topic")
        val id = if (reply) item.positiveInt("topic_id") ?: nested?.positiveInt("id") else item.positiveInt("id")
        id ?: return null
        val topic = nested ?: topics[id] ?: item
        val number = if (reply) item.positiveInt("post_number") else null
        val categoryId = topic.positiveInt("category_id") ?: item.positiveInt("category_id") ?: 0
        val category = categories[categoryId]
        val tags = (topic.optJSONArray("tags") ?: item.optJSONArray("tags")).topicTags()
        val image = topic.stringField("image_url").ifBlank { item.stringField("image_url") }
        return ProfileEntry("summary-$id-${number ?: 1}", id, number, topic.stringField("title").ifBlank { "话题 #$id" },
            plainText(item.stringField("excerpt")), item.stringField("created_at"), count = item.profileCount("like_count"),
            category = category, tags = tags, image = image, categoryId = categoryId)
    }
    val topIds = summary.optJSONArray("topics").objects()
    val references = summary.optJSONArray("topic_ids")
    val topTopics = if (references != null) (0 until references.length()).mapNotNull { index ->
        references.optInt(index).takeIf { it > 0 }?.let(topics::get)?.let { entry(it, false) }
    } else if (summary.has("topics")) topIds.mapNotNull { item ->
        val id = item.positiveInt("id") ?: item.positiveInt("topic_id")
        id?.let { entry(topics[it] ?: item, false) }
    } else topics.values.mapNotNull { entry(it, false) }
    val userGroups = listOf("most_replied_to_users", "most_liked_by_users", "most_liked_users").associateWith { key ->
        summary.optJSONArray(key).objects().map { ProfileUserCount(DiscourseParser.member(it), it.profileCount("count")) }
            .filter { it.member.username.isNotBlank() }
    }
    val badges = json.optJSONArray("badges").objects().mapNotNull { badge ->
        val id = badge.positiveInt("id") ?: return@mapNotNull null
        UserBadge(id, badge.stringField("name"), badge.stringField("description"), badge.stringField("long_description"),
            badge.stringField("icon"), badge.stringField("image_url").ifBlank { null }, badge.optInt("badge_type_id", 3), 0, true)
    }
    return ProfileSummary(listOf("days_visited", "posts_read_count", "likes_received", "likes_given", "topic_count", "post_count",
        "time_read", "topics_entered", "recent_time_read", "bookmark_count").associateWith(summary::profileCount),
        topTopics, summary.optJSONArray("replies").objects().mapNotNull { entry(it, true) },
        summary.optJSONArray("links").objects().map { ProfileLink(it.stringField("title").ifBlank { it.stringField("url") },
            it.stringField("url"), it.profileCount("clicks")) }.filter { it.url.isNotBlank() }, userGroups,
        topCategories, badges)
}

internal fun parseProfilePage(json: JSONObject, tab: ProfileTab, cursor: String?): ProfilePage {
    if (tab == ProfileTab.VOTES) {
        val page = DiscourseParser.topicPage(json)
        return ProfilePage(page.topics.map { ProfileEntry("vote-${it.id}", it.id, null, it.title, it.excerpt, it.bumpedAt) }, page.next)
    }
    val array = when (tab) {
        ProfileTab.REACTIONS -> json.optJSONArray("items") ?: json.optJSONArray("reactions") ?: json.optJSONArray("posts")
        ProfileTab.BOOSTS -> json.optJSONArray("boosts")
        ProfileTab.SOLVED -> json.optJSONArray("user_solved_posts")
        else -> json.optJSONArray("user_actions")
    } ?: throw ApiFailure(FailureKind.SERVER, "服务器未返回${tab.label}列表")
    val raw = array.objects()
    val entries = raw.mapNotNull { item ->
        val post = item.optJSONObject("post") ?: item
        val id = post.positiveInt("topic_id") ?: return@mapNotNull null
        val number = post.positiveInt("post_number") ?: post.stringField("url").substringBefore('?').split('/').filter(String::isNotBlank)
            .takeIf { it.size >= 4 && it.first() == "t" }?.lastOrNull()?.toIntOrNull()?.takeIf { it > 0 }
        val record = item.positiveInt("id") ?: item.positiveInt("post_id")
        val action = item.optInt("action_type")
        val key = record?.let { "${tab.name}-$it" } ?: "${tab.name}-$id-${number ?: 1}-$action"
        ProfileEntry(key, id, number, post.stringField("topic_title").ifBlank { item.stringField("title") }.ifBlank { "话题 #$id" },
            plainText(if (tab == ProfileTab.BOOSTS) item.stringField("cooked") else post.stringField("excerpt")),
            item.stringField("acting_at").ifBlank { item.stringField("created_at") }, when (tab) {
                ProfileTab.REACTIONS -> item.optJSONObject("reaction")?.stringField("reaction_value").orEmpty()
                ProfileTab.BOOSTS -> "Boost"
                ProfileTab.SOLVED -> "已采纳"
                else -> if (action == 4) "发布话题" else if (action == 5) "回复" else if (action == 1) "赞" else ""
            })
    }.distinctBy { it.key }
    val pageSize = if (tab in setOf(ProfileTab.REACTIONS, ProfileTab.BOOSTS)) 20 else 30
    val next = if (raw.size < pageSize) null else when (tab) {
        ProfileTab.REACTIONS, ProfileTab.BOOSTS -> raw.lastOrNull()?.positiveInt("id")?.toString()?.takeIf { it != cursor }
        else -> ((cursor?.toIntOrNull() ?: 0) + raw.size).toString()
    }
    return ProfilePage(entries, next)
}

private fun CommunityRepository.profileUrl(username: String, vararg segments: String): String =
    client.baseUrl.newBuilder().addPathSegment("u").addPathSegment(username).apply { segments.forEach(::addPathSegment) }.build().toString()

suspend fun CommunityRepository.publicProfile(username: String): PublicProfile = parsePublicProfile(client.get(
    client.baseUrl.newBuilder().addPathSegment("u").addPathSegment("$username.json").build().toString()))
suspend fun CommunityRepository.profileCardBackground(username: String): String =
    client.get(profileUrl(username, "card.json")).optJSONObject("user")?.stringField("card_background_upload_url")
        ?: throw ApiFailure(FailureKind.SERVER, "暂时无法读取用户卡片")
suspend fun CommunityRepository.profileSummary(username: String): ProfileSummary {
    val json = client.get(profileUrl(username, "summary.json"))
    val catalog = categoryCatalog.value.ifEmpty {
        try { withTimeoutOrNull(8000) { categories() }.orEmpty() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
    }
    return parseProfileSummary(json, catalog)
}
suspend fun CommunityRepository.profileFollows(username: String, followers: Boolean): List<Member> =
    client.getArray(profileUrl(username, "follow", if (followers) "followers" else "following")).objects()
        .map { DiscourseParser.member(it) }.filter { it.username.isNotBlank() }.distinctBy { it.username.lowercase() }

suspend fun CommunityRepository.profilePage(username: String, tab: ProfileTab, cursor: String? = null): ProfilePage {
    require(tab != ProfileTab.SUMMARY)
    if (tab == ProfileTab.VOTES) {
        val path = cursor ?: client.baseUrl.newBuilder().addPathSegment("topics").addPathSegment("voted-by").addPathSegment("$username.json").build().toString()
        return parseProfilePage(client.get(path), tab, cursor)
    }
    val query = mutableMapOf("username" to username)
    val path = when (tab) {
        ProfileTab.REACTIONS -> "/discourse-reactions/posts/reactions.json".also { cursor?.let { query["before_reaction_user_id"] = it } }
        ProfileTab.BOOSTS -> client.baseUrl.newBuilder().addPathSegment("discourse-boosts").addPathSegment("users")
            .addPathSegment(username).addPathSegment("boosts-given.json").build().toString().also { query.remove("username"); cursor?.let { query["before_boost_id"] = it } }
        ProfileTab.SOLVED -> "/solution/by_user.json".also { query["offset"] = cursor ?: "0" }
        else -> "/user_actions.json".also { query["offset"] = cursor ?: "0"; query["filter"] = tab.filter }
    }
    val url = client.resolve(path).newBuilder().apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build().toString()
    return parseProfilePage(client.getCollection(url), tab, cursor)
}
