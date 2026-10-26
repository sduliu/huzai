# 批Z：搜索分类与热榜深层核验（2026-10-27 匿名实测）

## 探源结论（逐项，附证据）

### 搜索结果分类（m.hupu.com/api/v2/search2）

| type | 结论 | 证据 |
|---|---|---|
| posts | 有源 | `type=posts` count=600、20/页、hasNextPage=1；桌面 `bbs.hupu.com/search` `$$data.searchRes` 同数据（type:"posts"） |
| users | 有源 | `type=users` count=2000；id=短puid、username(带高亮font)、header=头像 |
| topic | 有源 | `type=topic`（批L-1 已用于 ZonePage 选择器）；库里→库里专区 id=839 |
| news | **不接** | `type=news` 返回 type:null + count:0 + data:[]（库里/詹姆斯均空），无效类型值；`type=user/post/thread/bbs` 同形态 |
| suggest/联想词 | **无源** | 候选 `search/suggest`、`search2/suggest`、`searchSuggest`、`search/associate`、`search3` 全 400；官方移动搜索页 JS（search-e3dcc40e…js）与桌面 JS（search_b7cf5e90.js）均无 suggest 调用——官方搜索落地页只有 `hotInfo` 热词列表 + 本地历史，本 App 同形态（trending 同源 + HupuPrefs 历史） |

### 关键字段语义（users 类）

`lights`/`recNum` 语义经用户主页 `userInfoData` 逐字段比对锁定：
- user 50082186：搜索 `lights:1240` ↔ `be_light_count=1240` → **获亮**；`recNum:6200` ↔ `be_recommend_count=6200` → **推荐数**（非粉丝；粉丝=be_follow_count=88382 不在搜索接口下发）。
- user 30256516：`lights:10231402`↔`be_light_count`、`recNum:50547`↔`be_recommend_count` 复核一致。

### 热榜（m.hupu.com/hot）

- `pageProps.res` = 30 条 tag 榜（tagName/heat/rank/tagId/icon/competitionType/tagUpdateDesc/generatorType），`totalElements=50`，匿名首屏 30 口径与批F 定案一致、未变。
- tag 行无回复/亮回复字段（tag 不是帖）——行形态（rank+名+🔥热度）即官方形态，不改。
- 「热帖榜」区（板块页 SSR recommendations）是帖行 → meta 对齐官方扁平行：`{replies}回复 {lights}亮回复`，🔥 沿用批U-0c 口径亮回复≥50 才显示。

## 实现

### 子项1 搜索结果页
- `HupuModels`：`HupuSearchUser`（puid/username/header/lights/recNum）+ 泛型 `HupuSearchSlice<T>`（items/page/hasNext——search2 用 hasNextPage 而非 totalPage）。
- `HupuParser`：`parseUserSearch`（剥高亮、id 空跳过、hasNextPage→hasNext）；`parseTopicSearchPage`（原 `parseTopicSearch` 不动，ZonePage 调用零影响）。
- `HupuRepository`：`searchUsers`/`searchTopicsPage`（getMobileJson 通道，失败 null → 页内错误态）。
- `SearchPage`：分类 chips（帖子/版块/用户）入筛选条；帖子沿用桌面通道（sortby/topicId 仅该通道有效，sort+专区仅帖子类显示）；`TopicResults`（logo+名+简介，点按→onOpenBoard→category/{boardId}）、`UserResults`（头像+昵称+获亮/推荐，**不挂 clickable**）；帖子行 meta 补 `username`（模型字段早已解析未渲染）。
- 空态按分类显式：「没有找到相关帖子/版块/用户」；空关键词=历史面板（原有）；error=ErrorRetry（原有）。
- `KudoApp`：SearchPage 新参 `onOpenBoard` → `category/{boardId}`（与热榜热门版块同路由）。

### 子项2 热榜页
- `HotChannelPage`：虎扑热榜区三态化（loading / 显式空态+重试 / 列表）——原 `isNotEmpty()` 静默缺席；整页 boardPage 失败原无限 spinner → 显式失败+重试。
- 「热帖榜」行 meta 对齐官方：`🔥`（亮≥50）+ `{replies}回复  {lights}亮回复`。

## 取舍与欠账

- **用户行点按**：官方行可点进用户主页，但主页加载链路属批X2 范围——本批行不挂 clickable，欠账：`HupuSearchUser.puid` → 用户主页（批X2 合并后接）。
- **帖子不迁 search2**：桌面通道 sortby(6种)/topicId 筛选已在用且工作正常，search2 无对应 sort 参数（postSortList 只在无效 type 响应出现）；两通道并存已注释标注。
- **news 分类不接**：三关键词实测全空（type:null），非登录墙而是无效类型——如实记缺，不出现在 chips。
- **suggest 不做**：官方无此接口，编造联想词违反不编数据原则；落地页热词+本地历史已是官方同形态。

## 回退

逐项 revert 对应 commit；SearchPage 新参 `onOpenBoard` 带默认值，调用方不跟着改也能编。
