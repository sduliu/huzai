# Agent Note: 个人主页顶部与分页列表连续滚动

Status: implemented

## Problem

从个人主页分页列表快速上滑，抬手后的惯性不经过顶部，标签停在半路，后续列表内容开始滚动。

## Decision

纵向上滑和惯性先消耗顶部可收起距离，再交给列表；下拉先使当前列表回顶，再用剩余距离展开顶部。NestedScrollConnection 同时接收 UserInput 和 SideEffect，不过滤惯性事件。HorizontalPager 1.11.1 默认只消费主轴的惯性，保留其方向协调，不添加多余手势拦截。顶部直接拖动与列表使用同一个偏移，尺寸变化后偏移保持在有效范围内；切换用户时重建连接，避免继续引用旧用户的偏移状态。

## Related decisions

检索 PublicProfile、个人主页、折叠、nestedScroll。与[完整公开主页](../feature/2026-10-10-public-user-profile.md)部分重叠，本篇接管顶部连续滚动，其数据、独立标签缓存、背景来源与权限继续适用。[首页顶部](../feature/2026-10-08-home-tabs-and-collapse.md)反向轻滑立即展开的规则只属于首页，不用于个人主页。无完全吸收或过时提案。

## Alternatives considered

- 把所有内容放进同一个 LazyColumn 并固定标签：自然获得单一纵向滚动，但会破坏分页各自的阅读位置和可交互高度，不能直接复用现有各页惰性列表。
- 在分页外拦截所有 pointerInput：可控制每次拖动，但需自行处理方向锁定、惯性和取消，会与左右切页争抢；采用 Compose 嵌套滚动和现成 scrollable。

## Testing

PublicProfileUiTest 在 RMX2202 / Android 13 先复现快速上滑后标签顶部仍在1112px（artifacts/profile-scroll-repro-device.txt）。短列表下拉原先已通过，保留为回归。修复后3项真机测试全部通过（12.06秒，profile-scroll-fixed-device.txt），包含惯性收起、短页展开以及40条长列表先收顶部、回顶后再展开。Debug和测试APK构建通过。

## Consequences

The pinned toolbar keeps the avatar, display name, and follow action in stable slots while their alpha follows the header collapse fraction. This makes the identity appear progressively during the pin transition instead of popping in only at the final offset.

页面的纵向位移有统一消费顺序，左右切页仍由 Pager 负责；代价是顶部依赖 Compose 嵌套滚动的 SideEffect 语义，升级 Foundation 时需保留长短页和惯性用例。真机继续用原 Android Debug 证书覆盖安装，保留账号和数据。
