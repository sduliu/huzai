package com.kudo.android.hupu

/** 积分榜数据源：NONE 无入口 / NBA SSR 表格 / football-api 赛事榜。 */
internal enum class HupuBoardStanding { NONE, NBA, SOCCER_API }

internal data class HupuBoardCfg(
    /** 显示名（复用 HupuMatchApi.GAMES 的频道名） */
    val name: String,
    /** 赛程 businessId（= GAMES 频道 id；soccer 走 SSR，matchSchedule 已内部分流） */
    val scheduleId: String,
    /** newsList 的 tagCode；null 表示走 [newsTopic] 兜底或 soccer SSR */
    val tagCode: String? = null,
    /** 国际足球：tagCode 失败/空时回退 m.hupu.com/soccer SSR */
    val soccerSsr: Boolean = false,
    /** 资讯兜底：专区话题页（如 /245 网球场），tid 帖直接可开 */
    val newsTopic: String = "",
    /** 积分榜数据源 */
    val standing: HupuBoardStanding = HupuBoardStanding.NONE,
    /** SOCCER_API 用：football-api 的 competitionId（英超81/中超531/欧冠46/世界杯1） */
    val standingCompId: String = "",
    /** 积分榜 WebView 兜底落地页（无原生实现时用；空串 + NONE = 无入口） */
    val standingsUrl: String = "",
    /** 首页赛事频道帖流板块（湿乎乎/足球话题区等社区版块 URL，如 "/vote"）。
     *  空 → 频道 feed 回退 newsList 资讯；板块帖流与比赛 Tab 资讯分工见
     *  CommunityRepository.topics 的 sport: 分支。 */
    val boardUrl: String = "",
)

/**
 * 有板块页的频道（key = GAMES 频道 id）。
 * 实测 tagCode（2026-10 newsList 各拉 20 条验证）：nba/cba/lol/kog/val/pubg/epl/csl/
 * tabletennis/olympics/commonhotsports/fifa/digital 有量；wnba/cuba/lpl/lck/cs2/
 * pubgmobile/worldcup/tennis/badminton 空 → 分别兜底（lpl/lck 共用 lol 流，
 * 足球系共用 fifa 流，小球类走专区话题帖）。
 */
internal val HUPU_BOARDS: Map<String, HupuBoardCfg> = mapOf(
    HupuMatchApi.COMMON_HOT_SPORTS_ID to
        HupuBoardCfg("热门赛事", HupuMatchApi.COMMON_HOT_SPORTS_ID, tagCode = "commonhotsports"),
    "lol" to HupuBoardCfg("英雄联盟", "lol", tagCode = "lol", boardUrl = "/lol"),
    "nba" to HupuBoardCfg(
        "NBA", "nba", tagCode = "nba", boardUrl = "/vote",
        standing = HupuBoardStanding.NBA,
        standingsUrl = "https://nba.hupu.com/standings",
    ),
    "epl" to HupuBoardCfg(
        "英超", "epl", tagCode = "epl", boardUrl = "/yingchao",
        standing = HupuBoardStanding.SOCCER_API, standingCompId = "81",
    ),
    "kog" to HupuBoardCfg("王者荣耀", "kog", tagCode = "kog", boardUrl = "/kog"),
    "cba" to HupuBoardCfg("CBA", "cba", tagCode = "cba", boardUrl = "/cba"),
    "soccer" to HupuBoardCfg(
        // 国际足球资讯实测走 fifa tagCode（SSR 仅兜底）
        "国际足球", HupuMatchApi.SOCCER_ID, tagCode = "fifa", boardUrl = "/topic", soccerSsr = true,
        // 官方 H5 rank_new/:id 需 leagueId+seasonId query 才有数据（裸链必空态），
        // 原生榜默认英超（国际足球里热度最高的联赛，标题显示「英超 积分榜」）
        standing = HupuBoardStanding.SOCCER_API, standingCompId = "81",
    ),
    "cs2" to HupuBoardCfg("CS2", "cs2", boardUrl = "/csgo", newsTopic = "/240"),
    "val" to HupuBoardCfg("无畏契约", "val", tagCode = "val", boardUrl = "/803"),
    "lpl" to HupuBoardCfg("LPL", "lpl", tagCode = "lol", boardUrl = "/822"),
    "pubg" to HupuBoardCfg("绝地求生", "pubg", tagCode = "pubg", boardUrl = "/pubg"),
    "pubgmobile" to HupuBoardCfg("和平精英", "pubgmobile", boardUrl = "/gp", newsTopic = "/240"),
    "lck" to HupuBoardCfg("LCK", "lck", tagCode = "lol"),
    "worldcup" to HupuBoardCfg(
        "世界杯", "worldcup", tagCode = "fifa", boardUrl = "/soccer",
        standing = HupuBoardStanding.SOCCER_API, standingCompId = "1",
    ),
    "tennis" to HupuBoardCfg("网球", "tennis", boardUrl = "/tennis", newsTopic = "/245"),
    "tabletennis" to HupuBoardCfg("乒乓球", "tabletennis", tagCode = "tabletennis", boardUrl = "/pingpang"),
    "badminton" to HupuBoardCfg("羽毛球", "badminton", boardUrl = "/yumaoqiu", newsTopic = "/252"),
    "olympics" to HupuBoardCfg("奥运会", "olympics", tagCode = "olympics", boardUrl = "/753"),
    "wnba" to HupuBoardCfg("WNBA", "wnba", boardUrl = "/wnba", newsTopic = "/502"),
    "cuba" to HupuBoardCfg("CUBA", "cuba", boardUrl = "/cuba", newsTopic = "/176"),
)
