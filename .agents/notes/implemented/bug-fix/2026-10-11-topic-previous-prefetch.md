# Agent Note: 接近顶部自动预取前页

Status: implemented

## Problem

向前分页会被过期回调中的初始loading状态挡住，顶部提示可见也没有请求或loading。主时间线等待辅助回复父链、跳转动画被手势打断后的定位门控，也会阻止预取。提前预取后，固定请求发起时的视口会把继续滑动的读者拉回旧位置；前插恢复被拖动取消，或整页不可访问而首条正文key不变时，残留锚点又会锁住后续分页。

## Decision

当前窗口最前面三条正文或顶部前页提示进入视口时自动预取前一页，不要求滚到列表顶端。提示一次进入视口只触发一次，避免只有标题和提示可见时连续补齐远跳间隔。主时间线定位和双向分页不等待辅助回复父链，讨论预览仍使用自己的补齐约束。跳转尚未完成、遮挡、已有分页和错误时仍暂停，失败由用户重试，每次最多20条，不补齐整个远跳间隔。

前页请求期间持续记录可见正文key与偏移，到达新页后保持最新阅读位置。在锚点恢复完成前暂停双向自动分页，防止新页刚前插、列表尚未恢复时误把新边界当成读者正在靠近的边界而额外请求。前页失败、刷新、跳转或向后请求抢先开始时清除待恢复锚点，避免已终止或未接受的前页请求阻塞后续分页；成功返回时仍保留到前插恢复结束。维持加载/提示相同高度，避免提前加载造成视口跳回。跳转继续清空过期视口锚点。

跳转滚动被用户手势打断时，如果当前定位协程本身仍有效，则结束定位门控，后续阅读和分页跟随实际可见楼层；若协程因换目标或离开页面取消则保持取消，不用旧目标解除新定位的约束。

前插后若稳定key已经保持原正文偏移，则直接结束恢复；否则使用 requestScrollToItem 在下一次测量恢复锚点，并等待新布局后才放开分页。该请求不会被正在进行的拖动拒绝；只有位置确实变化时才取消滚动以保持原正文，避免标题/提示仍为首个可见item时新正文挤走原楼层。恢复观察前页加载完成与stream变化，即使这一页全部不可访问、首条正文key没变，也会结束恢复并继续读取下一段可用楼层。

自动加载的前页委托使用普通 lambda 调用本地 loadPreviousPage，不能把捕获页面状态的本地函数引用直接传给 rememberUpdatedState。Kotlin 函数引用的相等性不包含其局部捕获值，Compose 的结构相等策略可能保留首次 loading=true 的回调，导致真实网络加载完成后仍被旧加载守卫拦截。回归必须经过网络、ViewModel 的初始加载和 StateFlow 收集，不能只用已加载的静态 TopicState 验证回调计数。

## Related decisions

检索上一页、分页、连续加载及回复关系：[自动续载](../feature/2026-10-10-topic-continuation.md)部分重叠，本篇替代向前必须提示可见及主列表等待父链的约定，保留向后/讨论尾部触发、互斥、错误和遮挡规则。[连续窗口](2026-10-11-topic-jump-window.md)部分重叠，保留三页跳转及stream ID分页，更新前页触发时机和视口锚点。无完全吸收或过时提案。

## Alternatives considered

- 继续等顶部提示可见：能精确控制获取时机，且不受长短摘要影响；实际阅读会碰到加载停顿，又可能被辅助父链长期阻塞，采用最前面三条正文的有限预取。
- 保留请求发起时的固定锚点：逻辑简单，也能保持静止页面的位置；提前预取时用户仍在滑动，需要保持到包时最新可见正文，避免回跳。
- 继续传本地函数引用给 rememberUpdatedState：调用处简洁且看起来可以自动获取最新回调，但引用的结构相等性忽略捕获状态，使用随捕获值更新的 lambda 委托。
- 捕获前插 scrollToItem 的手势取消后直接放开分页：可以让拖动继续且避免残留锚点，但标题/提示为首个可见item时无法保持原正文位置，还会把新边界当成读者主动接近而连续预取；仅在偏移确实变化时请求下一次测量恢复位置。

