package com.kudo.android

import com.kudo.android.data.toTopicDetail
import com.kudo.android.hupu.HupuDataParser
import com.kudo.android.hupu.HupuMatchParser
import com.kudo.android.hupu.hupuJrsScoreText
import com.kudo.android.hupu.hupuScoreCount
import com.kudo.android.ui.topic.ArticleBlock
import com.kudo.android.ui.topic.parseArticle
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

    @Test fun mobileHome_keepsAllSourceCoversAndBoardLabel() {
        // 批C-5：三图卡需要 source[] 全量而非仅 source[0]
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{
            "props":{"pageProps":{"res":[
              {"tid":"1","title":"三图","label":"步行街主干道","lights":"50","replies":"120","type":"3_pic",
               "source":["//a.jpg","//b.jpg","//c.jpg"],"url":"/1.html"},
              {"tid":"2","title":"单图","label":"NBA","lights":"10","replies":"5","type":"1_pic",
               "source":["//d.jpg"],"url":"/2.html"},
              {"tid":"3","title":"纯文","label":"","lights":"1","replies":"0","source":[],"url":"/3.html"}
            ]}}
        }</script></html>"""
        val list = com.kudo.android.hupu.HupuParser.parseMobileHome(html)
        assertEquals(3, list.size)
        assertEquals(listOf("//a.jpg", "//b.jpg", "//c.jpg"), list[0].covers)
        assertEquals("//a.jpg", list[0].cover)
        assertEquals("步行街主干道", list[0].topic?.name)
        assertEquals(1, list[1].covers.size)
        assertTrue(list[2].covers.isEmpty())
        assertNull(list[2].topic)
    }

    @Test fun videoThread_realSample_flowsThroughBridgeToNativePlayerMount() {
        // 批E：真实样本 tid=643103358（2026-10-26 抓取的 PC SSR __NEXT_DATA__）——
        // detail.thread 带 hasVideo/video/videoCover，content 内无 <video>（视频是独立字段）
        val video = "https://v.hoopchina.com.cn/bbs-editor-web/4322477422577d3d6130fa51bca3c2ec_wz_transcode.mp4?auth_key=1792993545-2-0-210e10c3e5248a5542556bf999fa9f1f"
        val poster = "https://i5.hoopchina.com.cn/bbs-editor-web/17924935854601.jpg"
        val html = """<html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"detail":{"detailErrorInfo":{"code":200},
             "thread":{"tid":"643103358","title":"[搬运]职业选手套路介绍：上单飞机，上单穿甲剑魔",
               "hasVideo":true,"video":"$video","videoCover":"$poster",
               "content":"<h1 class=\"slate-h1\">这两个玩法难道会登上S16世界赛</h1>",
               "lights":8,"replies":14,"read":13465,"location":"天津","url":"/643103358.html",
               "createdAt":1792493532000,"createdAtFormat":"5天前",
               "author":{"puid":"116528540","euid":"22230928576422","puname":"求你了再让我补波兵","level":14},
               "topic":{"topicId":"85","name":"英雄联盟","url":"/lol"}},
             "replies":{"list":[],"count":14,"current":1}}}}}
        </script></html>"""
        val detail = com.kudo.android.hupu.HupuParser.parseThreadDetail(html)
        assertNotNull(detail)
        // hupuui 详情页挂载条件：hasVideo && video.isNotBlank() → VideoPlayer
        assertTrue(detail!!.thread.hasVideo && detail.thread.video.isNotBlank())
        assertEquals(video, detail.thread.video)
        assertEquals(poster, detail.thread.videoCover)

        // feed 详情路径：toTopicDetail 前置 <video> → parseArticle 出 Video 块 → RichVideoBlock 挂载
        val cooked = detail.toTopicDetail().posts.first().cooked
        val blocks = parseArticle(cooked)
        val videos = blocks.filterIsInstance<ArticleBlock.Video>()
        assertEquals(1, videos.size)
        assertEquals(video, videos[0].url)
        assertEquals(poster, videos[0].poster)
        assertTrue(blocks.any { it is ArticleBlock.TextBlock })
    }

    @Test fun topicChipBridgesToBoardKey_notTagIdNamespace() {
        // 批R-1：thread.topic.topicId=502（话题区页 id）必须映射 board:{url} 键 → topicPage 通道；
        // 误接 tagThreads 的 tagId 命名空间（批R-0 实测 tagId=502 返回空列表）
        val html = """<html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"detail":{"detailErrorInfo":{"code":200},
             "thread":{"tid":"643295837","title":"t","content":"<p>x</p>","url":"/643295837.html",
               "topic":{"topicId":"502","name":"篮球资讯","url":"/502"}},
             "replies":{"list":[],"count":0,"current":1}}}}}
        </script></html>"""
        val detail = com.kudo.android.hupu.HupuParser.parseThreadDetail(html)!!.toTopicDetail()
        assertEquals("篮球资讯", detail.tags[0].name)
        assertEquals("board:/502", com.kudo.android.data.HupuIds.key(detail.tags[0].id!!))
        assertEquals("/502", com.kudo.android.data.HupuIds.boardUrl(detail.tags[0].id!!))
    }

    @Test fun playerRankPage_parsesSingleDimensionPageAndEmptyPageMeansListEnd() {
        // 批F-1：playerSingleDimensionSeasonRank 真实结构（2026-10-26 匿名实测 pageNo=1/20）
        val json = """{"errorCode":"","errorMsg":"","success":true,"result":{
            "engName":"basic.ptsAvg","chineseName":"得分","hasMore":false,
            "rankInfoList":[
              {"rank":1,"playerId":"1901000000479677","playerName":"杰罗姆",
               "photo":"https://i11.hoopchina.com.cn/x.png","teamShortName":"灰熊","value":"36.5"},
              {"rank":19,"playerId":"","playerName":"默里","teamName":"掘金","value":"22.0"}
            ]}}"""
        val page = HupuDataParser.parsePlayerRankPage(json)
        assertNotNull(page)
        assertEquals("basic.ptsAvg", page!!.key)
        assertEquals("得分", page.name)
        assertEquals(2, page.leaders.size)
        assertEquals(1, page.leaders[0].rank)
        assertEquals("灰熊", page.leaders[0].teamName)
        assertEquals("36.5", page.leaders[0].value)
        // teamShortName 缺时回落 teamName
        assertEquals("掘金", page.leaders[1].teamName)
        // 成功响应但 rankInfoList 为空 = 榜尾（不是解析失败）
        val end = HupuDataParser.parsePlayerRankPage(
            """{"success":true,"result":{"engName":"basic.ptsAvg","chineseName":"得分","rankInfoList":[]}}"""
        )
        assertNotNull(end)
        assertTrue(end!!.leaders.isEmpty())
        assertNull(HupuDataParser.parsePlayerRankPage("""{"success":false}"""))
        assertNull(HupuDataParser.parsePlayerRankPage("not json"))
    }

    // 批G-3：预约 chip 只读对话框的字段来源——官方 subscribeInfo 原样映射；
    // 未开赛行带 showSubscribe+title/desc，已赛/无信息行为 null（chip 不渲）。
    @Test fun scheduleSubscribe_mapsOfficialFieldsAndNullsWhenAbsent() {
        val json = """{"success":true,"result":{"dayGameData":[{"dayTime":"2026-10-26","matchData":[
            {"matchId":"m1","matchStatus":"NOTSTARTED","subscribeInfo":{"showSubscribe":true,"didSubscribe":false,"subScribeTitle":"预约提醒","subScribeDesc":"湖人 vs 爵士"}},
            {"matchId":"m2","matchStatus":"NOTSTARTED"},
            {"matchId":"m3","matchStatus":"FINISHED","subscribeInfo":{"showSubscribe":false,"subScribeTitle":"","subScribeDesc":""}}
        ]}]}}"""
        val ms = HupuMatchParser.parseSchedule(json).single().matches
        assertEquals(3, ms.size)
        val s = ms[0].subscribe!!
        assertTrue(s.show)
        assertFalse(s.subscribed)          // 匿名恒 false，不伪造已约态
        assertEquals("预约提醒", s.title)    // subScribeTitle → 对话框标题
        assertEquals("湖人 vs 爵士", s.desc) // subScribeDesc → 对话框正文
        assertNull(ms[1].subscribe)
        assertNull(ms[2].subscribe)
    }

    @Test fun jrsScoreText_normalizesWanAndRenSuffix() {
        // 官方频道卡底行：原文计数 + 「JRs评分」（批T-0 实证）
        assertEquals("15.6万JRs评分", hupuJrsScoreText("15.6万人评分"))
        assertEquals("18.1万JRs评分", hupuJrsScoreText("18.1万人评分"))
        assertEquals("3.3万JRs评分", hupuJrsScoreText("3.3万评分"))
        assertEquals("9223JRs评分", hupuJrsScoreText("9223评分"))
        assertEquals("9068JRs评分", hupuJrsScoreText("9068人评分"))
        assertEquals("", hupuJrsScoreText(""))
        assertEquals("", hupuJrsScoreText("已结束"))
    }

    @Test fun scoreCount_parsesWanAndPlainNumbers() {
        assertEquals(156_000L, hupuScoreCount("15.6万人评分"))
        assertEquals(33_000L, hupuScoreCount("3.3万评分"))
        assertEquals(9_223L, hupuScoreCount("9223评分"))
        assertEquals(0L, hupuScoreCount(""))
        assertEquals(0L, hupuScoreCount("进行中"))
    }
}

class HupuMatchDetailParserTest {
    @Test fun matchInfo_parsesHeaderFields() {
        // 批U-1：真实 matchInfo 响应子集（NBA matchId 1537808021718040576 实测）
        val json = """{"success":true,"result":{"matchId":"1537808021718040576",
            "matchStatus":"COMPLETED","matchStatusChinese":"已结束",
            "matchTime":"2026-10-24 04:00:00","chinaStartTime":1792785600000,
            "homeScore":125,"awayScore":132,"homeTeamName":"雄鹿","awayTeamName":"公牛",
            "homeTeamLogo":"h.png","awayTeamLogo":"a.png",
            "homeTeamDayColor":"204224","awayTeamDayColor":"FA0808",
            "homeRank":null,"awayRank":null,"homeArea":null,"awayArea":null,
            "attendance":16354,"costTime":"2:27","score":"3.6万评分","season":"2026-2027",
            "liveUrl":"huputiyu://bball/live?matchId=1","ad_inside_pop":{"is_ad":1}}}"""
        val m = com.kudo.android.hupu.HupuDataParser.parseMatchInfo(json)!!
        assertEquals("已结束", m.matchStatusChinese)
        assertEquals("125", m.homeScore)
        assertEquals("132", m.awayScore)
        assertEquals("雄鹿", m.homeTeamName)
        assertEquals("204224", m.homeTeamDayColor)
        assertEquals("", m.homeRank)          // JSON null → 空串（不编名次）
        assertEquals("", m.homeArea)
        assertEquals(16354L, m.attendance)
        assertEquals("3.6万评分", m.scoreText)
        assertEquals(1792785600000L, m.chinaStartTime)
    }

    @Test fun matchInfo_rejectsBadPayloads() {
        assertNull(com.kudo.android.hupu.HupuDataParser.parseMatchInfo("""{"success":false,"result":null}"""))
        assertNull(com.kudo.android.hupu.HupuDataParser.parseMatchInfo("""{"result":{}}"""))
        assertNull(com.kudo.android.hupu.HupuDataParser.parseMatchInfo("not-json"))
    }

    @Test fun matchPlayerStats_parsesBothTeams() {
        // 批U-3：真实 playerMatchStats 响应子集
        val json = """{"success":true,"result":{"matchId":"1537808021718040576",
            "homePlayers":[{"playerId":"1","name":"泰勒-希罗","logoUrl":"p.png","position":"G",
              "starter":1,"mins":"31:34","pts":34,"reb":5,"asts":4,"userScoreAvg":9.6,"userScoreCount":7223}],
            "awayPlayers":[{"playerId":"2","name":"约什-吉迪","position":"G","starter":1,
              "mins":"30:00","pts":20,"reb":6,"asts":11,"userScoreAvg":8.1,"userScoreCount":3000}]}}"""
        val s = com.kudo.android.hupu.HupuDataParser.parseMatchPlayerStats(json)!!
        assertEquals(1, s.home.size)
        assertEquals("泰勒-希罗", s.home[0].name)
        assertEquals(34, s.home[0].pts.toInt())
        assertTrue(s.home[0].starter)
        assertEquals(7223L, s.home[0].userScoreCount)
        assertEquals("约什-吉迪", s.away[0].name)
        assertNull(com.kudo.android.hupu.HupuDataParser.parseMatchPlayerStats("""{"result":{}}"""))
    }
}

class TrendPointsTest {
    private fun play(q: Int, time: String, score: String) =
        com.kudo.android.hupu.NbaPlay(quarter = q, time = time, team = "", text = "x", score = score)

    @Test fun trendPoints_buildsCumulativeSeries() {
        val pts = com.kudo.android.hupuui.pages.trendPoints(listOf(
            play(1, "11:30", "2-0"),
            play(1, "10:00", "2-3"),
            play(1, "6:00", "10-7"),
            play(2, "11:00", "12-10"),
            play(4, "0:00", "30-25"),
        ))
        assertTrue(pts.size >= 5)
        assertEquals(0f, pts.first().minute)
        assertEquals(0, pts.first().away)
        // 第1节 11:30 剩余 → 已打 0.5 分钟
        assertEquals(0.5f, pts[1].minute, 0.01f)
        assertEquals(2, pts[1].away)
        // 第2节 11:00 → 12 + 1 = 13 分钟
        assertEquals(13f, pts[4].minute, 0.01f)
        // 终场点分钟数最大
        assertTrue(pts.last().minute >= 48f - 1f)
    }

    @Test fun trendPoints_rejectsSparseOrGarbage() {
        assertTrue(com.kudo.android.hupuui.pages.trendPoints(emptyList()).isEmpty())
        assertTrue(com.kudo.android.hupuui.pages.trendPoints(listOf(
            play(1, "12:00", "0-0"),
        )).isEmpty())
        assertTrue(com.kudo.android.hupuui.pages.trendPoints(listOf(
            play(0, "", "bad"),
            play(1, "", "x"),
        )).isEmpty())
    }
}

class WanScoreTextTest {
    @Test fun wanScoreText_normalizesToPeopleFormat() {
        assertEquals("15.6万人评分", com.kudo.android.hupu.hupuWanScoreText("15.6万人评分"))
        assertEquals("3.5万人评分", com.kudo.android.hupu.hupuWanScoreText("3.5万评分"))
        assertEquals("9223人评分", com.kudo.android.hupu.hupuWanScoreText("9223评分"))
        assertEquals("9068人评分", com.kudo.android.hupu.hupuWanScoreText("9068人评分"))
        assertEquals("", com.kudo.android.hupu.hupuWanScoreText("已结束"))
        assertEquals("", com.kudo.android.hupu.hupuWanScoreText(""))
    }
}
