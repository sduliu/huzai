# Agent Note: Boost 与回复提示实际对象

Status: implemented

## Problem

输入面板的通用提示不能辨认正在 Boost 或回复谁。

## Decision

Boost 提示使用目标帖作者 username，显示 `boost <用户名>`；回复主题使用主题作者 displayName，显示 `回复 <昵称>`，回复指定楼层使用该楼层作者 displayName。昵称空时沿用 username 回退。提示只作为展示上下文，不改变发送目标、输入内容或草稿键。无法取得目标时保留不含错误名字的简短提示。

TopicScreen 从 boostPostId 查找实际帖作者并传给 BoostForm。KudoApp 从 replyTarget 在 allPosts（含缓存回复和原帖）中查找楼层作者，主题回复使用 authorProfile 或 detail.author，再作为可选展示参数传入 EditorScreen/EditorForm；不读取登录用户作为收件对象。正文开始输入后沿用 CommunityTextField 的占位隐藏逻辑。

## Existing note audit

已检索 Boost、输入提示、表单与回复时间线。[帖子Boost](../../implemented/feature/2026-10-10-post-boosts-and-length.md)的发送目标不变；[酷安表单](../../implemented/feature/2026-10-10-coolapk-forms.md)保留编辑器承载与草稿；[回复时间线](../../implemented/feature/2026-10-10-reply-timeline-and-preview.md)保留真实楼层目标。[两行折叠](../../implemented/feature/2026-10-10-boost-two-row-folding.md)独立处理短条空间，分开提交。无过时提案。

## Alternatives considered

- 提示一律用主题作者不会新增对象传递，但 Boost 和指定楼层回复会误指他人，提示随实际目标作者变化。
- 将提示写入草稿可减少展示参数，但会污染输入及改变已存在的草稿身份，使用独立展示参数。

## Verification

Boost 主帖与指定回复原实现的2项复现测试均因找不到目标用户名失败（boost-hint-repro-device.txt）；回复昵称复现也先失败（reply-hint-repro-device.txt）。最终16项真机测试通过（boost-target-final-device.txt），覆盖主帖/楼层Boost用户名、昵称与username不同、回复昵称切换/空昵称回退、输入后提示隐藏、草稿保留、输入规则与确认发送目标、键盘拖动关闭、正文末尾阅读与遮挡暂停。构建和Lint通过（boost-target-final-build.txt），Lint 0错误、31条现有警告和1条提示。

## Consequences

用户可从空白输入识别对象，展示参数不进入草稿或发送协议。加载状态或从草稿列表独立恢复编辑器时可能没有目标资料，此时显示“回复”或“boost”；不为取名字增加网络请求。真机测试使用离线夹具和模拟回调，没有向社区发布真实内容。
