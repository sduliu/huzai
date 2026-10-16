# Agent Note: 详情与讨论到底自动续载

Status: implemented

## Problem

主题详情和楼中楼需要点击加载更多，打断连续阅读。

## Decision

以实际可见的列表尾部提示为触发点，主列表和已打开讨论自动获取下一批。加载中、错误、遮挡和深链定位尚未完成时不触发；每个分页状态只触发一次。原先主列表和讨论都等待回复关系补齐，这项限制由[接近顶部预取](../bug-fix/2026-10-11-topic-previous-prefetch.md)部分取代：主时间线定位和双向正文分页不依赖辅助父链，讨论仍保留补齐限制。沿用 ViewModel 的去重、游标和代际隔离，错误只由用户重试，空页或不可访问楼层仍推进流。

[连续楼层窗口](../bug-fix/2026-10-11-topic-jump-window.md)增加主列表顶部的向前续载，跳转预取目标页及相邻两页，之后两个方向仍按可见边界逐页触发。前后分页互斥，前插时按稳定楼层key和可见偏移保持阅读位置，加载/提示保留相同最小高度，避免分页状态切换导致跳动。

[接近顶部预取](../bug-fix/2026-10-11-topic-previous-prefetch.md)进一步提前向前加载：最前面三条正文进入视口就获取前一页，请求期间跟踪最新阅读位置。向后和讨论继续使用尾部提示可见触发。

## Related decisions

检索分页、楼中楼、阅读同步。[楼中楼](../../implemented/feature/2026-10-10-nested-replies.md)部分重叠，本篇替代其中手动续载入口；[阅读同步](../../implemented/bug-fix/2026-10-10-topic-reading-sync.md)继续守住加载不计为阅读。没有完全吸收或过时提案。

## Alternatives considered

- 提前按剩余三行预取可以减少等待，但外层行包含不同长度的讨论摘要，容易在未到列表尾部时过度获取。该取舍继续用于向后及讨论续载；向前阅读现按[接近顶部预取](../bug-fix/2026-10-11-topic-previous-prefetch.md)接受最多一页的提前获取，以避免停在顶部等待。
- 保留加载按钮最容易控制请求，但无法满足连续上拉阅读。

## Testing

TopicContinuationUiTest 的4项真机用例通过（artifacts/topic-continuation-verified-device.txt）：主列表未到底不请求、到底只请求一次、错误停止并手动重试、空页继续推进、新页将尾部推出视口后不继续获取、关闭讨论停止续载。深链与原操作另由 TopicDetailUiTest、NestedReplyUiTest 回归。

首轮 performScrollToNode 在自动加载已移除提示后继续查找提示，两个用例失败（artifacts/topic-flow-device.txt）；改用末尾索引并检查请求次数。下一轮错误卡片重排使重试按钮离开可视区域，点击前补上滚动到按钮（artifacts/topic-flow-final-device.txt）。这两类失败来自测试目标变化，不修改产品分页逻辑。

## Consequences

多个分页全部归入已显示讨论时会继续获取直至填满视口；请求仍逐批串行，失败停止。
