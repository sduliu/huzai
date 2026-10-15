package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * board：积分榜解析单测。
 * Fixture 取自 2026-10 实测：
 *   NBA：nba.hupu.com/standings 的 players_table 片段（东部组头 + 表头 + 雄鹿/步行者行）
 *   足球：football-api competition/ranking（英超 81，2026-27 赛季）
 */
class HupuStandingParseTest {

    private val nbaHtml = """
        <div class="rank_data">
        <table class="players_table">
        <tbody>
        <tr class="linglei"><td colspan="14" class="left">东部</td></tr>
        <tr class="color_font1 bg_a"><td width="60">排名</td><td width="292" class="left">队名</td>
        <td width="40">胜</td><td width="40">负</td><td width="40">胜率</td><td width="100">胜场差</td>
        <td width="60">主场</td><td width="60">客场</td><td width="60">赛区</td><td width="60">东部</td>
        <td width="60">得分</td><td width="60">失分</td><td width="40">净胜</td><td width="100">连胜/负</td></tr>
        <tr><td><span class="rank_bg">1</span></td>
        <td class="left"><a target="_blank" href="https://nba.hupu.com/teams/bucks">雄鹿</a></td>
        <td>52</td><td>30</td><td>63.4%</td><td>0.0</td><td>28-13</td><td>24-17</td><td>11-5</td><td>33-19</td>
        <td>117.2</td><td>113.0</td><td>4.2</td><td>3连胜</td></tr>
        <tr><td><span class="rank_bg">2</span></td>
        <td class="left"><a target="_blank" href="https://nba.hupu.com/teams/pacers">步行者</a></td>
        <td>50</td><td>32</td><td>61.0%</td><td>2.0</td><td>26-15</td><td>24-17</td><td>10-6</td><td>30-22</td>
        <td>117.5</td><td>114.3</td><td>3.2</td><td>1连败</td></tr>
        <tr class="linglei"><td colspan="14" class="left">西部</td></tr>
        <tr><td><span class="rank_bg">1</span></td>
        <td class="left"><a target="_blank" href="https://nba.hupu.com/teams/nuggets">掘金</a></td>
        <td>50</td><td>32</td><td>61.0%</td><td>0.0</td><td>26-15</td><td>24-17</td><td>10-6</td><td>33-19</td>
        <td>115.3</td><td>112.7</td><td>2.6</td><td>2连胜</td></tr>
        </tbody></table></div>
    """.trimIndent()

    private val soccerJson = """
        {"code":200,"msg":"success","data":{"leagueChinese":"英超","rankData":[
          {"groupNum":0,"title":"联赛排名","data":[
            {"ranking":1,"teamId":"135","teamName":"曼彻斯特城",
             "teamLogo":"https://assets-football.hoopchina.com.cn/a.png",
             "played":6,"wins":6,"ties":0,"losses":0,"goalsFor":16,"goalsAgainst":5,
             "points":18,"color":"#FFEBEB"},
            {"ranking":2,"teamId":"143","teamName":"阿森纳",
             "teamLogo":"","played":6,"wins":5,"ties":1,"losses":0,"goalsFor":14,
             "goalsAgainst":4,"points":16,"color":""}
          ]}
        ]}}
    """.trimIndent()

    private val seasonsJson = """
        {"status":200,"msg":"success","result":[
          {"seasonId":61319,"seasonName":"2026-2027","categorys":[]},
          {"seasonId":57093,"seasonName":"2025-2026","categorys":[]}
        ]}
    """.trimIndent()

    @Test
    fun nba_parsesGroupsAndRows() {
        val s = HupuStandingParser.parseNbaStandings(nbaHtml)
        assertNotNull(s)
        assertEquals("NBA", s!!.league)
        assertEquals(2, s.groups.size)
        assertEquals("东部", s.groups[0].title)
        assertEquals("西部", s.groups[1].title)
        val e0 = s.groups[0].rows[0]
        assertEquals("1", e0.rank)
        assertEquals("雄鹿", e0.team)
        // cells 与 columns 对齐：胜/负/胜率/胜场差/近况
        assertEquals(listOf("52", "30", "63.4%", "0.0", "3连胜"), e0.cells)
        assertEquals(listOf("胜", "负", "胜率", "胜场差", "近况"), s.groups[0].columns)
        assertEquals("掘金", s.groups[1].rows[0].team)
    }

    @Test
    fun nba_noTable_returnsNull() {
        assertNull(HupuStandingParser.parseNbaStandings("<html><body>empty</body></html>"))
    }

    @Test
    fun soccer_parsesGroupsRowsColor() {
        val s = HupuStandingParser.parseSoccerStandings(soccerJson)
        assertNotNull(s)
        assertEquals("英超", s!!.league)
        assertEquals(1, s.groups.size)
        assertEquals("联赛排名", s.groups[0].title)
        val r0 = s.groups[0].rows[0]
        assertEquals("曼彻斯特城", r0.team)
        assertEquals("1", r0.rank)
        assertEquals(listOf("6", "6", "0", "0", "16:5", "18"), r0.cells)
        assertEquals("#FFEBEB", r0.color)
        assertEquals(listOf("赛", "胜", "平", "负", "进/失", "积分"), s.groups[0].columns)
    }

    @Test
    fun soccer_badJson_returnsNull() {
        assertNull(HupuStandingParser.parseSoccerStandings("{broken"))
        assertNull(HupuStandingParser.parseSoccerStandings("""{"code":200,"data":{}}"""))
    }

    @Test
    fun season_firstIsLatest() {
        assertEquals("61319", HupuStandingParser.parseSoccerSeasonId(seasonsJson))
        assertNull(HupuStandingParser.parseSoccerSeasonId("""{"status":200,"result":[]}"""))
    }
}
