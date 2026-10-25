package com.kudo.android

import com.kudo.android.hupu.HupuDataParser
import org.junit.Assert.*
import org.junit.Test

/** 批B：HupuDataParser 各端点字段映射（fixture 结构与 2026-10-26 匿名实测响应一致）。 */
class HupuDataParserTest {

    @Test fun dataTabs_parsesOfficialOrderAndLinkTypes() {
        val json = """{"success":true,"result":[
            {"tabId":"global","tabName":"全球","tabLinkType":"oddsSchedule","tabUrl":"huputiyu://commonmatch/oddSchedule?code=basketballLottery"},
            {"tabId":"game","tabName":"赛程","tabLinkType":"schedule","tabUrl":"huputiyu://commonmatch/schedule?categoryType=common&enType=nba"},
            {"tabId":"common_tab_1","tabName":"球队榜","tabLinkType":"h5","tabUrl":"https://offline-download.hupu.com/online/prod/300003/nba/team.html"}
        ]}"""
        val tabs = HupuDataParser.parseDataTabs(json)
        assertEquals(listOf("global", "game", "common_tab_1"), tabs.map { it.id })
        assertEquals(listOf("全球", "赛程", "球队榜"), tabs.map { it.name })
        assertEquals("oddsSchedule", tabs[0].linkType)
        assertEquals("h5", tabs[2].linkType)
        assertEquals(emptyList<Any>(), HupuDataParser.parseDataTabs("""{"success":false}"""))
    }

    @Test fun seasons_parsesSelectedAndType() {
        val json = """{"success":true,"result":{"seasonData":[
            {"season":"2026-2027","shortSeason":"2026-27","seasonType":"REGULAR","seasonTypeName":"常规赛","select":true},
            {"season":"2025-2026","shortSeason":"2025-26","seasonType":"PLAYOFF","seasonTypeName":"季后赛","select":false}
        ]}}"""
        val s = HupuDataParser.parseSeasons(json)
        assertEquals(2, s.size)
        assertTrue(s[0].selected)
        assertEquals("2026-2027", s[0].season)
        assertEquals("PLAYOFF", s[1].seasonType)
    }

    @Test fun standingsBoard_groupsByConferenceAndDivision() {
        fun row(rank: Int, team: String, rt: String) =
            """{"rankType":"$rt","rankTypeDesc":"${if (rt == "W") "西部" else "东部"}","rank":"$rank","teamId":"1","teamName":"$team","teamShortName":"$team","logoLink":"l","won":"5","lost":"3","winRate":"62.5%","gb":"-","strk":"2","homeWins":"3","homeLosses":"1"}"""
        val json = """{"success":true,"result":{
            "competitionSeasonStageType":{"competitionSeasonType":{"competitionType":{"leagueTypeName":"NBA"},"season":"2026-2027"},"competitionStageTypeName":"常规赛"},
            "rankTypeListMap":{"W":[${row(1, "雷霆", "W")}],"E":[${row(1, "活塞", "E")}]},
            "divRankTypeListMap":{"Northwest":[${row(1, "雷霆", "W")}]}
        }}"""
        val b = HupuDataParser.parseStandingsBoard(json)!!
        assertEquals("NBA", b.leagueName)
        assertEquals("2026-2027", b.season)
        assertEquals("常规赛", b.stageName)
        assertEquals(listOf("E", "W", "divRank"), b.categories.map { it.id }.sorted())
        val west = b.categories.first { it.id == "W" }
        assertEquals("西部", west.title)
        assertEquals("雷霆", west.groups[0].rows[0].team)
        assertEquals(listOf("5", "3", "62.5%", "-", "2连胜"), west.groups[0].rows[0].cells)
        val div = b.categories.first { it.id == "divRank" }
        assertEquals("西北分区", div.groups[0].title)
    }

    @Test fun playerRank_mapsDimensionItemLeaders() {
        val json = """{"success":true,"result":{
            "competitionSeasonStageType":{"competitionSeasonType":{"season":"2026-2027"},"competitionStageTypeName":"常规赛"},
            "dimensionList":[{"engName":"basic","chineseName":"基础","itemList":[
                {"engName":"basic.ptsAvg","chineseName":"得分","rankInfoList":[
                    {"rank":1,"playerId":"p1","playerName":"库珀","photo":"ph","teamName":"独行侠","teamShortName":"独行侠","value":"30.1"}
                ]}
            ]}]
        }}"""
        val b = HupuDataParser.parsePlayerRankBoard(json)!!
        assertEquals("2026-2027", b.season)
        assertEquals(1, b.dimensions.size)
        val item = b.dimensions[0].items[0]
        assertEquals("得分", item.name)
        assertEquals(1, item.leaders[0].rank)
        assertEquals("库珀", item.leaders[0].name)
        assertEquals("30.1", item.leaders[0].value)
    }

    @Test fun draftBoard_groupsRoundsSortedByRank() {
        val json = """{"success":true,"result":{"draftMap":{
            "2":[{"round":2,"rank":31,"playerId":"p2","playerName":"乙","photo":"","draftTeamName":"湖人","position":"G","height":188}],
            "1":[{"round":1,"rank":2,"playerId":"p1b","playerName":"甲二","photo":"","draftTeamName":"马刺","position":"F","height":206},
                  {"round":1,"rank":1,"playerId":"p1a","playerName":"甲一","photo":"","draftTeamName":"独行侠","position":"F","height":203}]
        }}}"""
        val rounds = HupuDataParser.parseDraftBoard(json, "2025")
        assertEquals(listOf(1, 2), rounds.map { it.first })
        assertEquals(listOf("甲一", "甲二"), rounds[0].second.map { it.name })
        assertEquals("独行侠", rounds[0].second[0].teamName)
        assertEquals(203, rounds[0].second[0].heightCm)
    }

    @Test fun injuries_flatListAndTeamMapFallback() {
        val flat = """{"success":true,"result":{"playerInjuryDTOList":[
            {"playerId":"p","playerName":"东契奇","photo":"x","teamName":"独行侠","injuryPart":"后背","injuryStatus":"每日观察","injuryDate":"2026-10-24","zhDetail":"背部拉伤"}
        ],"teamInjuryPlayerListMap":{}}}"""
        val list = HupuDataParser.parseInjuries(flat)
        assertEquals(1, list.size)
        assertEquals("东契奇", list[0].name)
        assertEquals("每日观察", list[0].status)
        assertEquals("背部拉伤", list[0].detail)

        val mapOnly = """{"success":true,"result":{"playerInjuryDTOList":[],"teamInjuryPlayerListMap":{
            "凯尔特人":[{"playerId":"q","playerName":"塔图姆","teamName":"凯尔特人","injuryPart":"脚踝","injuryStatus":"无法出场","injuryDate":"2026-10-20"}]
        }}}"""
        val m = HupuDataParser.parseInjuries(mapOnly)
        assertEquals(1, m.size)
        assertEquals("塔图姆", m[0].name)
        assertEquals("无法出场", m[0].status)
    }

    @Test fun quarterStats_homeAwayArrays() {
        val json = """{"success":true,"result":{"homeQuarterScore":["35","26","37","25"],"awayQuarterScore":["32","49","29","22"]}}"""
        val q = HupuDataParser.parseQuarterStats(json)!!
        assertEquals(listOf("35", "26", "37", "25"), q.home)
        assertEquals(4, q.away.size)
        assertNull(HupuDataParser.parseQuarterStats("""{"success":true,"result":{}}"""))
    }
}
