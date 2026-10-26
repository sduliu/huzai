package com.kudo.android.hupu

/**
 * 虎扑数据模型
 * 数据来源见 docs/hupu_web_probe.md（HTML 内嵌 JSON 通道）
 */

data class HupuAuthor(
    val puid: String = "",
    val name: String = "",
    val url: String = "",
    // 加密用户 id：官方用户主页为 my.hupu.com/{euid}（移动端为 m.hupu.com/user/{euid}），用户主页跳转用
    val euid: String = "",
)

/** 批W-2：用户主页路由 id——euid 优先，部分帖（如官方账号帖）SSR 只下发 puid；
 *  m.hupu.com/user/{puid} 实测同返回完整 SSR，可安全回落。"0" 为占位脏值。
 *  注意不把 puid 写进 euid 字段：登录态 PC getUserInfo 接口要真 euid 命名空间。 */
val HupuAuthor.pageId: String
    get() = euid.takeIf { it.isNotEmpty() } ?: puid.takeIf { it.isNotEmpty() && it != "0" } ?: ""

data class HupuTopic(
    val topicId: String = "",
    val name: String = "",
    val url: String = "",
)

/** 帖子条目（列表页） */
data class HupuThread(
    val tid: String,
    val title: String,
    val fid: String = "",
    val cover: String? = null,
    /** 信息流多图卡（SSR res[].source[] 全量，≥3 张渲染三图行）；单图源回落 cover */
    val covers: List<String> = emptyList(),
    val desc: String? = null,
    val lights: Int = 0,
    val replies: Int = 0,
    val read: Int = 0,
    val createdAt: Long = 0L,
    val createdAtText: String = "",
    /** 1.189: 主楼发布地（官方 detail JSON 的 `location`，如「上海」；可为空） */
    val location: String = "",
    val hasVideo: Boolean = false,
    val video: String = "",
    val videoCover: String = "",
    val author: HupuAuthor? = null,
    val topic: HupuTopic? = null,
    val url: String = "",
    /**
     * 批N-1：转发数（官方信息流卡底栏第三计数）。
     * -1 = 数据源未携带（SSR 列表没有该字段）→ 底栏不显示；
     * ≥0 = `api/v2/bbs-thread/{tid}` 的 `t_detail.share`，惰性补齐后显示真实值。
     */
    val shareNum: Int = -1,
    /**
     * 批S-1：topicPage `threads.list[].titleFont` 标色标题标记（官方渲染 `color:X;font-weight:600`）。
     * 格式 `{css色}~{粗体}~~~~~`（实测 `orange~1~~~~~`/`blue~1~~~~~`/`blue~~~~~~`）；
     * 空 = 无标色。官方仅作彩色加粗标题呈现（置顶/精华/高亮混用，无语义布尔），本端同款还原。
     */
    val titleFont: String = "",
) {
    val detailUrl: String get() = if (url.isNotEmpty()) url else "/$tid.html"
}

/** 回复条目（详情页） */
data class HupuReply(
    val pid: String,
    val contentHtml: String = "",
    val floor: Int = 0,
    val lights: Int = 0,
    val totalReplies: Int = 0,
    val createdAtText: String = "",
    val isStarter: Boolean = false,
    val location: String = "",
    val author: HupuAuthor? = null,
    /**
     * 1.128：引用楼层的 pid（来自 SSR replies 条目的 quote.pid）。
     * 非空 = 该条实为「回复某条评论」的子回复——官方 SSR 也把它平铺进最外层，
     * 但楼中楼里同样能看到，造成重复；真一级评论（回复楼主/主题）**没有** quote 键。
     */
    val quotePid: String = "",
    /** 批F-3：客户端来源（reply.client：ANDROID/IPHONE；官方楼层「来自」标签） */
    val client: String = "",
    /** 批F-3：官方折叠态（isDelete/isHidden/hidePost>0/isAudit 任一）→ 楼层显示占位，不渲正文 */
    val hidden: Boolean = false,
) {
    /** 1.128：是否为子回复（最外层应过滤，只留真一级评论）。 */
    val isSubReply: Boolean get() = quotePid.isNotEmpty()
}

/** 楼中楼：一条子回复（bbs-reply-detail API） */
data class HupuSubReply(
    val pid: String = "",
    val contentHtml: String = "",
    val lights: Int = 0,
    val nestedCount: Int = 0,
    val createdAtText: String = "",
    val isStarter: Boolean = false,
    val location: String = "",
    val author: HupuAuthor? = null,
    /** 被引用楼层（同楼内）的作者名，用于「回复 @xxx」语义 */
    val quoteUser: String = "",
    /** 树层级：0=直接子楼层；>0 为「展开」递归加载的深层回复（渲染缩进用） */
    val depth: Int = 0,
)

/** 楼中楼数据包：父楼层 + 子回复分页 */
data class HupuFloorReplies(
    val parent: HupuReply,
    val subReplies: List<HupuSubReply>,
    /** 是否还有下一页（nextPage>0） */
    val hasMore: Boolean = false,
)

/** 帖子详情（主楼 + 回复分页） */
/**
 * 1.119: 帖子里的投票（从 thread.format 的 jsonV3 vote 节点解析）。
 *  - type: "radio" 单选 / "checkbox" 多选
 *  - limit: 最多可选几项（单选固定 1）
 * 依据实测帖 642389664：type=checkbox、limit=2、7 个选项。
 */
