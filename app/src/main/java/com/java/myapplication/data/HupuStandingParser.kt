package com.java.myapplication.data

import org.json.JSONObject

/**
 * board：积分榜解析。
 *
 * 两条链路（均实测，2026-10）：
 *  · NBA：nba.hupu.com/standings 的 SSR `<table class="players_table">` 纯 HTML。
 *    行结构：组头行（单 td colspan，文本=「东部/西部」）→ 表头行（首格=排名）
 *    → 数据行（14 列：排名/队名/胜/负/胜率/胜场差/主场/客场/赛区/分区/得分/失分/净胜/连胜）。
 *    移动端只保留 [NBA_COLS] 子集。
 *  · 足球：football-api `football/competition/ranking` JSON
 *    （data.leagueChinese + data.rankData[]，每组 title + data[] 行）。
 *    rank 色标（欧冠/降级区底色）走 [HupuStandingRow.color]。
 */
object HupuStandingParser {

    /** NBA 表格 14 列中要展示的列（名称 → 原表列下标）。 */
    private val NBA_COLS = linkedMapOf(
        "胜" to 2, "负" to 3, "胜率" to 4, "胜场差" to 5, "近况" to 13,
    )

    private val TR = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val TD = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]+>")
    private val TEAM_A = Regex("<a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)

    /**
     * NBA 积分榜（SSR 表格）。解析失败/无表格返回 null；解析成功但无数据行返回空组列表的壳。
     */
    fun parseNbaStandings(html: String): HupuStandings? {
        if (!html.contains("players_table")) return null
        val groups = mutableListOf<HupuStandingGroup>()
        var curTitle = ""
        var curRows = mutableListOf<HupuStandingRow>()
        fun flush() {
            if (curRows.isNotEmpty()) {
                groups += HupuStandingGroup(curTitle, NBA_COLS.keys.toList(), curRows)
                curRows = mutableListOf()
            }
        }
        for (tr in TR.findAll(html)) {
            val tds = TD.findAll(tr.value).map { td ->
                TAG.replace(td.groupValues[1], "").trim()
            }.toList()
            if (tds.isEmpty()) continue
            if (tds.size == 1) {          // 组头行（东部/西部）
                flush()
                curTitle = tds[0]
                continue
            }
            if (tds[0] == "排名") continue // 表头行
            if (tds.size < 14) continue
            val team = TEAM_A.find(tr.value)?.groupValues?.get(1)
                ?.let { TAG.replace(it, "").trim() }
                ?: tds[1]
            curRows += HupuStandingRow(
                rank = tds[0],
                team = team,
                cells = NBA_COLS.values.map { tds[it] },
            )
        }
        flush()
        return if (groups.isEmpty()) null else HupuStandings("NBA", groups)
    }

    /**
     * 足球积分榜 JSON（competition/ranking）。失败/无 rankData 返回 null。
     * 分组结构：rankData[] = {title:"联赛排名"|"A组"…, data:[{ranking,teamName,…}]}
     */
    fun parseSoccerStandings(json: String): HupuStandings? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val data = root.optJSONObject("data") ?: return null
        val rankData = data.optJSONArray("rankData") ?: return null
        val league = data.optString("leagueChinese")
        val cols = listOf("赛", "胜", "平", "负", "进/失", "积分")
        val groups = mutableListOf<HupuStandingGroup>()
        for (i in 0 until rankData.length()) {
            val g = rankData.optJSONObject(i) ?: continue
            val arr = g.optJSONArray("data") ?: continue
            val rows = mutableListOf<HupuStandingRow>()
            for (j in 0 until arr.length()) {
                val o = arr.optJSONObject(j) ?: continue
                rows += HupuStandingRow(
                    rank = o.optString("ranking"),
                    team = o.optString("teamName"),
                    teamLogo = o.optString("teamLogo"),
                    cells = listOf(
                        o.optString("played"),
                        o.optString("wins"),
                        o.optString("ties"),
                        o.optString("losses"),
                        "${o.optString("goalsFor")}:${o.optString("goalsAgainst")}",
                        o.optString("points"),
                    ),
                    color = o.optString("color"),
                )
            }
            if (rows.isNotEmpty()) {
                groups += HupuStandingGroup(g.optString("title"), cols, rows)
            }
        }
        return if (groups.isEmpty()) null else HupuStandings(league, groups)
    }

    /** 足球赛季列表 JSON → 最新赛季 id（result[] 首项的 seasonId，服务端按新到旧排）。 */
    fun parseSoccerSeasonId(json: String): String? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val arr = root.optJSONArray("result") ?: return null
        if (arr.length() == 0) return null
        val id = arr.optJSONObject(0)?.opt("seasonId") ?: return null
        return id.toString().takeIf { it.isNotEmpty() && it != "null" }
    }
}
