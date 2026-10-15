package com.java.myapplication.data

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 球队页数据源——NBA 官方站静态 SSR 页（匿名 GET，PC UA 直出）：
 *   · nba.hupu.com/teams/{slug}    球队信息（进入NBA/主场/分区/主教练/简介）+ 阵容表
 *   · nba.hupu.com/schedule/{slug} 全季滚窗赛程表（当月+次月；本队名加粗，带胜负列）
 *
 * slug 空间与赛程卡的 memberId 互不相通，页面只认英文名——
 * 用赛程卡的中文短名（memberName）映射 slug（NBA 30 队全覆盖，2026-10 实测）。
 * 非 NBA 球队（足球/电竞）无此页 → 返回 null 走 memberId 赛程兜底。
 */
object HupuTeamApi {

    /** 中文短名 → NBA 官方站 slug（memberName 与赛程卡一致） */
    private val NBA_SLUGS = mapOf(
        "老鹰" to "hawks", "凯尔特人" to "celtics", "篮网" to "nets",
        "黄蜂" to "hornets", "公牛" to "bulls", "骑士" to "cavaliers",
        "独行侠" to "mavericks", "掘金" to "nuggets", "活塞" to "pistons",
        "勇士" to "warriors", "火箭" to "rockets", "步行者" to "pacers",
        "快船" to "clippers", "湖人" to "lakers", "灰熊" to "grizzlies",
        "热火" to "heat", "雄鹿" to "bucks", "森林狼" to "timberwolves",
        "鹈鹕" to "pelicans", "尼克斯" to "knicks", "雷霆" to "thunder",
        "魔术" to "magic", "76人" to "76ers", "太阳" to "suns",
        "开拓者" to "blazers", "国王" to "kings", "马刺" to "spurs",
        "猛龙" to "raptors", "爵士" to "jazz", "奇才" to "wizards",
    )

    fun nbaSlug(memberName: String): String? = NBA_SLUGS[memberName]

    private const val NBA_BASE = "https://nba.hupu.com"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client: OkHttpClient = HupuHttp.client

    /** 球队信息+阵容页 HTML。失败返回 null。 */
    suspend fun fetchNbaTeamHtml(slug: String, forceNetwork: Boolean = false): String? =
        fetch("team-info-$slug", "$NBA_BASE/teams/$slug", "title-text", forceNetwork)

    /** 球队赛程页 HTML（滚窗约当月+次月）。失败返回 null。 */
    suspend fun fetchNbaTeamScheduleHtml(slug: String, forceNetwork: Boolean = false): String? =
        fetch("team-schedule-$slug", "$NBA_BASE/schedule/$slug", "players_table", forceNetwork)

    private suspend fun fetch(
        cacheKey: String,
        url: String,
        marker: String,
        forceNetwork: Boolean,
    ): String? {
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    if (!html.contains(marker)) return@use null
                    HupuCache.put(cacheKey, html)
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
