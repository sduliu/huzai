# Agent Note: 批Y 数据中心 tab 补齐——荣誉榜/历史榜原生接入，对阵图接源显式空态，战报复探维持空态

Status: implemented

## Problem

官方 NBA 数据中心 tab 轨道（matchallapi/getDataTabList，2026-10-27 匿名实测）共 9 个 tab：全球、赛程、球队榜、球员榜、选秀、伤病、对阵图、荣誉榜、历史榜。此前酷DO 只接前 6 个；对阵图/荣誉榜/历史榜回落 EmbedWebPage 官方 H5（从未核验过匿名源与渲染），比赛详情「战报」tab 沿用批U-0「无匿名源→空态」结论但未在本批复验。积分榜左栏（东/西/分区/技术统计）只有东部被真机验过。

## Decision

- **逐项先探匿名源再接**：每 tab 的 tabUrl（300003/<league>/<name>.html）反查其 H5 JS bundle 里实际调用的 basketballapi 端点与实参，匿名 GET 复验后才接；不接的写清原因。
- **荣誉榜 honor.html → `basketballapi/allPlayerAwardList?leagueType=NBA&competitionType=NBA`**（大写实参照抄 honor.js）：result.playerAwardMapDTOList 20 奖项 × 近年 top5 获奖者（pts/reb/asts 数字随行至 1 位小数）。原生 `AwardView` 奖项卡 + 获奖者行渲染；获奖者全空的奖项不渲染。
- **历史榜 history.html → `basketballapi/playerAllDimensionCareerRank?leagueType=NBA&competitionType=NBA&competitionStage={REGULAR|PLAYOFF}&grain={pergame|totals}`**：result.dataDimensionMap[STAGE][0].itemList 与 playerSeasonRank 同构 → 复用 HupuRankDimension/HupuRankItem/HupuRankEntry；官方页再滤掉「场均进球数/场均命中率/场均罚球命中数」三个足球残留项，解析层同款过滤。
- **对阵图 against.html → `basketballapi/against-plan-chart?leagueType=<l>&planChartType=<L>_PLAYOFF`**：端点匿名可达（参数缺省报错「planChartType is blank」佐证路由存在），但 NBA/CBA 全赛季 `result:null`——官方数据源当前为空（官方页同样渲染空图+规则文案）。接入端点 + 解析，`AgainstView` 有数据画 bracket、空则显式空态，不再回落 embed。
- **战报复探（2026-10-27）**：`matchInfo` 全字段无 report/article 链接；score_tree 根节点 infoJson `link=[""]` 仍空、`linkNodes=[]`；新猜 matchReport/matchNews/matchReportList/getMatchReport/matchDetail/matchArticleList 六端点全部「No static resource」。叠加批U-0 量取（matchReport/matchRecap/matchNews 404），结论维持：**战报是编辑帖、无匿名结构化源** → 保留「本场暂无战报」显式空态，不编数据。
- **积分榜左栏**：parser 已把 rankTypeListMap{E,W}→东/西部、divRankTypeListMap→分区排行、teamSeasonStatsAllRank→技术统计合成四类，JVM 测试锁定；无需改代码，本批只补回归证据。

## Alternatives considered

- 对阵图/荣誉榜/历史榜继续回落 EmbedWebPage 官方 H5：最省事，但 embed 页是整页 WebView（白屏检测脚本+离线包链路），对阵图在官方页本就是空图，embed 只多不少问题；有匿名 JSON 的接原生更符合「缺失渲染补齐」。
- 对阵图用 wnba 的 `common/queryAllDiagramInfoById`（common/match.html?diagramId=150 通路）：该端点不在 games.mobileapi 路由（实测「No static resource」），参数面未探明，且 NBA 官方 tab 自身就走老 against-plan-chart——不越权换源。
- 战报从 bbs 资讯侧猜关联帖（按 matchId 搜编辑帖）：无 join 键、命中率不可证，属于「编数据」路径——弃。
- 历史榜 grain 猜「total/sum」：实测 `grain=total` 报 invalid grain，history.js 证实枚举为 `pergame|totals`——照抄官方枚举。

## Consequences

- NBA 9 个官方数据 tab 全部有原生落地：6 旧 + 荣誉/历史/对阵图 3 新（对阵图为接源的空态页）。剩余 h5-only tab（如 cba 的 cnPlayer/daily、wnba 的 common/match.html 季后赛图与资讯）继续回落 EmbedWebPage，不属本批 NBA 主目标。
- 足球数据中心：epl/csl getDataTabList 仅回「赛程」一个 tab，laliga/soccer 空——官方足球无数据中心 tab 轨道，无欠账。
- 验证：HupuDataParserTest 新增 awards/careerRank/againstChart 三个解析用例 + 积分榜四类左栏回归断言；真机渲染由 Muse 验收。
- 回退：各 tab 一个 commit，revert 对应 commit 即回 embed（对阵图 revert 后回落官方 H5）。
