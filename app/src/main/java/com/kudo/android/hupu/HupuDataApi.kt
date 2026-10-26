package com.kudo.android.hupu

import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批B：虎扑联赛数据接口层（私有 mobileapi，**全部匿名 GET 只读**）。
 *
 * 实测可达性（2026-10-26，未登录无 Cookie）：
 *   · games.mobileapi.hupu.com 的 basketballapi 系列与 match-api.hupu.com 的
 *     matchallapi 系列**不需要签名**——官方 App 与官方数据 H5 页
 *     （offline-download.hupu.com/online/prod/300003 系列）同源调用，参数照抄 H5 JS 包。
 *   · basketball/public 老路由才走 §9 旧签（实测返回「签名验证不正确」）——本模块
 *     一律不碰签名路由，需要登录态的接口一律不用。
 *
 * 公共参数（官方 H5 JS 实参）：
 *   · 榜单/球员/选秀/伤病：competitionLeagueType + competitionType = 联赛小写（nba/cba）
 *   · 赛季选择器：clientId=106819853 + rankType 枚举（NBA_TEAM_RANK / NBA_PLAYER_RANK）
 *   · 选秀年份：leagueType + competitionType；draftYearConfig 返回可选年份表
 *   · 赛程中心 tab：match-api getDataTabList?categoryType=common&enType=<联赛>
 *   · 全球赛程：match-api oddsScheduleList?code=basketballLottery（官方「全球」tab 的 tabUrl 解码）
 *
 * 所有方法返回 JSON 文本或 null（失败/非 JSON），解析集中在 [HupuDataParser]。
 * 频率策略：与赛程/评分同款的 HupuCache 内存缓存，不批量刷接口。
 */
object HupuDataApi {

    private const val GAMES_BASE = "https://games.mobileapi.hupu.com/1/8.2.66"
    private const val MATCH_BASE = "https://match-api.hupu.com/1/8.2.66"