data class HupuVote(
    val voteId: Int,
    val title: String,
    val choices: List<String>,
    val limit: Int = 1,
    val type: String = "radio",
) {
    /**
     * 1.121: 多选判定 = type 为 checkbox **或** limit>1。
     * 实测单选帖（642385783）的 vote 节点**没有 type 字段**（只有 voteType:"text"、limit:1），
     * 故不能只认 type=="checkbox"，须用 limit 兜底。
     */
    val isMulti: Boolean get() = type == "checkbox" || limit > 1
}

/** 1.121: 投票选项（读取接口 voteDetailList 单项） */
data class HupuVoteOption(
    val sort: Int,
    val content: String,
    val voteCount: Int,
)

/**
 * 1.121: 投票实时结果（getVoteInfo 读取 / 提交投票响应体共用同一结构）。
 * 实测字段：voteCount(总票数)、userCount(参与人数)、userOptionLimit(最多可选)、
 * canVote(是否还能投)、end(是否结束)、voteDetailList、userVoteRecordList(我投了哪几项)。
 */
data class HupuVoteResult(
    val voteId: Int,
    val title: String,
    val options: List<HupuVoteOption>,
    val totalVotes: Int,
    val userCount: Int,
    val limit: Int,
    val canVote: Boolean,
    val ended: Boolean,
    /** 当前登录用户已投的选项序号；未登录/未投为空 */
    val myChoices: List<Int>,
)

data class HupuThreadDetail(
    val thread: HupuThread,
    val contentHtml: String,
    val replies: List<HupuReply>,
    val replyCount: Int,
    val replyPage: Int,
    val replyTotalPages: Int,
    /** 1.185: 回复展示方向（true=最新在前，倒序分页）；默认 false=正序 */
    val descReplies: Boolean = false,
    /** 批F-3：官方详情 detail.hot（同区热帖，相关推荐卡数据） */
    val hot: List<HupuThread> = emptyList(),
    /** 批F-3：官方详情 detail.latest（同区最新帖） */
    val latest: List<HupuThread> = emptyList(),
    val isLocked: Boolean = false,
    /** 1.119: 正文里的投票（无投票帖为 null） */
    val vote: HupuVote? = null,
)

/** 浏览记录条目（「我的」页历史：帖子打开时写入，仅本地存储） */
data class HupuHistoryEntry(
    val tid: String,
    val title: String,
    val topicName: String = "",
    val lights: Int = 0,
    val replies: Int = 0,
    val read: Int = 0,
    /** 打开时刻（epoch ms） */
    val visitedAt: Long = 0L,
)

/** 话题（具体版块，如"步行街主干道"） */
data class HupuTopicInfo(
    val topicId: String,
    val name: String,
    val url: String,
    val desc: String = "",
    val hotText: String = "",
    val logo: String? = null,
)

/** 版块大类（如"步行街"），含话题列表 */
data class HupuCategory(
    val cateId: String,
    val name: String,
    val logo: String? = null,
    val topics: List<HupuTopicInfo> = emptyList(),
)

/** 版块热帖页数据 */
data class HupuBoardPage(
    val category: HupuTopicInfo?,
    val threads: List<HupuThread>,
    val hotTopics: List<HupuTopicInfo>,
    val recommendations: List<HupuThread>,
    /** 官方页内嵌的热搜词（热榜频道「大家都在搜」用） */
    val trending: List<String> = emptyList(),
)

/** 批F-4：官方热榜条目（m.hupu.com/hot SSR pageProps.res，微博式热搜 tag 榜） */
data class HupuHotTag(
    val tagId: String = "",
    val name: String = "",
    val heat: Long = 0,
    val icon: String = "",
    val rank: Int = 0,
    /** 赛事 / 非赛事（官方分类标签，暂仅透传） */
    val competitionType: String = "",
    /** 「每10分钟更新一次」等官方更新说明 */
    val updateDesc: String = "",
)

/** 批F-4：tag 聚合页头部（m.hupu.com/tag/{id} SSR topicTopInfo） */
data class HupuTagInfo(
    val tagId: String = "",
    val name: String = "",
    val icon: String = "",
    val banner: String = "",
    val bannerRgb: String = "",
    /** 话题帖数（tNum）与浏览量（pv） */
    val tNum: Long = 0,
    val pv: Long = 0,
    val followNum: Long = 0,
)

/** 批F-4：tag 聚合页帖行（topicThreadList/threadList 元素，字段与热帖流不同 schema） */
data class HupuTagThread(
    val tid: String = "",
    val userName: String = "",
    val header: String = "",
    val time: String = "",
    val title: String = "",
    val summary: String = "",
    val replies: Int = 0,
    val recommendCount: Int = 0,
    val shareNum: Int = 0,
    val pics: List<String> = emptyList(),
    val video: String = "",
)

/** 批F-4：tag 聚合页（SSR 首屏 page=1；/api/v2/bbs/tagThreads 分页只填 threads/nextPage/page） */
data class HupuTagPage(
    val info: HupuTagInfo = HupuTagInfo(),
    val threads: List<HupuTagThread> = emptyList(),
    val nextPage: Boolean = false,
    val page: Int = 1,
)

