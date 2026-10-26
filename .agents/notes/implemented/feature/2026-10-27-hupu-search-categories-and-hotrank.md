# Agent Note: 批Z 搜索分类接入与热榜行/空态对齐——search2 三匿名类（帖子/版块/用户），热帖榜官方meta

Status: implemented

## Problem

搜索页此前只渲「帖子」单类（桌面 `bbs.hupu.com/search` HTML）；官方 `search2` 的分类能力（版块/用户）从未核验，联想词行为未知，行 meta 缺作者。热榜页（HotChannelPage）从未真机核验：tag 榜空/失败时静默缺席，「热帖榜」行 meta（👍/💬）非官方形态，整页取数失败时无限 spinner 无错误态。

## Decision

- **分类口径**：官方 `search2` 三匿名类有源（posts count=600、users count=2000、topic）→ 搜索结果页加分类 chips（帖子/版块/用户）；`news` 类型实测 type:null 全空（无效类型值，非登录墙）→ 不接；suggest 5 候选端点全 400 且官方双端 JS 无 suggest 调用 → 无源不做，落地页维持 hotInfo 热词+本地历史（官方同形态）。
- **通道分工**：帖子沿用桌面 HTML 通道（sortby 六排序/topicId 专区筛选只对该通道有效，search2 无对应参数）；版块/用户走 `search2` JSON 分页（hasNextPage）。
- **字段语义**：users `lights`=be_light_count(获亮)、`recNum`=be_recommend_count(推荐)——经两个用户主页 userInfoData 逐字段比对锁定，行 meta 按此标注；非粉丝数（be_follow_count 搜索接口不下发）。
- **点按边界**：用户行不挂 clickable——官方可点进用户主页但加载链路属批X2，行只渲染（欠账：puid→用户主页）；版块行点按→`category/{boardId}`（既有路由）；帖子行 meta 补 `username` 纯文本（作者点按同为 X2 欠账）。
- **热榜**：tag 榜 30 口径复测不变（res=30 条、totalElements=50）；tag 行无回复字段，行形态（rank+名+🔥热度）即官方形态不动；「热帖榜」帖行 meta 对齐官方 `{replies}回复 {lights}亮回复` + 🔥（批U-0c 亮≥50 门控）；热榜区与整页取数失败均给显式空态+重试（原静默/无限 spinner）。

## Alternatives considered

- 帖子改走 `search2?type=posts`（JSON 更稳）：否决——桌面通道 6 种 sortby + topicId 专区筛选无对应参数，迁过去丢功能。
- 搜索联想词：官方双端均无 suggest 接口（5 候选 400），编词违反不编数据原则 → 不做，维持官方同形态（热词+历史）。
- users meta 猜「粉丝」标签：否决——比对 userInfoData 证明 recNum=推荐而非粉丝，按实测语义标注。
- news 分类进 chips：类型值无效（三关键词全空、type:null），加进 chips 等于做空分类 → 如实记缺。
- 子项2 只改热帖榜行：不足——task 明确要求「榜单为空或取数失败时显式空态」，故热榜区+整页均补三态。

## Consequences

- `SearchPage` 新增可选参 `onOpenBoard`（默认 {}），调用方不改也能编；`HupuSearchSlice<T>`/`HupuSearchUser`/`parseUserSearch`/`parseTopicSearchPage`/`searchUsers`/`searchTopicsPage` 均为纯增量，旧签名零改动（`searchTopics`/`parseTopicSearch` 保持 ZonePage 原语义）。
- 分类空态各自显式（没有找到相关帖子/版块/用户）；失败统一走 ErrorRetry。
- 验证：JVM `ZoneSearchParserTest` 新增 users/topic 分页用例；真机渲染待 Muse 验。
- 回退：逐项 revert；`onOpenBoard` 有默认值不影响旧调用。
