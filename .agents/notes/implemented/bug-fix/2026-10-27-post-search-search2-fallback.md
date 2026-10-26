# Agent Note: 帖子搜索通道增加 search2 JSON 降级源

Status: implemented

## Problem

批 Z 真机验收（vc2040）：搜索「帖子」分类对 lakers/雷霆恒报「加载失败」，同屏版块/用户分类正常。
断点追查分段结论：

- **接线**：批 Z 的 SearchPage diff 未动帖子取数路径（仍 `repo.search` → `fetchSearchHtml`），排除回归。
- **请求**：同出口直连 + Resin 多次探测 `GET /search?sortby=general&page=1&q=lakers` 均 HTTP 200、约 127KB、`window.$$data`/`searchRes` 齐全——端点未死、非登录墙。
- **解析**：探测页 `parseSearchPage` 可正常解析。
- **设备侧**：同机 `fetchHtml` 拉 `/all-gambia`（同 host 同 client）成功落盘，仅 `/search` 路径失败且无缓存文件。`fetchHtml` 三处返回 null（非 2xx / <50KB 无数据载体壳页 / 异常吞没）此前完全无观测手段，真机断点形态无法进一步定位——最可能是出口侧风控挑战页或设备网络对该 URL 的特异性失败。

## Decision

1. `HupuRepository.search` 增加降级链：桌面 `fetchSearchHtml` 取空或 `parseSearchPage` 解不出时，回退到 `m.hupu.com/api/v2/search2?type=posts`（匿名实测 count=500、totalPage=25、hasNextPage=1、20 条/页，与版块/用户分类同一条已验证通道）。
2. 降级仅在 `topicId 为空 && sortby == "general"` 时启用：实测 search2 帖子通道对 `topicId=127`、`sortby/sort/order` 参数一律忽略——带筛选条件降级会以未过滤结果冒充过滤结果，故筛选态仍显式失败。
3. `parsePostSearchJson` 复用 `HupuSearchItem`/`HupuSearchPage`；`addTimeDisplay` 由 `addtime`（unix 秒）合成「x分钟前/x小时前/x天前」，超 30 天回落 `yyyy-MM-dd`。
4. `fetchHtml` 三处失败分支补 `Log.w`（http 码 / 壳页体积 / 异常类名），真机复测时 logcat 可读出真实断点。

## Alternatives considered

- **整体迁移到 search2**：批 Z 已否决——桌面通道独有 6 种 sortby + topicId 专区筛选，search2 实测不支持，迁移=静默丢功能。
- **无条件降级（含筛选态）**：会以默认相关度结果冒充筛选结果，违背「不编数据」原则，否决。
- **只加日志不加降级**：能定位断点但帖子分类在真机仍不可用；降级源字段与官方行结构一一对应，双通道收益大于风险。
- **缓存 search2 结果**：`getMobileJson` 本身无缓存语义，与列表页一致，不额外引入。

## Consequences

- 默认查询（综合排序+所有专区）在桌面通道故障时自动降级到 search2，行结构/meta/分页不变；count 口径变为 search2 的 500（桌面为 600），属官方另一索引的真实数据。
- 用户选了非综合排序或专区筛选且桌面通道故障时，仍显示「加载失败」——显式、诚实、可重试。
- `fetchHtml` 增加 3 处 `Log.w`，语义不变（仍返回 null），全量调用方共用此观测。