/** 话题页数据（含分页） */
data class HupuTopicPage(
    val topic: HupuTopicInfo,
    val threads: List<HupuThread>,
    val page: Int,
    val totalPages: Int,
    val sortTabs: List<SortTab>,
    /** 批L-2：SSR $$data.admins 的「专区信息」（版主团队 + 招募标记），无则空 */
    val boardInfo: HupuBoardInfo = HupuBoardInfo(),
)

/** 批L-2：版块 SSR $$data.admins 附带的专区信息（版主团队 + 招募版主标记） */
data class HupuBoardInfo(
    val title: String = "",
    /** 1=正在招募版主（官方「招募版主」入口的匿名可读标记） */
    val adminApplicable: Int = 0,
    val admins: List<HupuBoardAdmin> = emptyList(),
)

/** 批L-2：版主条目（SSR admins.adminList） */
data class HupuBoardAdmin(
    val roleName: String,
    val userName: String,
    val header: String,
    val url: String,
)

/** 批L-2：m站 zone 页 zoneData（JRs 成员数等，_next/data JSON） */
data class HupuZoneInfo(
    /** followedUserNum，如 "277.5万" */
    val memberText: String = "",
    val allThreadNum: String = "",
    val bgColor: String = "",
    val desc: String = "",
)

data class SortTab(val id: Int, val title: String, val url: String)

/** 搜索结果条目（bbs.hupu.com/search SSR 页 window.$$data.searchRes.data） */
data class HupuSearchItem(
    val tid: String,
    /** 已剥高亮标签的标题 */
    val title: String,
    /** 已剥高亮标签的摘要 */
    val desc: String,
    /** 封面图（可空） */
    val picture: String? = null,
    val replies: Int = 0,
    val lights: Int = 0,
    /** 发布时间戳（秒） */
    val addtime: Long = 0L,
    /** 相对时间文案（"9小时前"） */
    val addTimeDisplay: String = "",
    val fid: String = "",
    val forumName: String = "",
    val username: String = "",
)

/** 搜索结果页数据 */
data class HupuSearchPage(
    val items: List<HupuSearchItem>,
    val page: Int,
    val totalPages: Int,
    val count: Long = 0L,
)

/**
 * 批Z-1：用户搜索结果条目（m.hupu.com/api/v2/search2 type=users，匿名可读）。
 * 字段语义经用户主页 userInfoData 逐字段比对（2026-10-27 匿名实测 user 50082186/30256516）：
 *   lights  = be_light_count（获亮）；
 *   recNum  = be_recommend_count（推荐数，非粉丝——fans=be_follow_count 不在此接口下发）。
 */
data class HupuSearchUser(
    val puid: String,            // data.id（短 puid）
    /** 已剥高亮标签的用户名 */
    val username: String,
    /** 头像 URL */
    val header: String,
    /** 获亮数（be_light_count） */
    val lights: Long = 0L,
    /** 被推荐数（be_recommend_count） */
    val recNum: Long = 0L,
)

/** 批Z-1：search2 分类分页结果（type=users/topic 走 hasNextPage，非 totalPage）。 */
data class HupuSearchSlice<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean,
)

/** 搜索排序方式（sortby 参数，桌面版六种） */
data class SearchSort(val key: String, val title: String)

/** 桌面版搜索页的六种排序 */
val SEARCH_SORTS = listOf(
    SearchSort("general", "综合"),
    SearchSort("createtime", "最新发布"),
    SearchSort("createtimeasc", "最早发布"),
    SearchSort("replytime", "最新回复"),
    SearchSort("light", "最多亮评"),
    SearchSort("reply", "最多回复"),
)

// ---------- 比赛评分（match-api.hupu.com，匿名浏览） ----------

/** 赛程日分组 */
data class HupuMatchDay(
    val dayTime: String,
    val dateBlock: String,
    val matches: List<HupuMatch>,
)

/** 一场比赛（含选手评分卡） */
data class HupuMatch(
    val matchId: String,
    val statusDesc: String,          // 已结束 / 进行中 / 未开始
    val status: String,             // NOTSTARTED / INPROGRESS / COMPLETED / CANCELED（**无下划线**，见 HupuMatchParser）
    /**
     * 1.199：match-api 的 matchType：
     * · `against` = 一对一对抗（NBA / 足球 / 电竞 / 斯诺克 / 摔跤…）→ 有记分板
     * · `not_against` = 多人或非对抗（绝地求生决赛、和平精英小组赛、举重、F1 练习赛…）→ 不显示记分板
     * 实测：pubg 100/100、pubgmobile 85% 为 not_against，且其 memberName/memberBaseScore 全为 null。
     */
    val matchType: String = "",
    val introduction: String,       // LPL第三赛段组内赛
    val matchName: String,          // 电竞第三赛段组内赛
    val startTimeText: String,      // "7月30日 20:00" 之类（由时间戳格式化）
    val startTimestamp: Long,
    val scoreCountText: String,      // 3.3万人评分
    val home: HupuMatchTeam?,        // 左队
    val away: HupuMatchTeam?,        // 右队
    val winnerMemberId: String?,
    val playerScore: HupuPlayerScore?, // 选手评分卡（本场焦点选手）
    val scoreBizType: String?,          // 评分树钥匙（scoreItemKey.outBizType，如 lol_match）
    val scoreBizNo: String?,            // 评分树钥匙（scoreItemKey.outBizNo，如 3837）
    /** match-api 的 businessType（basketball/common_match/…）；单节比分等篮球数据接口按它分流 */
    val businessType: String = "",
    /** 比赛所属联赛名（全球赛程 oddsScheduleList 的 competitionName；普通赛程为空） */
    val competitionName: String = "",
    /** 预约信息（subscribeInfo；仅展示占位，不做真实订阅） */
    val subscribe: HupuSubscribe? = null,
)

