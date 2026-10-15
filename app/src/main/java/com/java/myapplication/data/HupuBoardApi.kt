package com.java.myapplication.data

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * board：板块资讯 API——官方 App 首页赛事板块（NBA/国际足球/电竞…）的资讯流。
 *
 * 主接口是官方 App 私有 API（games.mobileapi basketballapi，匿名 GET 可用）：
 *   GET /1/{ver}/basketballapi/news/v2/newsList?tagCode={code}&preCount={n}&newId={anchor}
 * 接口名叫 basketballapi 但服务全部板块。实测可用 tagCode（2026-10）：
 *   nba / lol / cba / epl / csl / digital——其余板块码（football/soccer/zuqiu 等）返回空 data。
 *
 * 翻页语义（实测）：
 *   · preCount = 已展示条数（0 / 20 / 40…），每页固定约 20 条；
 *   · newId   = 本页首条 nid 作锚点，翻页回传（传 0 或旧 nid 服务端都接受，
 *     但传首条 nid 可避免 feed 更新后重复）。
 *
 * 国际足球板块不走此接口（tagCode=football/soccer 均空），资讯在
 * m.hupu.com/soccer 的 SSR `pageProps.news` 里，见 [fetchSoccerNewsHtml]。
 *
 * 响应里 `ad_page_id` 表明服务端会插运营/广告位：实测 type=LINK 且 tid 为空的
 * 条目（如赛季倒计时卡）即运营位；IMG_TEXT 且 tid 非空的才是可开帖的资讯。
 * 解析层不过滤，由 UI 按 [HupuBoardNews.openableAsThread] 决定展示策略。
 */
object HupuBoardApi {

    private const val NEWS_BASE = "https://games.mobileapi.hupu.com"
    private const val NEWS_PATH = "/1/8.2.58/basketballapi/news/v2/newsList"

    /** 国际足球板块 SSR 资讯页 */
    private const val SOCCER_HOME_URL = "https://m.hupu.com/soccer"

    /** NBA 积分榜 SSR 页（players_table 纯 HTML 表格） */
    private const val NBA_STANDINGS_URL = "https://nba.hupu.com/standings"

    /** 足球数据服务（football-api，匿名 GET 可用） */
    private const val FOOTBALL_BASE = "https://football-api.hupu.com/3/8.0.73/football"

    /** 与 HupuMatchApi 一致：PC UA 走 mobileapi 无妨（实测）。 */
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // 共享 client（HupuHttp）——连接池/线程池复用
    private val client: OkHttpClient = HupuHttp.client

    /**
     * 拉取某板块的资讯列表（JSON 文本）。失败返回 null。
     *
     * @param tagCode 板块码（nba/lol/cba/epl/csl/digital…）
     * @param preCount 翻页偏移：已展示条数；首页传 0
     * @param anchorNid 翻页锚点：首页返回的首条 nid；首页传空
     */
    suspend fun fetchBoardNews(
        tagCode: String,
        preCount: Int = 0,
        anchorNid: String = "",
        forceNetwork: Boolean = false,
    ): String? {
        if (tagCode.isBlank()) return null
        val cacheKey = "board-news-$tagCode-$preCount"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val url = "$NEWS_BASE$NEWS_PATH?tagCode=$tagCode&preCount=$preCount&newId=$anchorNid"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!HupuMatchApi.isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 国际足球资讯页 HTML（m.hupu.com/soccer，Next.js SSR）。
     * 资讯在 `__NEXT_DATA__.props.pageProps.news`（与 newsList 同结构，另含 schedules）。
     * 失败返回 null。
     */
    suspend fun fetchSoccerNewsHtml(forceNetwork: Boolean = false): String? {
        val cacheKey = "board-soccer-news-html"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val request = Request.Builder()
                .url(SOCCER_HOME_URL)
                .header("User-Agent", HupuApi.MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    // 必须含 SSR 数据（否则是被风控/降级的壳页，直接当失败）
                    if (!html.contains("__NEXT_DATA__")) return@use null
                    HupuCache.put(cacheKey, html)
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * NBA 积分榜页 HTML（nba.hupu.com/standings，SSR 表格 players_table）。
     * 失败返回 null。
     */
    suspend fun fetchNbaStandingsHtml(forceNetwork: Boolean = false): String? {
        val cacheKey = "board-standings-nba"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val request = Request.Builder()
                .url(NBA_STANDINGS_URL)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    if (!html.contains("players_table")) return@use null
                    HupuCache.put(cacheKey, html)
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 足球赛事的赛季列表（subDataCategory）。result[] 按新到旧排列，
     * 每项 seasonId + categorys（积分榜/球员榜/球队榜入口）。失败返回 null。
     */
    suspend fun fetchSoccerSeasonsJson(competitionId: String, forceNetwork: Boolean = false): String? =
        fetchFootballJson(
            "board-fb-seasons-$competitionId",
            "$FOOTBALL_BASE/category/subDataCategory?competitionId=$competitionId",
            forceNetwork,
        )

    /**
     * 足球积分榜 JSON（competition/ranking）。实测参数三件套：
     * leagueId=competitionId、seasonId、hupuCompetitionId=competitionId，缺一不可。
     * 失败返回 null。
     */
    suspend fun fetchSoccerRankingJson(
        competitionId: String,
        seasonId: String,
        forceNetwork: Boolean = false,
    ): String? =
        fetchFootballJson(
            "board-fb-rank-$competitionId-$seasonId",
            "$FOOTBALL_BASE/competition/ranking?leagueId=$competitionId" +
                "&seasonId=$seasonId&hupuCompetitionId=$competitionId",
            forceNetwork,
        )

    private suspend fun fetchFootballJson(
        cacheKey: String,
        url: String,
        forceNetwork: Boolean,
    ): String? {
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://games.mobileapi.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!HupuMatchApi.isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
