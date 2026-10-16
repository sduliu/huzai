# Agent Note: 原生正文投票

Status: implemented

## Problem

3004973 的 cooked 包含静态的 0 投票人，真实数据在 Post.polls 中。通用递归把投票拆成普通列表，选项不可操作且人数错误。

## Decision

实现按数据协议、正文投票组件、详情接线三个小单元分解。按 data-poll-name 匹配 Post.polls，保留正文位置；单选、数字和多选使用网站 PUT /polls/vote（options[]）、DELETE /polls/vote，结果采用确认后的 poll，不乐观修改票数。Post.polls_votes 单独保存当前用户选择；同一 Post 的 busy 互斥，失败保留选择并通过现有事件提示。

结果遵守 always / on_vote / on_close / staff_only、关闭时间及归档权限。受限分组和未实现的排序投票保留网页入口，不伪造结果或提交格式。匿名点击引导登录。普通结果以黄色横条呈现票数和百分比，不加载全部投票人。

多选使用不可变 List<String> 保存当前选择，每次点击替换列表，使 Compose 状态变更可观察；rememberSaveable 保留重建恢复能力，不把可变集合直接作为快照状态。

## Related decisions

检索 poll、投票、正文、Boost：[原生客户端](../../implemented/architecture/2026-10-07-native-community-client.md)和[Boost](../../implemented/feature/2026-10-10-post-boosts-and-length.md)部分重叠，分别保留原生正文边界与写入互斥；没有同主题记录或过时提案。

## Alternatives considered

- 整个投票嵌 WebView：网站功能完整，但正文高度、会话和触摸管理更复杂，普通投票直接复用已确认协议。
- 只解析 cooked 选项：无需模型改动，但人数和选择属于占位数据，不能表达真实投票状态。

## Testing

PostPollsTest 先复现 cooked 被拆散，日志 artifacts/post-poll-repro.txt；修复后 3 项 JVM 与 3 项真机测试通过，日志 post-poll-build.txt、post-poll-device.txt。覆盖票数解析、隐藏结果、单多选范围、投票/撤回请求、失败不修改原 Post、匿名登录、确认结果、关闭状态和深色大字体多选。写入只使用 MockWebServer 和 UI 回调替身，没有向真实社区投票。

不可变选择状态调整后3项投票真机用例再次通过（artifacts/render-final-device.txt）。最终Debug、测试APK及Lint构建通过（render-final-build.txt），消除了新增的可变集合状态警告，保留30项既有警告与1项提示，无错误。

## Consequences

原生正文使用真实票数和可操作选项；投票数据只随详情刷新和操作响应更新，暂不接投票 MessageBus。特殊投票使用网页继续操作；受限分组由网页提供完整权限提示。无需下载全部投票人的头像和资料。