/** 赛程行预约信息（match-api subscribeInfo 原样映射；只读展示） */
data class HupuSubscribe(
    val show: Boolean,        // showSubscribe：官方是否展示预约入口
    val subscribed: Boolean,  // didSubscribe：官方口径的已订阅态（匿名恒 false）
    val title: String,        // subScribeTitle
    val desc: String,         // subScribeDesc（如「湖人 vs 爵士」）
)

/** ============ 虎扑通用评分（m.hupu.com/score-home，非赛事体系） ============ */

/** 通用评分主题卡（首页 SSR __NEXT_DATA__.pageProps.list 一项） */
data class HupuCommonSubject(
    val bizType: String,                // "common_first"
    val bizNo: String,                  // 主题 id，如 12855
    val name: String,                   // "全职法师"
    val score: String,                  // 主题均分（可能为空串）
    val scoreCountNum: Long,            // 评分人数
    val desc: String,                   // 主题简介（可能为空）
    val bgColorDay: String?,            // 卡片日间底色（官方色）
    val scoreColorDay: String?,         // 分数日间色
    val items: List<HupuScoreItem>,     // 首页预置的部分子项（直接点进叶子详情）
)

/** 通用评分主题树（getCurAndSubNodeByBizKey CHILD：主题 → 子项流，分页续拉） */
data class HupuCommonTree(
    val name: String,
    val image: String?,
    val desc: String,
    val scoreAvg: String,
    val scorePersonCount: Long,
    val items: List<HupuScoreItem>,
    val totalCount: Long,
)

/** 评分树（bplcommentapi getCurAndSubNodeByBizKey，匿名可用）：比赛 → 对局[] → 选手[] */
data class HupuScoreTree(
    val name: String,                    // 比赛名，如 "HLE 3-2 T1"
    val image: String?,                  // 比赛封面
    val scorePersonCount: Long,          // 总评分人数（summedScorePersonCount）
    val rounds: List<HupuScoreRound>,    // 对局列表（正序：第1局在前）
    /** true = 扁平结构（NBA/WNBA 等传统体育：children 直接是选手，无对局层，UI 隐藏对局横滑条） */
    val flat: Boolean = false,
    /** 批V-6：足球详情头数据（football_match 根节点 infoJson，匿名可用）。
     *  篮球不走此路——matchInfo 由 basketballapi 单独供给且优先。 */
    val matchInfo: HupuMatchInfo? = null,
)

/** 一个对局（BO）及其选手 */
data class HupuScoreRound(
    val bizId: String,
    val name: String,                    // "第1局"
    val scorePersonCount: Long,
    val players: List<HupuScoreItem>,
)

/** 一名选手的评分 */
data class HupuScoreItem(
    val bizId: String,
    val bizType: String,                  // 跳转钥匙（lol_item）
    val name: String,
    val image: String?,
    val scoreAvg: String,                // "9.8"
    val scorePersonCount: Long,          // "6124人评分"
    val hotComment: String,              // 最热评论内容
    /** 所属队伍 id（infoJson.teamId，与赛程卡 againstInfo.memberId 同一命名空间）；
     *  为空表示中立角色（解说/主持/BP聚合），官方详情页将其归入「趣评」类 Tab */
    val teamId: String? = null,
    /** 所选英雄/角色小图标（infoJson.auxiliaryPic[0]，MOBA 赛事有值）；
     *  教练/中立角色为 null，偶有兜底图 → 渲染层「有才显示」 */
    val heroIcon: String? = null,
    /** 1.224k：评分对象**说明文本**（infoJson.desc；通用评分层为简介长文，赛事选手层为 KDA 短串）。
     *  此前它只在叶子详情页（getSelfByBizKey）取得到，二级页的行也能拿同一份就一并解析出来展示。 */
    val intro: String = "",
)

/**
 * 1.224k：`infoJson.desc[0]` → **评分对象说明文本**（二级页每行名称下方那行）。
 *
 * 这里**只做两处收敛**，其余一律原样返回 —— 短文本（如电竞选手的 `K/D/A:1/3/20`）
 * 也是要显示的，绝不能再按长度或 KDA 形态过滤掉：
 *  · 空 / 全空白 → 返回空（渲染层不占位，不画空行）；
 *  · 取到的是 JSON 数组/对象字面量（以 `[` / `{` 开头）→ 按空处理。
 *    踩过的坑：解析时先试数组取第 0 项、空了再回落到「整个 desc 字段」，
 *    而 desc 本身就是数组 → 回落拿到数组本体，界面上就出现 `[""]` 字样。
 */
internal fun scoreIntroFromDesc(desc: String?): String {
    val d = desc?.trim().orEmpty()
    if (d.isEmpty()) return ""
    if (d.startsWith("[") || d.startsWith("{")) return ""
    return d
}

