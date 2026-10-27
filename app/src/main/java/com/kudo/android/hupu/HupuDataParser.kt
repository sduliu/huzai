package com.kudo.android.hupu

import org.json.JSONObject

/**
 * 批B：联赛数据接口响应解析（与 [HupuDataApi] 一一对应）。
 * 所有字段映射以实测响应为准（2026-10-26 匿名 GET）：
 *   · getDataTabList        → result[]{tabId,tabName,tabLinkType,tabUrl}
 *   · teamStandingList      → result{rankTypeListMap{E,W}[],divRankTypeListMap{6 分区}[],
 *                             competitionSeasonStageType}
 *   · switchableRankSeasonList → result.seasonData[]{season,shortSeason,seasonType,
 *                             seasonTypeName,select}
 *   · teamSeasonStatsAllRank → result[]{name,rankType,data[]{rank,teamName,logoUrl,value}}
 *   · playerSeasonRank      → result{dimensionList[]{engName,chineseName,itemList[]
 *                             {engName,chineseName,rankInfoList[]}},competitionSeasonStageType}
 *   · players/draft         → result.draftMap{"1"[],"2"[]}
 *   · allInjuryList         → result{playerInjuryDTOList[],teamInjuryPlayerListMap}
 *   · teamMatchQuarterStats → result{homeQuarterScore[],awayQuarterScore[]}
 * 失败返回 null / 空结构；不做任何数据编造。
 */
object HupuDataParser {

