# Agent Note: 比赛详情页复核（批 X7）——官方逐项比对与差异修复

Status: implemented

## Problem

批 U/V 已按 2026-10-26 官方实测对齐详情页大结构，但从未按同机同天逐项复核。
本批对照官方截图（`kudo-hupu-compare/DEEP_off_match.png` / `DEEP_off_stats.png`，
官方虎扑 8.2.66.10232，灰熊 123-112 国王场）逐项复核，真实差异 11 处，已全部修复。

## Evidence

- 官方头部：`<`返回+分享(icon+「分享」字) 内嵌深区；队标两侧；大比分分居中轴两侧
  （`123 | 已结束/10月26日/01:00 | 112`）；日期与时间两行；排名行「客/西部第4」。
- 官方 Tab：战报/虎扑评分/统计/讨论/直播 五项，选中态=黑粗字+红下划线（虎扑红），
  默认落「虎扑评分」。直播为产品排除项（直播不做）→ 我方四项不变。
- 官方评分 tab：灰底容器两枚等宽队胶囊（队标+队名，客队左默认选中，无「全部」）；
  球员卡=大方图+姓名+五枚灰星（不随分填充）+ 数据行「31分 4板 6助 1断 1失误 1犯规」
  + 蓝色大分 + `9087JRs评分` + 橙引言块 + 卡内右下分享 icon。
- 官方统计 tab：节次表首列「球队」表头、数据行该列仅队标；走势图无卡标题、
  图例在图内左上、纵轴 0-150 步 30、横轴「第N节」。
- 匿名源探测（直连实测 2026-10-27）：
  · `basketballapi/playerMatchStats` 球员行本有 `stl/blk/to/pf` 等全字段（`to`=失误、`pf`=犯规），
    但更优源在评分树：`getCurAndSubNodeByBizKey` 节点 `infoJson.desc[0]` 直接下发官方成品串
    「16分 5板 2助 1断 1失误 1犯规」——已解析进 `HupuScoreItem.intro`，卡片直渲即可，零拼接。
  · `getSubGroups` 对 `basketball_match` 实测返空数组 → 篮球球队切换不能走服务端分组，
    由赛程卡两队（memberId/name/logo）本地构建。
  · `matchInfo` 的 `homeRank/awayRank/homeArea/awayArea` 近期场次实测全 null
    （官方仅部分场下发，有才显示）；字段值形态 `rank` 自带「第」。

## Decision

1. 深色头：`MatchDarkHeader` 接管返回+分享（`statusBarsPadding` 内嵌），有深色头信息
   （篮球/足球）时不再画浅色 `CenteredTopBar`；比分 28sp 分列中轴两侧；日期/时间拆两行
   （`matchInfoDateTime` 返回 Pair）；排名行去多余「名」（`rank` 自带「第」）。
2. Tab 条：选中=onSurface 黑粗字 + `MatchHupuRed`(#C01E2F) 下划线；无直播（排除项）。
3. 评分 tab：`BasketballTeamSwitch` 两胶囊（灰底容器+白底选中枚，客队左默认）；
   `BasketballRatingRow` 数据行优先 `p.intro`（官方串），空回落 playerMatchStats 三项 join；
   星改全灰；头像 44→56dp；卡内右下补分享 icon（ACTION_SEND 纯文本）。
4. 统计 tab：节次表补「球队」列头、数据行仅队标（logo 缺回落队名）；走势图去「比分走势」
   标题、图例移入图内左上、`maxScore` 上取整到 `yStep` 倍数（官方 0-150 步 30 口径）。

## Alternatives considered

- playerMatchStats 补 `stl/blk/to/pf` 再自拼六项：可行但多余——评分树 `desc` 已带官方成品串
  （语序/缩写与官方逐字一致），直渲 `intro` 最诚实也最简。join 保留为 desc 缺省时的兜底。
- 服务端分组（getSubGroups）驱动队胶囊：实测 basketball_match 返空，不可用；
  只能走赛程卡两队本地构建。

## Consequences

- 深色头路径（篮球 + 足球 V-6 兜底）不再有浅色标题栏——返回手势/系统返回不变，
  PredictiveBackHandler 不动；电竞等无深色头信息的赛事仍走旧浅色顶栏+ScoreboardBanner。
- 非篮球赛事（电竞/足球）评分列表分类条逻辑零改动（`useBbPills` 仅 basketball）。
- 无登录态引入；战报/讨论仍显式空态（官方口径：无匿名结构化源 / canComment=false）。