/** 服务端分组定义（getSubGroups 响应项）：分类子 Tab 的数据源（官方对所有赛事通用） */
data class HupuScoreGroup(
    val groupId: Long,
    val name: String,
    val logo: String?,
    val rootNodeId: Long,   // groupAndSubNodes?nodeId= 拉取该组成员
    val childCount: Int,
)

/** 选手/对局节点详情（getSelfByBizKey data.detail，第 4 层） */
data class HupuSelfDetail(
    val bizType: String,
    val bizId: String,
    val name: String,
    val image: String?,
    val scoreAvg: String,                // "9.8"
    val scorePersonCount: Long,
    val kda: String,                     // "K/D/A:1/3/20"（infoJson.desc 为 KDA 特征短串时）
    val intro: String = "",              // 评分对象介绍（infoJson.desc 为长文时，通用评分层；内容区介绍卡展示）
    /** 所选英雄/角色小图标（infoJson.auxiliaryPic[0]，MOBA 赛事有值；非 MOBA/教练为 null） */
    val heroIcon: String? = null,
    val distribution: List<Pair<String, Long>>,   // 评分分布（档位→人数，降序：10 分在前）
    val hottestComments: List<String>,    // 热评文本
    /** 登录态：当前用户已打的分（getSelfByBizKey detail.userScore，10 制；0=未打分）——1.64 */
    val userScore: Int = 0,
)

/** 评分区一条评论（primarySingleRow，匿名浏览） */
data class HupuScoreComment(
    val commentId: String,
    val userName: String,
    val userHead: String?,
    /** 1.163: 评论作者的用户 id（来源 commentUserId；数字型，可直接当 euid 打开用户主页） */
    val userId: String = "",
    val content: String,
    val score: Int,                       // 该用户打的分（10 制，0=无）
    val lightCount: Long,                 // 亮数
    val date: String,                     // "18小时前"
    val ipLocation: String,
    /** 楼中楼：子回复数（subCommentCount；官方主响应只内嵌前几条） */
    val subCommentCount: Int = 0,
    /** 楼中楼：后代回复数（descendantCount=回复的回复；官方「全部回复 N」= subCommentCount+descendantCount） */
    val descendantCount: Int = 0,
    /** 楼中楼：内嵌子回复（subCommentList，官方默认带 1~3 条；深层是扁平后代树） */
    val subComments: List<HupuScoreComment> = emptyList(),
    /** 宿主评论 id（parentCommentId；区分 direct 子回复 vs 深层后代——楼中楼展开去重用） */
    val parentCommentId: String = "",
    /** 附件图片（commentContentImages：元素 commentContent=URL；纯图评论 content 为空） */
    val images: List<String> = emptyList(),
    /** 点亮目标 subjectId（评论 commentKey.subjectId——实测 ≠ outBizNo，必须用评论自带值） */
    val subjectId: String = "",
    /** 登录态：当前用户是否已点亮（hasLight；匿名恒 false） */
    val hasLight: Boolean = false,
)
/**
 * 服务端评论 ⊕ 本机乐观条目（1.151）。
 * 两个坑一起解决：
 *  1) 重复：乐观条目用服务端真实 commentId 建，按 id 合并，服务端稍后返回同一条时不会出现两条；
 *  2) 带图回复「图消失」：刚发送时服务端可能先回一条还没有 commentContentImages 的副本，
 *     而它排在乐观条目之前——简单 distinctBy 会让服务端空图副本胜出，于是图就没了。
 *     这里按 commentId 对齐，图片以信息更全的一方为准。
 */
internal fun mergeWithOptimistic(
    server: List<HupuScoreComment>,
    optimistic: List<HupuScoreComment>,
): List<HupuScoreComment> {
    if (optimistic.isEmpty()) return server
    val local = optimistic.associateBy { it.commentId }
    val merged = server.map { s ->
        val l = local[s.commentId] ?: return@map s
        if (l.images.size > s.images.size) s.copy(images = l.images) else s
    }
    val ids = server.mapTo(HashSet()) { it.commentId }
    return merged + optimistic.filterNot { it.commentId in ids }
}

/** 孙评论「展开更多回复」状态（楼中楼内某子评论的；commentId 键控、可多次点击分页） */
data class GrandExpandState(
    /** 已追加显示的孙评论（拉平后的平级列表，与内嵌 subComments 去重合并） */
    val comments: List<HupuScoreComment> = emptyList(),
    /** 下一页游标（孙评论端点 cursor.publishTime） */
    val cursor: Long = 0L,
    /** 还有下一页（多次点击展开） */
    val hasMore: Boolean = false,
    /** 加载中（按钮转圈） */
    val loading: Boolean = false,
    /** 已完成过一次请求（成功响应即置位；失败不置位——按钮保留可重试） */
    val attempted: Boolean = false,
)
/** 递归拉平评论及其全部后代（先序：本条在前，后代随后）——楼中楼「展开更多回复」合并去重用 */
fun flattenWithDescendants(c: HupuScoreComment): List<HupuScoreComment> =
    listOf(c) + c.subComments.flatMap { flattenWithDescendants(it) }

