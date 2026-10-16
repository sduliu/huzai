# Agent Note: 跳转后的连续楼层窗口

Status: implemented

## Problem

从2楼的讨论面板跳到已预取的108楼时，预览缓存被单独追加到1至20楼后，形成20接108的断层。未缓存跳转也会把互不相邻的窗口直接拼接，且只有向后分页。

## Decision

主列表展示一个服务端流中的连续窗口。只有目标已在主列表中才直接定位；预览缓存命中仍读取目标附近窗口，并按 stream ID 并行读取前后各最多20条，准备目标页及相邻两页，复用最新互动状态。旧主列表保留为辅助缓存，首楼保留 pinnedOriginal。之后在窗口首尾按需逐页加载，像 Web 版一样边翻边补齐中间内容，不逐页下载整个间隔。邻页失败不阻止可用目标页定位，保留对应方向的重试；删除或无权限的编号保持真实空洞。跳转取消旧分页，代次阻止迟到结果插回新窗口。

[接近顶部预取](2026-10-11-topic-previous-prefetch.md)部分更新续载入口和视口保持方式：最前面三条正文进入视口即预取前页，不等顶部提示或辅助父链；请求期间跟踪最新可见正文，避免读者继续滑动后被请求发起时的固定锚点拉回。三页跳转和stream ID窗口契约继续适用。

## Related decisions

检索楼层、连续、跳转、预览和分页：[楼层导航](../feature/2026-10-10-topic-floor-navigation.md)与[原楼层预览](../feature/2026-10-10-reply-timeline-and-preview.md)部分重叠，本篇替代缓存命中无需窗口请求及互不相邻窗口拼接的约定，保留定位、错误和阅读规则。[自动续载](../feature/2026-10-10-topic-continuation.md)部分重叠，增加向前续载；无完全吸收或过时提案。

## Alternatives considered

- 一次补齐20至108之间全部楼层：实现连续列表且保留旧位置，但远距离跳转请求量随间隔增长，不能快速定位；选择连续窗口及按需双向分页。
- 只在断层处增加提示：最少改动且能提醒尚未加载，但向前阅读仍断开；不满足继续阅读邻近楼层的要求。

## Testing

TopicFloorNavigationTest 先复现缓存108楼不读取窗口及缺相邻页（artifacts/floor-window-repro-20261011.txt、artifacts/floor-three-pages-repro-20261011.txt）。修复后目标页98至117楼，并行预取78至97及118至137，原2楼仍在缓存，向前逐页加载58至77，向后逐页加载138至157。覆盖辅助预取失败不阻止跳转、前页重试和删除ID推进、请求互斥/过期跳转、stream ID与楼层号码不同。窗口合并继续复用最新确认互动状态及阅读状态。

相关35项JVM用例、Debug及AndroidTest构建、Debug Lint通过（artifacts/floor-three-pages-final-build-20261011.txt）；补充单页失败重试及非连续楼层号检查后12项楼层导航JVM用例通过，共37个不同相关单元用例（artifacts/floor-three-pages-extra-unit-20261011.txt）。17项楼层导航、主列表/讨论续载、原楼层预览及楼中楼真机用例通过（artifacts/floor-three-pages-device-20261011.txt）。前插分页检查原楼层像素位置不变，97/98相邻，截图 artifacts/floor-backward-continuous.png。主包和测试包仅通过 install -r 覆盖安装，未卸载或清除账号数据。

## Consequences

远跳网络请求量不随间隔增长，目标窗口请求后最多并行预取两页。后续边界分页串行，失败由用户重试；真实删除或无权限的楼层仍可能有编号空洞。原楼层状态需在缓存/主列表间一致，前插分页使用稳定key及可见楼层偏移保持视口；真正删除的楼层不能伪造为连续编号。