    /** getDataTabList → tab 轨道（官方顺序原样保留）。 */
    fun parseDataTabs(json: String): List<HupuDataTab> {
        return try {
            val arr = JSONObject(json).optJSONArray("result") ?: return emptyList()
            val out = mutableListOf<HupuDataTab>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val name = t.optString("tabName")
                if (name.isEmpty()) continue
                out += HupuDataTab(
                    id = t.optString("tabId", ""),
                    name = name,
                    linkType = t.optString("tabLinkType"),
                    url = t.optString("tabUrl"),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** switchableRankSeasonList → 赛季选项表（selected=true 为服务端当前季）。 */
    fun parseSeasons(json: String): List<HupuSeasonOption> {
        return try {
            val arr = JSONObject(json).optJSONObject("result")
                ?.optJSONArray("seasonData") ?: return emptyList()
            val out = mutableListOf<HupuSeasonOption>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                out += HupuSeasonOption(
                    season = s.optString("season"),
                    shortSeason = s.optString("shortSeason"),
                    seasonType = s.optString("seasonType"),
                    seasonTypeName = s.optString("seasonTypeName"),
                    selected = s.optBoolean("select", false),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * teamStandingList → 东/西/分区三类 + 当前赛季阶段。
     * 行字段：rank/teamName/teamShortName/logoLink/won/lost/winRate/gb/strk/homeWins/homeLosses/
     *        playoffsCertification → 复用 HupuStandingGroup/Row（列：胜 负 胜率 胜差 连胜）。
     */
    fun parseStandingsBoard(json: String): HupuStandingsBoard? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return null
            val r = root.optJSONObject("result") ?: return null
            val stage = r.optJSONObject("competitionSeasonStageType")
            val seasonType = stage?.optJSONObject("competitionSeasonType")
            val leagueName = seasonType?.optJSONObject("competitionType")
                ?.optString("leagueTypeName").orEmpty()
            val season = seasonType?.optString("season").orEmpty()
            val stageName = stage?.optString("competitionStageTypeName").orEmpty()

            fun row(o: JSONObject): HupuStandingRow {
                val strk = o.optString("strk")
                val strkDesc = when {
                    strk.toIntOrNull() == null || strk == "0" -> "-"
                    strk.toInt() > 0 -> "${strk}连胜"
                    else -> "${-strk.toInt()}连败"
                }
                return HupuStandingRow(
                    rank = o.optString("rank"),
                    team = o.optString("teamShortName").ifEmpty { o.optString("teamName") },
                    teamLogo = o.optString("logoLink"),
                    // 批X8：官方列头「胜-负/胜率/胜场差/近况」——胜负合并一格「3-0」，近况=连胜/连败文案
                    cells = listOf(
                        "${o.optString("won")}-${o.optString("lost")}",
                        o.optString("winRate"),
                        o.optString("gb"),
                        strkDesc,
                    ),
                )
            }
            fun group(title: String, arr: org.json.JSONArray?): HupuStandingGroup? {
                if (arr == null || arr.length() == 0) return null
                val rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::row) }
                return if (rows.isEmpty()) null
                else HupuStandingGroup(title, listOf("胜-负", "胜率", "胜场差", "近况"), rows)
            }

            val categories = mutableListOf<HupuRankCategory>()
            r.optJSONObject("rankTypeListMap")?.let { m ->
                for (key in m.keys()) {
                    val arr = m.optJSONArray(key) ?: continue
                    val title = arr.optJSONObject(0)?.optString("rankTypeDesc")
                        .orEmpty().ifEmpty { key }
                    group(title, arr)?.let { categories += HupuRankCategory(key, title, listOf(it)) }
                }
            }
            // 六分区：divRankTypeListMap 的 key 是分区英文名（官方分组序）
            r.optJSONObject("divRankTypeListMap")?.let { m ->
                val groups = mutableListOf<HupuStandingGroup>()
                for (key in m.keys()) {
                    group(divName(key), m.optJSONArray(key))?.let { groups += it }
                }
                if (groups.isNotEmpty()) {
                    categories += HupuRankCategory("divRank", "分区排行", groups)
                }
            }
            HupuStandingsBoard(
                leagueName = leagueName,
                season = season,
                stageName = stageName,
                categories = categories,
                statCategories = emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** teamSeasonStatsAllRank → 「技术统计」类别（每项一个分组，top5）。 */
    fun parseTeamStatRanks(json: String): List<HupuStandingGroup> {
        return try {
            val arr = JSONObject(json).optJSONArray("result") ?: return emptyList()
            val out = mutableListOf<HupuStandingGroup>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val data = s.optJSONArray("data") ?: continue
                val rows = mutableListOf<HupuStandingRow>()
                for (j in 0 until data.length()) {
                    val d = data.optJSONObject(j) ?: continue
                    rows += HupuStandingRow(
                        rank = d.optString("rank"),
                        team = d.optString("teamShortName").ifEmpty { d.optString("teamName") },
                        teamLogo = d.optString("logoUrl"),
                        cells = listOf(d.optString("value")),
                    )
                }
                if (rows.isNotEmpty()) {
                    val rt = s.optString("rankType")
                    out += HupuStandingGroup(
                        title = s.optString("name").ifEmpty { rt },
                        // 批X8：单项统计左栏独立类别；数据列头按 rankType 语义（Avg→场均，其余→数值）
                        columns = listOf(if (rt.endsWith("Avg")) "场均" else "数值"),
                        rows = rows,
                    )
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** playerSeasonRank → 维度→统计项→top5。 */
    fun parsePlayerRankBoard(json: String): HupuPlayerRankBoard? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return null
            val r = root.optJSONObject("result") ?: return null
            val stage = r.optJSONObject("competitionSeasonStageType")
            val season = stage?.optJSONObject("competitionSeasonType")
                ?.optString("season").orEmpty()
            val stageName = stage?.optString("competitionStageTypeName").orEmpty()
            val dims = mutableListOf<HupuRankDimension>()
            r.optJSONArray("dimensionList")?.let { dl ->
                for (i in 0 until dl.length()) {
                    val d = dl.optJSONObject(i) ?: continue
                    val items = mutableListOf<HupuRankItem>()
                    d.optJSONArray("itemList")?.let { il ->
                        for (j in 0 until il.length()) {
                            val it = il.optJSONObject(j) ?: continue
                            val leaders = mutableListOf<HupuRankEntry>()
                            it.optJSONArray("rankInfoList")?.let { rl ->
                                for (k in 0 until rl.length()) {
                                    val e = rl.optJSONObject(k) ?: continue
                                    leaders += rankEntry(e)
                                }
                            }
                            if (leaders.isNotEmpty()) {
                                items += HupuRankItem(
                                    key = it.optString("engName"),
                                    name = it.optString("chineseName"),
                                    leaders = leaders,
                                    hasMore = it.optBoolean("hasMore"),
                                )
                            }
                        }
                    }
                    if (items.isNotEmpty()) {
                        dims += HupuRankDimension(
                            key = d.optString("engName"),
                            name = d.optString("chineseName"),
                            items = items,
                        )
                    }
                }
            }
            HupuPlayerRankBoard(season, stageName, dims)
        } catch (e: Exception) {
            null
        }
    }

    private fun rankEntry(e: JSONObject): HupuRankEntry = HupuRankEntry(
        rank = e.optInt("rank"),
        playerId = e.optString("playerId"),
        name = e.optString("playerName"),
        teamName = e.optString("teamShortName").ifEmpty { e.optString("teamName") },
        photo = e.optString("photo"),
        value = e.optString("value"),
    )

    /** 批F-1：playerSingleDimensionSeasonRank → 单维度单页榜；成功响应空页返回空 leaders（=榜尾），非 JSON/失败才 null。 */
    fun parsePlayerRankPage(json: String): HupuRankItem? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return null
            val r = root.optJSONObject("result") ?: return null
            val leaders = mutableListOf<HupuRankEntry>()
            r.optJSONArray("rankInfoList")?.let { rl ->
                for (i in 0 until rl.length()) {
                    leaders += rankEntry(rl.optJSONObject(i) ?: continue)
                }
            }
            HupuRankItem(
                key = r.optString("engName"),
                name = r.optString("chineseName"),
                leaders = leaders,
                hasMore = r.optBoolean("hasMore"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** draftYearConfig → 年份表（降序，原样保留）。 */
    fun parseDraftYears(json: String): List<String> {
        return try {
            val arr = JSONObject(json).optJSONArray("result") ?: return emptyList()
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotEmpty) }
                .sortedDescending()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** players/draft → 按轮次分组的顺位表（draftMap 的 key=轮次字符串）。 */
    fun parseDraftBoard(json: String, year: String): List<Pair<Int, List<HupuDraftPick>>> {
        return try {
            val map = JSONObject(json).optJSONObject("result")
                ?.optJSONObject("draftMap") ?: return emptyList()
            val rounds = mutableListOf<Pair<Int, List<HupuDraftPick>>>()
            for (key in map.keys()) {
                val roundNo = key.toIntOrNull() ?: continue
                val arr = map.optJSONArray(key) ?: continue
                val picks = mutableListOf<HupuDraftPick>()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    picks += HupuDraftPick(
                        round = p.optInt("round", roundNo),
                        rank = p.optInt("rank"),
                        playerId = p.optString("playerId"),
                        name = p.optString("playerName"),
                        photo = p.optString("photo"),
                        teamName = p.optString("draftTeamName").ifEmpty { p.optString("teamName") },
                        position = p.optString("position"),
                        heightCm = p.optInt("height"),
                    )
                }
                if (picks.isNotEmpty()) rounds += roundNo to picks.sortedBy { it.rank }
            }
            rounds.sortedBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** allInjuryList → 平铺伤病表（DTO 主列表优先；空时回退 teamMap 聚合）。 */
    fun parseInjuries(json: String): List<HupuInjury> {
        fun from(o: JSONObject) = HupuInjury(
            playerId = o.optString("playerId"),
            name = o.optString("playerName"),
            photo = o.optString("photo"),
            teamName = o.optString("teamName"),
            part = o.optString("injuryPart"),
            status = o.optString("injuryStatus"),
            date = o.optString("injuryDate").take(10),
            detail = o.optString("zhDetail").takeIf { it != "null" }.orEmpty(),
        )
        return try {
            val r = JSONObject(json).optJSONObject("result") ?: return emptyList()
            val out = mutableListOf<HupuInjury>()
            r.optJSONArray("playerInjuryDTOList")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += from(it) }
            }
            if (out.isEmpty()) {
                r.optJSONObject("teamInjuryPlayerListMap")?.let { m ->
                    for (k in m.keys()) {
                        m.optJSONArray(k)?.let { arr ->
                            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += from(it) }
                        }
                    }
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 批Y：allPlayerAwardList → 奖项分组（awardDesc 题名 + 近年 top5 获奖者）。
     * pts/reb/asts 是 JSON 数字（如 31.132），保留 1 位小数文案与官方一致。
     */
    fun parseAwards(json: String): List<HupuAward> {
        fun num(o: JSONObject, key: String): String {
            val v = o.opt(key) ?: return ""
            return when (v) {
                is Number -> {
                    val d = v.toDouble()
                    if (d == d.toLong().toDouble()) "${v.toLong()}" else String.format("%.1f", d)
                }
                else -> v.toString().takeUnless { it == "null" } ?: ""
            }
        }
        return try {
            val arr = JSONObject(json).optJSONObject("result")
                ?.optJSONArray("playerAwardMapDTOList") ?: return emptyList()
            val out = mutableListOf<HupuAward>()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val winners = mutableListOf<HupuAwardWinner>()
                a.optJSONArray("playerAwardDetailDTOList")?.let { wl ->
                    for (j in 0 until wl.length()) {
                        val w = wl.optJSONObject(j) ?: continue
                        winners += HupuAwardWinner(
                            playerId = w.optString("playerId"),
                            name = w.optString("playerName"),
                            photo = w.optString("photo"),
                            teamName = w.optString("teamName"),
                            season = w.optString("awardDate").ifEmpty { w.optString("season") },
                            pts = num(w, "pts"),
                            reb = num(w, "reb"),
                            asts = num(w, "asts"),
                        )
                    }
                }
                if (winners.isNotEmpty()) {
                    out += HupuAward(
                        awardName = a.optString("awardName"),
                        awardDesc = a.optString("awardDesc").ifEmpty { a.optString("awardName") },
                        winners = winners,
                    )
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 批Y：playerAllDimensionCareerRank → 生涯榜整版。
     * dataDimensionMap[stage] 是单元素维度组数组（pergame→avg / totals→all），
     * itemList 与 playerSeasonRank 同构；官方页过滤三个足球残留项，此处同款。
     */
    private val careerRankItemFilter = setOf("场均进球数", "场均命中率", "场均罚球命中数")

    fun parseCareerRank(json: String, stage: String, grain: String): HupuCareerRankBoard? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return null
            val r = root.optJSONObject("result") ?: return null
            val stageArr = r.optJSONObject("dataDimensionMap")
                ?.optJSONArray(stage) ?: return null
            val dims = mutableListOf<HupuRankDimension>()
            for (i in 0 until stageArr.length()) {
                val d = stageArr.optJSONObject(i) ?: continue
                val items = mutableListOf<HupuRankItem>()
                d.optJSONArray("itemList")?.let { il ->
                    for (j in 0 until il.length()) {
                        val it = il.optJSONObject(j) ?: continue
                        val cn = it.optString("chineseName")
                        if (cn in careerRankItemFilter) continue
                        val leaders = mutableListOf<HupuRankEntry>()
                        it.optJSONArray("rankInfoList")?.let { rl ->
                            for (k in 0 until rl.length()) {
                                rl.optJSONObject(k)?.let { e -> leaders += rankEntry(e) }
                            }
                        }
                        if (leaders.isNotEmpty()) {
                            items += HupuRankItem(
                                key = it.optString("engName"),
                                name = cn,
                                leaders = leaders,
                                hasMore = it.optBoolean("hasMore"),
                            )
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    dims += HupuRankDimension(
                        key = d.optString("name"),
                        name = d.optString("chineseName"),
                        items = items,
                    )
                }
            }
            if (dims.isEmpty()) return null
            HupuCareerRankBoard(
                stage = stage,
                stageName = if (stage == "PLAYOFF") "季后赛" else "常规赛",
                grain = grain,
                dimensions = dims,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 批Y：against-plan-chart → 季后赛对阵图。planChartItemList 平铺格，
     * planChartNumber="行-列" 定 bracket 位置；result=null/无格 → null（官方数据空）。
     */
    fun parseAgainstChart(json: String): HupuAgainstBracket? {
        return try {
            val r = JSONObject(json).optJSONObject("result") ?: return null
            val arr = r.optJSONArray("planChartItemList") ?: return null
            val cells = mutableListOf<HupuAgainstCell>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pos = o.optString("planChartNumber").split("-")
                val row = pos.getOrNull(0)?.toIntOrNull() ?: continue
                val col = pos.getOrNull(1)?.toIntOrNull() ?: continue
                cells += HupuAgainstCell(
                    row = row, col = col,
                    teamId = o.optString("teamId"),
                    teamName = o.optString("teamName"),
                    teamLogo = o.optString("teamLogo"),
                    teamScore = o.optString("score"),
                    rank = o.optString("rank"),
                    oppoTeamId = o.optString("oppoTeamId"),
                    oppoTeamName = o.optString("oppoTeamName"),
                    oppoTeamLogo = o.optString("oppoTeamLogo"),
                    oppoScore = o.optString("oppoScore"),
                    winnerTeamId = o.optString("winnerTeamId"),
                )
            }
            if (cells.isEmpty()) null else HupuAgainstBracket(cells)
        } catch (e: Exception) {
            null
        }
    }

    /** teamMatchQuarterStats → 单节比分（Q1..Qn 原样数组；OT 节由数组长度>4 表达）。 */
    fun parseQuarterStats(json: String): HupuQuarterScore? {
        return try {
            val r = JSONObject(json).optJSONObject("result") ?: return null
            fun list(key: String): List<String>? =
                r.optJSONArray(key)?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                } ?: return null
            val home = list("homeQuarterScore").orEmpty()
            val away = list("awayQuarterScore").orEmpty()
            if (home.isEmpty() && away.isEmpty()) return null
            HupuQuarterScore(home, away)
        } catch (e: Exception) {
            null
        }
    }

    /** 批U-1：basketballapi/matchInfo → 比赛详情头字段。 */
    fun parseMatchInfo(json: String): HupuMatchInfo? {
        return try {
            val r = JSONObject(json).optJSONObject("result") ?: return null
            if (r.optString("matchId").isEmpty()) return null
            fun str(key: String): String =
                r.optString(key).takeUnless { it == "null" } ?: ""
            // rank/area 官方在部分场次给 JSON null（optString→"null" 字面量，先判空）
            fun opt(key: String): String {
                val v = r.opt(key)
                return when (v) {
                    null, JSONObject.NULL -> ""
                    is Number -> v.toString()
                    else -> v.toString()
                }
            }
            HupuMatchInfo(
                matchId = str("matchId"),
                matchStatus = str("matchStatus"),
                matchStatusChinese = str("matchStatusChinese"),
                matchTime = str("matchTime"),
                chinaStartTime = r.optLong("chinaStartTime", 0L),
                homeScore = opt("homeScore"),
                awayScore = opt("awayScore"),
                homeTeamName = str("homeTeamName"),
                awayTeamName = str("awayTeamName"),
                homeTeamLogo = str("homeTeamLogo").ifEmpty { null },
                awayTeamLogo = str("awayTeamLogo").ifEmpty { null },
                homeTeamDayColor = str("homeTeamDayColor"),
                awayTeamDayColor = str("awayTeamDayColor"),
                homeRank = opt("homeRank"),
                awayRank = opt("awayRank"),
                homeArea = str("homeArea"),
                awayArea = str("awayArea"),
                attendance = r.optLong("attendance", 0L),
                costTime = str("costTime"),
                scoreText = str("score"),
                season = str("season"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 批U-3：basketballapi/playerMatchStats → 主客两队数据行。 */
    fun parseMatchPlayerStats(json: String): HupuMatchPlayerStats? {
        return try {
            val r = JSONObject(json).optJSONObject("result") ?: return null
            fun player(o: JSONObject) = HupuMatchPlayerStat(
                playerId = o.optString("playerId"),
                name = o.optString("name"),
                logoUrl = o.optString("logoUrl").takeUnless { it == "null" || it.isEmpty() },
                position = o.optString("position"),
                starter = o.optInt("starter", 0) == 1,
                mins = o.optString("mins"),
                pts = o.opt("pts")?.toString() ?: "",
                reb = o.opt("reb")?.toString() ?: "",
                asts = o.opt("asts")?.toString() ?: "",
                userScoreAvg = o.opt("userScoreAvg")?.toString()?.takeUnless { it == "null" } ?: "",
                userScoreCount = o.optLong("userScoreCount", 0L),
            )
            fun team(key: String): List<HupuMatchPlayerStat> =
                r.optJSONArray(key)?.let { a ->
                    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::player) }
                } ?: emptyList()
            val home = team("homePlayers")
            val away = team("awayPlayers")
            if (home.isEmpty() && away.isEmpty()) return null
            HupuMatchPlayerStats(
                matchId = r.optString("matchId"),
                home = home,
                away = away,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun divName(key: String): String = when (key.lowercase()) {
        "atlantic" -> "大西洋分区"
        "central" -> "中部分区"
        "southeast" -> "东南分区"
        "northwest" -> "西北分区"
        "pacific" -> "太平洋分区"
        "southwest" -> "西南分区"
        else -> key
    }
}
