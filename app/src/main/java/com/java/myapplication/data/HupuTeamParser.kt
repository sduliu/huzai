package com.java.myapplication.data

/**
 * 球队页 SSR 解析（nba.hupu.com/teams|schedule/{slug}）。
 * 页面为静态表格，结构实测见各方法注释。
 */
object HupuTeamParser {

    /**
     * /teams/{slug} → 球队信息 + 阵容。
     * 结构：
     *   · `title-text` span：「迈阿密热火（Miami Heat）」
     *   · `.content .font` 内 `<p>`：信息行，一行可含多对「键：值」（&nbsp; 分隔）
     *   · `.txt` div：球队简介
     *   · `.jiben_title_table .a` x_list 行：号码 + 球员名
     *   · `.jiben_title_table .b` x_list 行：位置/年龄/球龄/身高/体重/薪金（6 格，与 .a 行序对应）
     */
    fun parseNbaTeam(html: String): HupuTeamInfo? {
        val fullName = Regex("""<span class="title-text">([^<]+)</span>""")
            .find(html)?.groupValues?.get(1)?.trim() ?: return null
        val logo = Regex("""<div class="img">\s*<img src="([^"]+)"""")
            .find(html)?.groupValues?.get(1) ?: ""

        // 信息行：.font 块内每个 <p>；一行的多对键值按 &nbsp; 分隔（先转 | 再拆，否则会吞键名）
        val fontBlock = Regex("""<div class="font">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: ""
        val infos = mutableListOf<Pair<String, String>>()
        Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).findAll(fontBlock).forEach { pm ->
            val plain = pm.groupValues[1]
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", "|")
            Regex("""([^：|]+?)：([^：|]*)""").findAll(plain).forEach { kv ->
                val k = kv.groupValues[1].trim()
                val v = kv.groupValues[2].trim()
                if (k.isNotEmpty() && v.isNotEmpty()) infos += k to v
            }
        }
        val intro = Regex("""<div class="txt">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("<[^>]+>"), "") ?: ""

        // 阵容：.a（号码+姓名）与 .b（位置/年龄/球龄/身高/体重/薪金）两表行序对应
        val tableZone = html.substringAfter("""<div class="jiben_title_table">""", "")
        val aZone = tableZone.substringAfter("""<div class="a">""", "")
            .substringBefore("""<div class="b">""")
        val bZone = tableZone.substringAfter("""<div class="b">""", "")

        val names = mutableListOf<Pair<String, String>>() // no to name
        Regex("""<div class="x_list">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(aZone).forEach { rm ->
                val cells = Regex("""<span class="c\d">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(rm.groupValues[1])
                    .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                    .toList()
                if (cells.size >= 2) names += cells[0] to cells[1]
            }
        val stats = mutableListOf<List<String>>()
        Regex("""<div class="x_list">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(bZone).forEach { rm ->
                val cells = Regex("""<span class="c\d">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(rm.groupValues[1])
                    .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim() }
                    .toList()
                // 基本数据行恰好 6 格（位置/年龄/球龄/身高/体重/薪金）；
                // 同区还有 20 列的「比赛数据」表（c5 格）——用 ==6 排除
                if (cells.size == 6) stats += cells
            }
        val roster = names.mapIndexedNotNull { i, (no, name) ->
            val s = stats.getOrNull(i) ?: return@mapIndexedNotNull null
            HupuTeamPlayer(
                no = no, name = name,
                pos = s.getOrElse(0) { "" }, age = s.getOrElse(1) { "" },
                height = s.getOrElse(3) { "" }.substringBefore('/'),
                weight = s.getOrElse(4) { "" }.substringBefore('/'),
            )
        }
        return HupuTeamInfo(fullName = fullName, logo = logo, infos = infos, intro = intro, roster = roster)
    }

    /**
     * /schedule/{slug} → 赛程表行序列表。
     * 行结构（<tr class="left">）：
     *   td1 `客队&nbsp;vs&nbsp;主队`（本队链接带 font-weight:bold）
     *   td2 比分 `129&nbsp;-&nbsp;105` / `-`
     *   td3 结果（本队视角）胜/负/-
     *   td4 北京时间 `2026-10-04 07:00:00`
     */
    fun parseNbaTeamSchedule(html: String): List<HupuTeamGame> {
        val out = mutableListOf<HupuTeamGame>()
        Regex("""<tr class="left">(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).forEach { rm ->
                val row = rm.groupValues[1]
                val teams = Regex(
                    """<a href="https://nba\.hupu\.com/teams/([a-z0-9]+)"[^>]*?(style="font-weight:bold;")?\s*>([^<]+)</a>"""
                ).findAll(row).map { Triple(it.groupValues[3].trim(), it.groupValues[2].isNotEmpty(), it.groupValues[1]) }
                    .toList()
                if (teams.size < 2) return@forEach
                val selfFirst = teams[0].second
                val opponent = (if (selfFirst) teams[1] else teams[0]).first
                val tds = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(row).map { it.groupValues[1] }.toList()
                val score = tds.getOrNull(1)
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.replace("&nbsp;", "")
                    ?.replace(Regex("\\s+"), "")
                    ?.trim() ?: ""
                val result = tds.getOrNull(2)
                    ?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
                val dateTime = tds.getOrNull(3)
                    ?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
                if (dateTime.isEmpty()) return@forEach
                out += HupuTeamGame(
                    dateTime = dateTime,
                    opponent = opponent,
                    isHome = !selfFirst,
                    score = score,
                    result = result,
                )
            }
        return out
    }
}
