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
                    cells = listOf(
                        o.optString("won"),
                        o.optString("lost"),
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
                else HupuStandingGroup(title, listOf("胜", "负", "胜率", "胜差", "连胜"), rows)
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
                    out += HupuStandingGroup(
                        title = s.optString("name").ifEmpty { s.optString("rankType") },
                        columns = listOf("值"),
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
                                    leaders += HupuRankEntry(
                                        rank = e.optInt("rank"),
                                        playerId = e.optString("playerId"),
                                        name = e.optString("playerName"),
                                        teamName = e.optString("teamShortName")
                                            .ifEmpty { e.optString("teamName") },
                                        photo = e.optString("photo"),
                                        value = e.optString("value"),
                                    )
                                }
                            }
                            if (leaders.isNotEmpty()) {
                                items += HupuRankItem(
                                    key = it.optString("engName"),
                                    name = it.optString("chineseName"),
                                    leaders = leaders,
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
