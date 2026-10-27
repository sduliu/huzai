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
        // 批X8：胜-负合并一格，列头对齐官方「胜-负/胜率/胜场差/近况」
        assertEquals(listOf("胜-负", "胜率", "胜场差", "近况"), west.groups[0].columns)
        assertEquals(listOf("5-3", "62.5%", "-", "2连胜"), west.groups[0].rows[0].cells)
        val div = b.categories.first { it.id == "divRank" }
        assertEquals("西北分区", div.groups[0].title)
    }

    @Test fun teamStatRanks_parsesStatGroupsForLeftRail() {
        // 批Y-3：积分榜左栏「技术统计」= teamSeasonStatsAllRank 每项一个分组（官方 top5）
        val json = """{"success":true,"result":[
            {"rankType":"ptsAvg","name":"场均得分","data":[
                {"rank":1,"teamName":"俄克拉荷马城雷霆","teamShortName":"雷霆","logoUrl":"l","value":"120.5"},
                {"rank":2,"teamName":"丹佛掘金","teamShortName":"掘金","logoUrl":"","value":"118.9"}
            ]},
            {"rankType":"astAvg","name":"场均助攻","data":[
                {"rank":1,"teamName":"掘金","teamShortName":"掘金","logoUrl":"","value":"31.2"}
            ]}
        ]}"""
        val groups = HupuDataParser.parseTeamStatRanks(json)
        assertEquals(2, groups.size)
        assertEquals("场均得分", groups[0].title)
        // 批X8：Avg 型统计列头「场均」，非 Avg 为「数值」
        assertEquals(listOf("场均"), groups[0].columns)
        assertEquals("雷霆", groups[0].rows[0].team)
        assertEquals("120.5", groups[0].rows[0].cells[0])
        assertEquals("掘金", groups[1].rows[0].team)
        // 空 data 的分组被跳过；整体空 → 空表（左栏该项不渲染类别）
        assertTrue(HupuDataParser.parseTeamStatRanks("""{"success":true,"result":[{"rankType":"x","data":[]}]}""").isEmpty())
        assertTrue(HupuDataParser.parseTeamStatRanks("not-json").isEmpty())
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

    @Test fun injuryGroups_latestFirstAndTeamOrderPreserved() {
        // 批X8：分组结构=「最新」+ 各队；teamMap 键序按 JSON 文档序保留（JSONObject.keys() 无序不可用）
        fun pl(name: String, team: String) =
            """{"playerId":"x","playerName":"$name","photo":"","teamName":"$team","injuryPart":"膝伤","injuryStatus":"无法出场","injuryDate":"2026-10-27"}"""
        val json = """{"success":true,"result":{
            "playerInjuryDTOList":[${pl("甲", "湖人")},${pl("乙", "勇士")}],
            "teamInjuryPlayerListMap":{"湖人":[${pl("甲", "湖人")}],"勇士":[${pl("乙", "勇士")}],"马刺":[${pl("丙", "马刺")}]}
        }}"""
        val gs = HupuDataParser.parseInjuryGroups(json)
        assertEquals(listOf("最新", "湖人", "勇士", "马刺"), gs.map { it.first })
        assertEquals(2, gs[0].second.size)
        assertEquals("丙", gs[3].second[0].name)
        assertTrue(HupuDataParser.parseInjuryGroups("not-json").isEmpty())
    }

    @Test fun awards_parsesGroupsWinnersAndStatLine() {
        // 批Y：allPlayerAwardList 真实结构（2026-10-27 匿名实测）——20 奖项 × top5 获奖者
        val json = """{"success":true,"result":{"playerAwardMapDTOList":[
            {"awardName":"NBA_Most_Valuable_Player","awardDesc":"MVP","playerAwardDetailDTOList":[
                {"playerId":"p1","playerName":"亚历山大","photo":"ph","teamName":"雷霆",
                 "season":"2025-2026","awardDate":"2025-2026","pts":31.132,"reb":4.294,"asts":6.588},
                {"playerId":"p2","playerName":"约基奇","photo":"","teamName":"掘金",
                 "season":"2024-2025","awardDate":"2024-2025","pts":29,"reb":12,"asts":10}
            ]},
            {"awardName":"NBA_Total_champion","awardDesc":"总冠军","playerAwardDetailDTOList":[]}
        ]}}"""
        val list = HupuDataParser.parseAwards(json)
        assertEquals(1, list.size)   // 获奖者全空的奖项不渲染（不编数据）
        val a = list[0]
        assertEquals("MVP", a.awardDesc)
        assertEquals(2, a.winners.size)
        assertEquals("亚历山大", a.winners[0].name)
        assertEquals("2025-2026", a.winners[0].season)
        assertEquals("31.1", a.winners[0].pts)   // 1 位小数
        assertEquals("29", a.winners[1].pts)     // 整数不补零
        assertEquals(emptyList<Any>(), HupuDataParser.parseAwards("""{"result":{}}"""))
    }

    @Test fun careerRank_parsesStageDimensionAndFiltersFootballLeftovers() {
        // 批Y：playerAllDimensionCareerRank 真实结构（2026-10-27 匿名实测 REGULAR+pergame）——
        // dataDimensionMap[stage] 单元素维度组，itemList 同 playerSeasonRank 结构。
        val json = """{"success":true,"result":{
            "competitionType":{"leagueTypeName":"NBA","competitionTypeName":"NBA"},
            "dataDimensionMap":{"REGULAR":[{"name":"avg","itemList":[
                {"engName":"basic.minAvg","chineseName":"场均出场时间","rankInfoList":[
                    {"rank":1,"playerId":"p1","playerName":"张伯伦","photo":"ph","value":"45.8","pts":"30.1","reb":"22.9","ast":"4.4"}
                ]},
                {"engName":"xx","chineseName":"场均进球数","rankInfoList":[{"rank":1,"playerName":"x","value":"1"}]},
                {"engName":"basic.ptsAvg","chineseName":"场均得分","rankInfoList":[
                    {"rank":1,"playerId":"p2","playerName":"乔丹","photo":"","teamName":"公牛","value":"30.1"}
                ]}
            ]}]}}}"""
        val b = HupuDataParser.parseCareerRank(json, "REGULAR", "pergame")!!
        assertEquals("常规赛", b.stageName)
        assertEquals("pergame", b.grain)
        assertEquals(1, b.dimensions.size)
        // 「场均进球数」按官方同款过滤剔除
        assertEquals(listOf("场均出场时间", "场均得分"), b.dimensions[0].items.map { it.name })
        assertEquals("张伯伦", b.dimensions[0].items[0].leaders[0].name)
        assertEquals("45.8", b.dimensions[0].items[0].leaders[0].value)
        assertNull(HupuDataParser.parseCareerRank(json, "PLAYOFF", "pergame")) // 无该 stage 键→null
        assertNull(HupuDataParser.parseCareerRank("""{"success":false}""", "REGULAR", "pergame"))
    }

    @Test fun againstChart_nullOnEmptyResultAndParsesCells() {
        // 批Y：against-plan-chart 2026-10-27 实测 result=null（官方数据空）→ null（显式空态）
        assertNull(HupuDataParser.parseAgainstChart("""{"success":true,"result":null}"""))
        assertNull(HupuDataParser.parseAgainstChart("""{"success":true,"result":{"planChartItemList":[]}}"""))
        // 有数据时按 planChartNumber "行-列" 定位解析（字段名照抄官方组件访问名）
        val json = """{"success":true,"result":{"planChartItemList":[
            {"planChartNumber":"0-0","teamId":"t1","teamName":"凯尔特人","teamLogo":"l1","score":"4","rank":"1",
             "oppoTeamId":"t8","oppoTeamName":"热火","oppoTeamLogo":"l8","oppoScore":"1","winnerTeamId":"t1"},
            {"planChartNumber":"3-6","teamId":"t4","teamName":"掘金","teamLogo":"","score":"3","rank":"",
             "oppoTeamId":"t5","oppoTeamName":"森林狼","oppoTeamLogo":"","oppoScore":"3","winnerTeamId":""}
        ]}}"""
        val b = HupuDataParser.parseAgainstChart(json)!!
        assertEquals(2, b.cells.size)
        val c = b.cells[0]
        assertEquals(0, c.row); assertEquals(0, c.col)
        assertEquals("凯尔特人", c.teamName)
        assertEquals("热火", c.oppoTeamName)
        assertEquals("4", c.teamScore); assertEquals("1", c.oppoScore)
        assertEquals("t1", c.winnerTeamId)
        assertEquals(6, b.cells[1].col); assertEquals(3, b.cells[1].row)
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

    /** 批W-1：国际足球赛程走 m.hupu.com/soccer SSR——match 对象无 subscribeInfo 字段
     *  （实测字段表无 subscribe 键）→ subscribe=null → MatchRow 右列诚实保留「未开始」，
     *  不伪造预约位。match-api 系（nba/cba/lol/epl 实测）全量 NOTSTARTED 带
     *  showSubscribe=true → 🔔预约正常渲染。 */
    @Test fun soccerSchedule_notStartedRow_hasNoSubscribeField() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"data":{"games":[
            {"day":"20261027","date_block":"明天","data":[
                {"currentMatchId":"3867723","status":{"txt":"未开始"},"title":"德乙第9轮",
                 "home":{"name":"达姆施塔特","teamId":7298,"logo":"h.png"},
                 "away":{"name":"圣保利","teamId":12151,"logo":"a.png"},"begin_time":1793000000}
            ]}]}}}}</script></html>"""
        val days = HupuMatchParser.parseSoccerSchedule(html)
        assertEquals(1, days.size)
        val m = days.single().matches.single()
        assertEquals("NOTSTARTED", m.status)
        assertNull(m.subscribe)
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

    // 批V-6：football_match 树根节点真实形态（达姆施塔特 0-0 圣保利，德乙第9轮）——
    // self.node.infoJson 各字段为单元素数组；children 为 football_item 选手/教练 → flat。
    private val footballTreeJson = """{"code":1,"data":{
        "self":{"node":{"bizId":"57665","bizType":"football_match","name":"达姆施塔特 0-0 圣保利",
            "summedScorePersonCount":128,"infoJson":{
                "homeTeamName":["达姆施塔特"],"awayTeamName":["圣保利"],
                "homeTeamScore":["0"],"awayTeamScore":["0"],
                "homeTeamLogo":["https://assets-football.hoopchina.com.cn/h.png"],
                "awayTeamLogo":["https://assets-football.hoopchina.com.cn/a.png"],
                "matchStatus":["COMPLETED"],"matchTime":["1792773000000"],
                "matchId":["3867722"],"roundCn":["第9轮"],
                "competitionTypeCn":["德乙"],"season":["2026-2027"]}}},
        "pageResult":{"data":[
            {"node":{"bizId":"90001","bizType":"football_item","name":"舒汉","summedScorePersonCount":30,"infoJson":{"teamId":["7298"]}}},
            {"node":{"bizId":"90002","bizType":"football_item","name":"教练 弗洛里安·科菲尔特","summedScorePersonCount":12}}]}
    }}"""

    @Test fun scoreTree_footballMatch_mapsHeaderFromInfoJson() {
        val t = HupuMatchParser.parseScoreTree(footballTreeJson)!!
        val mi = t.matchInfo!!
        assertEquals("3867722", mi.matchId)
        assertEquals("COMPLETED", mi.matchStatus)
        assertEquals("已结束", mi.matchStatusChinese)
        assertEquals(1792773000000L, mi.chinaStartTime)
        assertEquals("达姆施塔特", mi.homeTeamName)
        assertEquals("圣保利", mi.awayTeamName)
        assertEquals("0", mi.homeScore)
        assertEquals("0", mi.awayScore)
        assertEquals("https://assets-football.hoopchina.com.cn/h.png", mi.homeTeamLogo)
        assertEquals("德乙 第9轮", mi.leagueDesc)
        assertEquals("2026-2027", mi.season)
        // football_item 叶子 → flat 结构不变（与既有行为一致）
        assertTrue(t.flat)
        assertEquals(2, t.rounds.single().players.size)
    }

    @Test fun scoreTree_nonFootball_matchInfoStaysNull() {
        // 非 football_match 根节点不产头数据（不泛化套用其他 bizType 命名空间）
        val json = footballTreeJson.replace("football_match", "basketball_match")
        assertNull(HupuMatchParser.parseScoreTree(json)!!.matchInfo)
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

/**
 * 批V-1：讨论 tab 数据源闭环——比赛级 primarySingleRow 抽样全空（canComment=false），
 * 但球员级同端点返回真实评论。用真实球员级响应证明 parseComments/parseHottestComments
 * 映射出 CommentRow 消费的全部字段（头像/昵称/内容/日期/点亮/IP/打分），比赛级开放即同款渲染。
 */
class MatchDiscussCommentTest {
    // 真实 primarySingleRow 响应子集（basketball_item/228733 2026-10-26 实测）
    private val listJson = """{"code":1,"data":{"comments":[{
        "commentKey":{"subjectId":"374201009","commentId":"2996903733"},
        "subjectId":"374201009","commentId":"2996903733",
        "commentUserId":110603213,"commentUserName":"库里没哨",
        "commentUserHeadImg":"https://i2.hoopchina.com.cn/user/420/x.png",
        "commentContent":"加油","commentContentImages":null,
        "commentDate":"10-09","lightCount":0,"score":10,"hasLight":false,
        "publishTime":1791540807955,"ipLocation":"天津",
        "subCommentCount":0,"descendantCount":0,"subCommentList":[],
        "parentCommentId":"0"
    }],"commentCount":55,"cursor":{"publishTime":1791274778281},"hasMore":true}}"""

    private val hottestJson = """{"code":1,"data":[{
        "commentKey":{"subjectId":"374201009","commentId":"2994024799"},
        "subjectId":"374201009","commentId":"2994024799",
        "commentUserId":118941231,"commentUserName":"晚安1j1",
        "commentUserHeadImg":"https://i1.hoopchina.com.cn/user/default/tiger1.png",
        "commentContent":"爱来自金州","commentContentImages":null,
        "commentDate":"10-06","lightCount":50,"score":10,
        "publishTime":1791269264405,"ipLocation":"浙江",
        "subCommentCount":0,"descendantCount":0,"subCommentList":null,
        "parentCommentId":"0"
    }]}"""

    @Test fun parseComments_mapsOfficialRowFields() {
        val st = HupuMatchParser.parseComments(listJson)!!
        assertEquals(55L, st.commentCount)
        assertEquals(1791274778281L, st.cursor)
        assertTrue(st.hasMore)
        val c = st.comments.single()
        assertEquals("2996903733", c.commentId)
        assertEquals("库里没哨", c.userName)
        assertEquals("https://i2.hoopchina.com.cn/user/420/x.png", c.userHead)
        assertEquals("110603213", c.userId)
        assertEquals("加油", c.content)
        assertEquals("10-09", c.date)
        assertEquals("天津", c.ipLocation)
        assertEquals(0L, c.lightCount)
        assertEquals(10, c.score)
        assertEquals("374201009", c.subjectId)
    }

    @Test fun parseHottestComments_mapsLightedRow() {
        val list = HupuMatchParser.parseHottestComments(hottestJson)
        val c = list.single()
        assertEquals("晚安1j1", c.userName)
        assertEquals("爱来自金州", c.content)
        assertEquals(50L, c.lightCount)
        assertEquals("浙江", c.ipLocation)
        assertEquals("2994024799", c.commentId)
    }

    @Test fun parseComments_emptyMatchPayloadYieldsEmptyState() {
        // 比赛级真实响应形态（commentCount=0/comments=[]）→ 空列表 → UI 空态
        val st = HupuMatchParser.parseComments(
            """{"code":1,"data":{"comments":[],"commentCount":0,"cursor":null,"hasMore":false}}"""
        )!!
        assertTrue(st.comments.isEmpty())
        assertEquals(0L, st.commentCount)
        assertFalse(st.hasMore)
    }
}
