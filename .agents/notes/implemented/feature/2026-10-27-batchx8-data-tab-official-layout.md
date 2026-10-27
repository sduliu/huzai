# Agent Note: 批X8 数据中心四 tab 版式官方化（球队榜/球员榜/选秀/伤病）

Status: implemented

## Problem

2026-10-27 Muse 同机同天双 App 比对：四个数据 tab 数值已与官方一致，差在版式与一处 bug——球队榜队名全量显示为「…」；球员榜/选秀/伤病缺左栏目录、赛季下拉、官方列头与行结构。

## Root cause（队名「…」）

非字段映射问题：`teamStandingList` 的 `teamShortName`/`teamName` 均正常返回中文队名。根因是 `StandingGroupTable` 旧布局 5 个独立数据列各 34dp（共 170dp）+ 排名列 24dp + 队标，在 96dp 左栏挤压下 `weight(1f)` 的队名列仅剩 ~30dp，`TextOverflow.Ellipsis` 把所有队名折叠成「…」。

## Decision

- **球队榜**：数据列 5→4（官方列头「胜-负/胜率/胜场差/近况」，胜-负合并一格「3-0」），列宽 42dp、排名列 20dp、队标 16dp、卡内边距 10dp、左栏 84dp。首列表头=组名（「东部排行」即列头，官方形态）。积分榜类（非 stat- 前缀）排名 1-6 红 #C01E2F、7+ 金 #B8860B。
- **左栏分类来源**：技术统计项由 `teamSeasonStatsAllRank` 返回的 39 项逐项扁平为独立左栏类别（官方左栏即逐项铺开，非「技术统计」一档）——`allCategories` 内每个 statGroup 映射为单 group 的 HupuRankCategory，id=`stat-<title>`；30+ 项左栏必须 LazyColumn 可滚动。
- **赛季选择器**：`SeasonChips` 内部实现改为官方下拉（文本+▾+DropdownMenu，「2026-27常规赛」），函数签名不变；选秀年份同款下拉「2026年」（draftYearConfig 升序列表→菜单降序）。
- **球员榜**：`HupuRankEntry` 增 `pts/reb/ast`（rankInfoList 自带字段，此前被丢弃）→ 行副行「34.7分 4.3板 6.0助」三字段齐全才渲。左栏=当前维度统计项逐项，点击滚动定位到卡（LazyListState.animateScrollToItem）。卡头加「球队」「数据」列头，数据值主题蓝→黑，「查看完整榜单」→灰字「查看全部」（保留全量榜 overlay）。切基础/进阶维度重置左栏选中。
- **选秀**：左栏=board.rounds 逐轮（第一轮/第二轮）；卡头「第N轮|球队|数据」；顺位黑；球队 48dp 居中列；数据列「前锋 2.06米」（`heightCm/100` 两位小数）。
- **伤病**：新增 `parseInjuryGroups`——「最新」=playerInjuryDTOList（79 条全量）+ teamInjuryPlayerListMap 各队；**队序按 JSON 文档序提取**：org.json `JSONObject.keys()` 是 HashMap 无序，故在原始串中定位 `teamInjuryPlayerListMap` 对象区间、正则 `"key":[` 按出现序取队名（跌穿失败回退无序 keys）。行=头像+姓名+队名灰字|伤停(injuryPart)|最新进展纯文字（zhDetail 优先，缺省 injuryStatus）——pill 与行内日期按官方口径移除。组内 >5 行出现「查看全部」就地展开（allInjuryList 已全量，无独立 full 页接口，不外跳编链接）。

## Alternatives considered

- 「查看全部」跳 WebView 全量页：接口无 allUrl，官方跳转目标未知，就地展开不编链接。
- 伤病队序按队名字典序/队 ID 排序：官方序（湖人/勇士/马刺/快船…）非字典序，文档序即服务端序是唯一可靠来源。
- 球员榜右栏滚动联动高亮左栏：过度工程，点击定位已满足官方交互。
- 「胜率/胜场差」合并一格「100.0%/0.0」：官方列头是四个独立标签，行内两值分格显示在每列下即可等宽对齐，不引入跨列合并复杂度。

## Consequences

- 改动面：`ScoreDataPages.kt`（四视图）+ `HupuDataParser`（列合并/场均列头/伤病分组）+ `HupuModels`（HupuRankEntry +3 字段）+ `HupuRepository`（injuryGroups）+ 测试断言同步。赛程/频道/详情页零触碰。
- `repo.injuries`/`parseInjuries` 保留（平铺形态），InjuryView 已切分组接口。
- 左栏各视图统一 84dp/13sp/两行截断模式（与球队榜同款）。
- 回退：逐项 commit revert 即可（65073ff/da36079/0725bb9/b2be941）。
