# Agent Note: 分类筛选横向分页

Status: implemented

## Problem

分类筛选只有点击切换，共用列表状态；用户要求支持跟手左右滑动并保留各页位置。

## Decision

分类页使用 HorizontalPager，共用同一分类顶部，点击标签和滑动驱动同一页码。按筛选键保存 LazyListState，当前页使用主状态，其他页使用同账号缓存；只有 settled 当前筛选页可以刷新和加载更多，预组合不得发请求。分类默认最新，账号或 unified_new 改变重建分页；切页保留顶部收起状态与各页位置。

## Related decisions

检索分类、Pager、Feed缓存和滚动位置。[首页分页](../../implemented/feature/2026-10-10-home-pager.md)部分重叠，沿用缓存及账号隔离，不把首页类别目录加进分类分页；[分类顶部](../../implemented/feature/2026-10-10-category-detail-header.md)负责共用顶部和纵向手势；[分类筛选](../../implemented/feature/2026-10-10-category-feed-filters.md)负责许可和接口范围。无完全吸收或过时提案。

## Alternatives considered

- 首页和分类强制合并为一个分页组件：可以减少重复代码，但首页还有目录页和切页展开搜索的决定，分类保持共用顶部；各自小范围组装同一 FeedPage 和 FeedTabBar。
- 横滑完成才替换列表：实现最少，但无法跟手翻页和保留多页阅读位置，使用 Compose Pager 的保存能力。

## Testing

CategoryFeedUiTest 先复现分类没有分页手势区域（category-pager-repro-device.txt）。接入后6项真机用例通过（18.335秒，category-pager-device.txt），覆盖点击与左右滑动同步、跨过中间页返回保留两个列表位置、切页保持收起顶部、登录八页与退出移除个人页，以及原有顶部滚动顺序/惯性/刷新/窄屏测试。接口缓存及权限沿用分类筛选单测。

## Consequences

有限筛选缓存增加内存；列表纵滑、横滑和刷新需共同验收。