## Testing

三项新增真机用例先失败，分别复现距顶部两条时不预取、辅助父链阻塞正文定位、请求期间滑动后回到旧楼层（artifacts/previous-prefetch-repro-device-20261011.txt）。修复后21项楼层索引、双向续载及实际阅读用例全部通过（artifacts/previous-prefetch-fixed-device-20261011.txt），确认首条98楼未显示、100楼可见时已请求前页，新页到来后100楼像素位置不变且不继续获取下一页；加载期间继续滑到104楼，104楼仍保持原位置。辅助父链仍在加载时也能定位108楼并请求前页。

Debug/测试包构建、Debug Lint通过（artifacts/previous-prefetch-fixed-build-20261011.txt）。已查看 artifacts/floor-early-previous-prefetch.png，前插后当前100楼不变、顶部提示不在视口。签名核对一致后仅以 install -r 覆盖安装，保留已安装开发版的数据。

真实前页链路使用 TopicBackwardPaginationUiTest、OkHttp拦截器、真实 TopicViewModel 与 StateFlow：帖子ID与楼层号不同，从最初20楼跳到1520楼，再前翻1490楼边界。6项专项测试通过（artifacts/backward-loading-restoration-device-20261011.txt），覆盖请求与可见loading、原楼层像素位置不变、只有顶部提示可见、用户打断跳转动画、拖动中返回数据、整页不可访问、失败后向后翻页与手动重试。拖动用例额外检查前插不误触下一页。

对应的失败证据：初始4项完整链路用例均失败（artifacts/backward-loading-repro-device-20261011.txt）；前页503后向后翻页被残留锚点阻塞（artifacts/backward-loading-boundary-device-20261011.txt）；拖动中返回前页后第二页不触发（artifacts/backward-loading-drag-repro-device-20261011.txt）；整页不可访问后恢复协程未重启（artifacts/backward-loading-empty-repro-device-20261011.txt）。静态状态回调测试不能替代初始loading变化和拖动中到包的验证。

楼层索引、双向续载与实际阅读21项关联回归全部通过（artifacts/backward-loading-restoration-regression-device-20261011.txt）；与6项专项测试合计27项通过。

首页置顶帖「社区官方 iOS App 已上架 App Store」从前几楼跳500楼、连续向前滑动，修复前能补一页但停在454楼边界，提示可见、继续下拉仍不触发（artifacts/backward-live-pinned-boundary.png、backward-live-pinned-stalled.json）。修复后相同真机、正常网络从500楼连续前翻到416楼，六批滑动依次到485、469、461、446、430、416楼，跨过原454楼卡点和多个前页边界（artifacts/backward-live-fixed-500.json、backward-live-fixed-scroll-1.json至backward-live-fixed-scroll-6.json）。已查看 artifacts/backward-live-fixed-416.png，正文楼层与底部索引正常，未停在加载提示。

最新Debug/测试包构建及Debug Lint通过（artifacts/backward-loading-restoration-build-20261011.txt、backward-loading-restoration-lint-20261011.txt）。已查看 artifacts/backward-pagination-loading.png，1490楼上方黄色loading可见。Debug签名与原安装开发版一致，仅以 install -r 覆盖安装。

## Consequences

向前阅读在抵达窗口边界前获取相邻页，辅助父链不再造成正文加载停顿。长正文会让三条预取覆盖更多阅读距离，但每次只取一页；错误仍停止自动重试。标题或提示为首个可见item时，必要的锚点恢复会结束当次滚动以保持原正文，稳定key已经保持位置时继续跟随用户手势。前插恢复与跳转需要继续按代次隔离，不能用列表索引代表真实楼层。