/** 评论流状态（游标分页：cursor=下一页 publishTime） */
data class ScoreCommentState(
    val comments: List<HupuScoreComment> = emptyList(),
    val commentCount: Long = 0L,
    val cursor: Long = 0L,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/** 参赛队伍 */
data class HupuMatchTeam(
    val memberId: String,
    val name: String,
    val logo: String?,
    val baseScore: String,    // 2:0 的比分
    val extraScore: String?,  // 加时/额外分
    val bigScore: String?,    // 大分（BO 赛制总局数）
)

/** 选手评分卡（官方展示的本场焦点评分） */
data class HupuPlayerScore(
    val name: String,
    val logo: String?,
    val teamLogo: String?,
    val scoreNum: String,        // "2.4"
    val scoreCountText: String,   // "2941人评分"
    val hotComment: String,      // 热评
)


/** 用户主页（资料卡 + 双 Tab 首屏，SSR 一次请求全量给出） */
data class HupuUserProfile(
    val euid: String = "",
    val puid: String = "",
    val name: String = "",
    val avatar: String = "",
    // IP 属地（如 "广东"）
    val locationStr: String = "",
    // 如 "加入虎扔2127天"
    val regTimeStr: String = "",
    // 等级体系：Lv 徽章 + 进度条（官方色 #704BE5）
    val level: String = "",
    val levelDesc: String = "",
    val levelColor: String = "",
    val levelScore: Long = 0L,
    val nextLevelScore: Long = 0L,
    val levelPercent: Double = 0.0,
    // 统计：粉丝/关注/被点亮/被推荐（后两者不封顶，与详情页口径一致）
    val followers: Int = 0,
    val following: Int = 0,
    val beLightCount: Int = 0,
    val beRecommendCount: Int = 0,
    val msgCount: Int = 0,
    val postCount: Int = 0,
    // PC 个人中心口径：推荐数 / 收藏数（收藏仅自己可见）；isSelf 由 getUserInfo 下发
    val recommendCount: Int = 0,
    val favoriteCount: Int = 0,
    val isSelf: Boolean = false,
    val reputation: Int = 0,
    val threads: List<HupuProfileThread> = emptyList(),
    val replies: List<HupuProfileReply> = emptyList(),
)

/** 用户主页主题帖条目（threadList；recommend_num 为真实推荐数，不封顶） */
data class HupuProfileThread(
    val tid: String,
    val title: String = "",
    val forumName: String = "",
    val topicName: String = "",
    val replies: Int = 0,
    val recommendNum: Int = 0,
    val createdAtText: String = "",
    val summary: String = "",
    val cover: String? = null,
)

/** 用户主页回帖条目（replyList：content 为纯文本，threadTitle 为所在帖标题） */
data class HupuProfileReply(
    val pid: String,
    val tid: String = "",
    val content: String = "",
    val formatTime: String = "",
    val lights: Int = 0,
    val threadTitle: String = "",
    val cover: String? = null,
)

/** PC 个人中心关注列表条目（getUserFollowList） */
data class HupuFollowUser(
    val puid: String = "",
    val name: String = "",
    val avatar: String = "",
    val level: Int = -1,
    val fansNum: Int = 0,
    val joinDaysText: String = "",
    val threadsNum: Int = 0,
    val repliesNum: Int = 0,
    val isSelf: Boolean = false,
)

/**
 * board：板块资讯条目。
 * 两套来源共用同一结构：
 *  · games.mobileapi `basketballapi/news/v2/newsList`（nba/lol/cba/epl/csl/digital 等 tagCode）
 *  · m.hupu.com/soccer SSR `pageProps.news`（国际足球）
 * tid 非空即可按帖子打开（官方 link 形如 kanqiu://bbs/topic/{tid} / m.hupu.com/bbs/{tid}）；
 * 为空的条目是运营位（type=LINK 的倒计时/活动卡），按 link 兜底处理。
 */
data class HupuBoardNews(
    val nid: String,
    val tid: String = "",
    val type: String = "",
    val title: String,
    val img: String = "",
    val link: String = "",
    /** 落地链接类型：普通帖为 bbs 链接；LINK 运营位多为 CUSTOM
     *  （huputiyu://webview/openencodeurl?url=<编码后的 https 活动页>） */
    val linkType: String = "",
    val replies: Int = 0,
    val lights: Int = 0,
    /** "2026-10-15 04:51:33" 服务端格式化时间 */
    val publishTime: String = "",
    /** epoch 秒（newsList 的 addTime 字段） */
    val addTime: Long = 0L,
    /** 置顶标记（newsList 的 top 字段 / badge 含「置顶」） */
    val top: Boolean = false,
    /** 角标名列表（置顶/精选/流言板 等，来自 badge[].name） */
    val badges: List<String> = emptyList(),
) {
    /** 可按帖子打开（有 tid） */
    val openableAsThread: Boolean get() = tid.isNotEmpty()
}

/** 一页板块资讯。 */
data class HupuBoardNewsPage(
    val items: List<HupuBoardNews>,
    /** 服务端置顶的条数（data 前 N 条为置顶位） */
    val topNewsCount: Int = 0,
    /** 翻页游标：下一次请求的 preCount（本页累计条数） */
    val nextPreCount: Int = 0,
    /** 翻页锚点：下一次请求的 newId（本页首条 nid；首页为空串） */
    val anchorNid: String = "",
)

// ---------- board：积分榜（Step 3）----------

/**
 * 积分榜一行。NBA（nba.hupu.com/standings SSR 表格）与足球
 * （football-api competition/ranking JSON）共用此结构；[cells] 与
 * 所属组 [HupuStandingGroup.columns] 一一对应（均不含排名/队名两列）。
 */
data class HupuStandingRow(
    val rank: String,
    val team: String,
    /** 队徽 url（足球有，NBA SSR 表无图） */
    val teamLogo: String = "",
    val cells: List<String>,
    /** 排名色标（足球升降级区底色，如 "#FFEBEB"；空=无） */
    val color: String = "",
)

/** 积分榜分组（东/西部、联赛排名、小组赛 A 组…）。 */
data class HupuStandingGroup(
    val title: String,
    /** 数据列头（不含排名/队名） */
    val columns: List<String>,
    val rows: List<HupuStandingRow>,
)

data class HupuStandings(
    /** 榜单名（英超 / NBA） */
    val league: String,
    val groups: List<HupuStandingGroup>,
)

/** ============ 批B：联赛数据中枢（games.mobileapi basketballapi + match-api matchallapi，匿名 GET） ============ */

/** 赛程中心 tab 项（matchallapi/getDataTabList 一项）。
 *  linkType: oddsSchedule=全球页 / schedule=赛程页 / h5=官方 H5 数据页（可内嵌 WebView）。 */
data class HupuDataTab(
    val id: String,
    val name: String,
    val linkType: String,
    val url: String,
)

/** 赛季选项（basketballapi/switchableRankSeasonList 的 seasonData 一项）。 */
data class HupuSeasonOption(
    val season: String,          // "2026-2027"
    val shortSeason: String,     // "2026-27"
    val seasonType: String,      // REGULAR / PRESEASON / PLAYOFF …
    val seasonTypeName: String,  // 常规赛
    val selected: Boolean,
)

/** 积分榜左栏类别（东部/西部/分区/技术统计…）：标题 + 可并列的分组表。 */
data class HupuRankCategory(
    val id: String,
    val title: String,
    val groups: List<HupuStandingGroup>,
)

/** 球队榜整版（basketballapi/teamStandingList + teamSeasonStatsAllRank）。 */
data class HupuStandingsBoard(
    val leagueName: String,      // "NBA"
    val season: String,          // "2026-2027"
    val stageName: String,       // 常规赛（competitionStageTypeName）
    val categories: List<HupuRankCategory>,
    /** 技术统计榜（teamSeasonStatsAllRank 各项，每项一个分组表，top5） */
    val statCategories: List<HupuStandingGroup>,
)

/** 球员榜单项 top-N 行（playerSeasonRank rankInfoList 一项）。 */
data class HupuRankEntry(
    val rank: Int,
    val playerId: String,
    val name: String,
    val teamName: String,
    val photo: String,
    val value: String,
)

/** 球员榜统计项（如「得分」basic.ptsAvg）。 */
data class HupuRankItem(
    val key: String,             // engName，如 basic.ptsAvg
    val name: String,            // chineseName，如 得分
    val leaders: List<HupuRankEntry>,
    /** 官方下发 hasMore（top5 摘要是否还有全量榜可展开） */
    val hasMore: Boolean = false,
)

/** 球员榜维度（基础/进阶）。 */
data class HupuRankDimension(
    val key: String,             // basic / advanced
    val name: String,
    val items: List<HupuRankItem>,
)

/** 球员榜整版（basketballapi/playerSeasonRank）。 */
data class HupuPlayerRankBoard(
    val season: String,
    val stageName: String,
    val dimensions: List<HupuRankDimension>,
)

/** 选秀一行（basketballapi/players/draft draftMap 的一项）。 */
data class HupuDraftPick(
    val round: Int,
    val rank: Int,               // 顺位
    val playerId: String,
    val name: String,
    val photo: String,
    val teamName: String,        // draftTeamName（选中球队）
    val position: String,
    val heightCm: Int,
)

/** 选秀整版（draftYearConfig 年份表 + draftMap 按轮次分组）。 */
data class HupuDraftBoard(
    val year: String,
    val years: List<String>,
    val rounds: List<Pair<Int, List<HupuDraftPick>>>,
)

/** 伤病一行（basketballapi/allInjuryList playerInjuryDTOList 一项）。 */
data class HupuInjury(
    val playerId: String,
    val name: String,
    val photo: String,
    val teamName: String,
    val part: String,      // injuryPart 部位（后背受伤）
    val status: String,    // injuryStatus（每日观察/无法出场）
    val date: String,      // injuryDate
    val detail: String,    // zhDetail（可空）
)

/** 单节比分（basketballapi/teamMatchQuarterStats 的 home/awayQuarterScore）。 */
data class HupuQuarterScore(
    val home: List<String>,
    val away: List<String>,
)

/** 批Y：荣誉榜获奖者（allPlayerAwardList playerAwardDetailDTOList 一项）。 */
data class HupuAwardWinner(
    val playerId: String,
    val name: String,
    val photo: String,
    val teamName: String,
    val season: String,      // awardDate（获奖赛季，如 2025-2026）
    val pts: String,
    val reb: String,
    val asts: String,
)

/** 批Y：荣誉榜奖项分组（allPlayerAwardList playerAwardMapDTOList 一项，top5 近年获奖者）。 */
data class HupuAward(
    val awardName: String,   // NBA_Most_Valuable_Player
    val awardDesc: String,   // MVP
    val winners: List<HupuAwardWinner>,
)

/** 批Y：历史榜整版（playerAllDimensionCareerRank）——生涯累计榜，
 *  维度结构与球员季榜同构（基础/进阶/关键时刻/拼抢），无赛季选择。 */
data class HupuCareerRankBoard(
    val stage: String,       // REGULAR / PLAYOFF
    val stageName: String,   // 常规赛 / 季后赛
    val grain: String,       // pergame / totals
    val dimensions: List<HupuRankDimension>,
)

/** 批Y：对阵图一个系列赛格（against-plan-chart planChartItemList 一项）。
 *  planChartNumber="行-列" 标定 bracket 位置；主客两队 + winnerTeamId 标晋级。 */
data class HupuAgainstCell(
    val row: Int,
    val col: Int,
    val teamId: String,
    val teamName: String,
    val teamLogo: String,
    val teamScore: String,       // 本队系列赛胜场（score 字段）
    val rank: String,            // 种子位次（官方左角 rank 徽标）
    val oppoTeamId: String,
    val oppoTeamName: String,
    val oppoTeamLogo: String,
    val oppoScore: String,
    val winnerTeamId: String,
)

/** 批Y：对阵图整版（against-plan-chart）。NBA 七列 bracket：
 *  东 R1(4)/东 R2(2)/东决(1)/总决(1)/西决(1)/西 R2(2)/西 R1(4)。 */
data class HupuAgainstBracket(
    val cells: List<HupuAgainstCell>,
)

/**
 * 批U-1：篮球比赛详情头（basketballapi/matchInfo?matchId= 的 result 子集）。
 *
 * 只收详情页要用的字段；`homeRank/awayRank/homeArea/awayArea` 官方在旧完赛场返回 null——
 * 渲染层按「有才显示」处理（排名行整体缺省时省略，不画占位）。
 */
data class HupuMatchInfo(
    val matchId: String,
    val matchStatus: String,            // COMPLETED / INPROGRESS / NOTSTARTED
    val matchStatusChinese: String,     // 「已结束」官方中文态
    val matchTime: String,              // "2026-10-24 04:00:00"
    /** 开赛毫秒时间戳（chinaStartTime——官方「中国时间」字段，详情头日期文案按它格式化） */
    val chinaStartTime: Long = 0L,
    val homeScore: String,
    val awayScore: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeTeamLogo: String?,
    val awayTeamLogo: String?,
    /** 官方队色（日间 hex 无前缀，如 "204224"）；走势图/队标底色可复用 */
    val homeTeamDayColor: String = "",
    val awayTeamDayColor: String = "",
    /** 排名（官方在部分场次返回 null——有才显示） */
    val homeRank: String = "",
    val awayRank: String = "",
    /** 分区（「西部」/「东部」；足球可能为联赛分组名） */
    val homeArea: String = "",
    val awayArea: String = "",
    /** 到场人数（0/空不显） */
    val attendance: Long = 0L,
    /** 耗时（"2:27"；空不显） */
    val costTime: String = "",
    /** 官方评分人数文案（"3.6万评分"） */
    val scoreText: String = "",
    /** 赛季（"2026-2027"） */
    val season: String = "",
    /** 批V-6：联赛+轮次文案（football_match 树 infoJson 的 competitionTypeCn+roundCn，如「德乙 第9轮」；空不显） */
    val leagueDesc: String = "",
)

/** 批U-3：篮球单场球员统计一行（basketballapi/playerMatchStats 的 homePlayers/awayPlayers 一项）。 */
data class HupuMatchPlayerStat(
    val playerId: String,
    val name: String,
    val logoUrl: String?,
    val position: String,   // G/F/C
    val starter: Boolean,
    val mins: String,       // "31:34"
    val pts: String,
    val reb: String,
    val asts: String,
    /** 球员评分（userScoreAvg 10制 / userScoreCount 原始计数——详情页口径非万制） */
    val userScoreAvg: String = "",
    val userScoreCount: Long = 0L,
)

/** 批U-3：单场球员统计整版（主客两队 + 官方 sortList 列序）。 */
data class HupuMatchPlayerStats(
    val matchId: String,
    val home: List<HupuMatchPlayerStat>,
    val away: List<HupuMatchPlayerStat>,
)

/** ============ 球队页（nba.hupu.com/teams|schedule/{slug} SSR） ============ */

/** 阵容一行：号码 / 姓名 / 位置 / 年龄 / 身高 / 体重 */
data class HupuTeamPlayer(
    val no: String,
    val name: String,
    val pos: String,
    val age: String,
    val height: String,
    val weight: String,
)

/** 球队赛程一行（/schedule/{slug} 全季滚窗表；结果列是本队视角） */
data class HupuTeamGame(
    val dateTime: String,     // 北京时间 "2026-10-04 07:00:00"
    val opponent: String,     // 对手中文名
    val isHome: Boolean,      // 本队为主队（页面里本队名字加粗）
    val score: String,        // "129-105" / 未赛 "-"
    val result: String,       // 胜 / 负 / 未赛 "-"
)

/** 球队信息（/teams/{slug}）：队名 + 信息行 + 简介 + 阵容 */
data class HupuTeamInfo(
    val fullName: String,                    // 迈阿密热火（Miami Heat）
    val logo: String = "",
    /** 信息行（进入NBA/主场/分区/官网/主教练…），键值对保持页面顺序 */
    val infos: List<Pair<String, String>> = emptyList(),
    val intro: String = "",
    val roster: List<HupuTeamPlayer> = emptyList(),
)