    /** 官方 App 赛程中心「全球」tab 的 oddsSchedule code（tabUrl 解码自 getDataTabList） */
    const val GLOBAL_BASKETBALL_CODE = "basketballLottery"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 统一 GET（缓存 + JSON 校验 + 异常归零）。cacheKey 必传；forceNetwork 跳过缓存。 */
    private suspend fun get(url: String, cacheKey: String, forceNetwork: Boolean, referer: String): String? {
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", referer)
                .build()
            try {
                HupuHttp.client.newCall(request).execute().use { response ->
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
     * 联赛数据 tab 列表（官方赛程中心的 tab 轨道：全球|赛程|球队榜|球员榜|选秀|伤病|…）。
     * enType = 联赛小写（nba/cba/epl…）。只回接口真实给出的 tab，不编。
     */
    suspend fun fetchDataTabs(enType: String, forceNetwork: Boolean = false): String? =
        get(
            "$MATCH_BASE/matchallapi/getDataTabList?categoryType=common&enType=$enType",
            "data-tabs-$enType", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 「全球」赛程（官方全球 tab：跨联赛比赛日流，行内含 competitionName/subscribeInfo）。
     * code 来自 tab 的 tabUrl query（篮球=basketballLottery）。
     */
    suspend fun fetchGlobalSchedule(code: String, filterCode: String = "all", forceNetwork: Boolean = false): String? =
        get(
            "$MATCH_BASE/matchallapi/oddsScheduleList?code=$code&filterCode=$filterCode",
            "global-schedule-$code-$filterCode", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 球队榜（teamStandingList）：rankTypeListMap 东/西 + divRankTypeListMap 六分区 +
     * competitionSeasonStageType（当前赛季/阶段）。season+competitionStageType 切历史赛季。
     */
    suspend fun fetchTeamStandings(
        leagueType: String,
        season: String = "",
        stageType: String = "",
        forceNetwork: Boolean = false,
    ): String? {
        var url = "$GAMES_BASE/basketballapi/teamStandingList" +
            "?competitionLeagueType=$leagueType&competitionType=$leagueType"
        if (season.isNotEmpty()) url += "&season=$season&competitionStageType=$stageType"
        return get(url, "team-standings-$leagueType-$season-$stageType", forceNetwork, "https://m.hupu.com/")
    }

    /**
     * 赛季选项（switchableRankSeasonList）：rankType 枚举 NBA_TEAM_RANK / NBA_PLAYER_RANK。
     * 官方 H5 实参 clientId=106819853。
     */
    suspend fun fetchRankSeasons(rankType: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/switchableRankSeasonList?clientId=106819853&rankType=$rankType",
            "rank-seasons-$rankType", forceNetwork, "https://m.hupu.com/",
        )

    /** 球队技术统计榜（teamSeasonStatsAllRank）：39 项各 top5，作积分榜左栏「技术统计」。 */
    suspend fun fetchTeamStatRanks(leagueType: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/teamSeasonStatsAllRank" +
                "?competitionLeagueType=$leagueType&competitionType=$leagueType",
            "team-stat-ranks-$leagueType", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 球员榜（playerSeasonRank）：dimensionList（基础/进阶）各统计项 top5。
     * 与官方 player.html 同数据面；单维度全量榜接口参数未明（统计维度空），未接。
     */
    suspend fun fetchPlayerSeasonRank(
        leagueType: String,
        season: String = "",
        stageType: String = "",
        forceNetwork: Boolean = false,
    ): String? {
        var url = "$GAMES_BASE/basketballapi/playerSeasonRank" +
            "?competitionLeagueType=$leagueType&competitionType=$leagueType"
        if (season.isNotEmpty()) url += "&season=$season&competitionStageType=$stageType"
        return get(url, "player-season-rank-$leagueType-$season-$stageType", forceNetwork, "https://m.hupu.com/")
    }

    /**
     * 批F-1：单维度球员全量榜（playerSingleDimensionSeasonRank）——
     * playerSeasonRank 每个统计项的 allUrl（template/index?model=basketball/rank/index#/playerRank）
     * 背后的官方端点，rank/index 页 JS 实参：`pageNo` 分页 20 行/页，拉空页即止
     * （2026-10-26 匿名实测 ptsAvg 连续取到 ~rank300+）。season 空 = 当季。
     */
    suspend fun fetchPlayerSingleDimRank(
        leagueType: String,
        rankType: String,
        season: String = "",
        stageType: String = "",
        pageNo: Int = 1,
        forceNetwork: Boolean = false,
    ): String? {
        var url = "$GAMES_BASE/basketballapi/playerSingleDimensionSeasonRank" +
            "?competitionLeagueType=$leagueType&competitionType=$leagueType" +
            "&rankType=$rankType&playerType=normal&pageNo=$pageNo"
        if (season.isNotEmpty()) url += "&season=$season&competitionStageType=$stageType"
        return get(url, "player-dim-rank-$leagueType-$rankType-$season-$stageType-p$pageNo", forceNetwork, "https://m.hupu.com/")
    }

    /** 选秀年份表（draftYearConfig）。 */
    suspend fun fetchDraftYears(leagueType: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/draftYearConfig?leagueType=$leagueType&competitionType=$leagueType",
            "draft-years-$leagueType", forceNetwork, "https://m.hupu.com/",
        )

    /** 选秀榜（players/draft）：draftMap 按轮次分组。 */
    suspend fun fetchDraft(leagueType: String, year: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/players/draft?draftYear=$year&competitionType=$leagueType",
            "draft-$leagueType-$year", forceNetwork, "https://m.hupu.com/",
        )

    /** 伤病表（allInjuryList）：playerInjuryDTOList + teamInjuryPlayerListMap。 */
    suspend fun fetchInjuries(leagueType: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/allInjuryList?leagueType=$leagueType&competitionType=$leagueType",
            "injuries-$leagueType", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 批Y：荣誉榜（allPlayerAwardList）——官方 honor.html 同源实参 leagueType/competitionType 大写。
     * result.playerAwardMapDTOList：20 个奖项 × 近年 top5 获奖者（pts/reb/asts 随行）。
     */
    suspend fun fetchPlayerAwards(leagueType: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/allPlayerAwardList" +
                "?leagueType=${leagueType.uppercase()}&competitionType=${leagueType.uppercase()}",
            "player-awards-$leagueType", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 单节比分（teamMatchQuarterStats）：home/awayQuarterScore 数组（Q1..Q4+OT）。
     * 仅 businessType=basketball 的 matchId 有效。
     */
    suspend fun fetchQuarterStats(matchId: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/teamMatchQuarterStats?matchId=$matchId",
            "quarter-stats-$matchId", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 批U-1：比赛详情头（basketballapi/matchInfo）——状态中文/时间/排名分区/队色/评分文案。
     * 仅 businessType=basketball 的 matchId 有效。
     */
    suspend fun fetchMatchInfo(matchId: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/matchInfo?matchId=$matchId",
            "match-info-$matchId", forceNetwork, "https://m.hupu.com/",
        )

    /**
     * 批U-3：单场球员统计（basketballapi/playerMatchStats）——主客两队全场数据行。
     * 仅 businessType=basketball 的 matchId 有效。
     */
    suspend fun fetchMatchPlayerStats(matchId: String, forceNetwork: Boolean = false): String? =
        get(
            "$GAMES_BASE/basketballapi/playerMatchStats?matchId=$matchId",
            "match-player-stats-$matchId", forceNetwork, "https://m.hupu.com/",
        )
}
