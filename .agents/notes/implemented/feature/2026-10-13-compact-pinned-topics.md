# Agent Note: 置顶帖精简显示

Status: implemented

## Problem

置顶帖沿用完整列表卡片时，作者、摘要与统计占用首页空间。用户要求参考 FluxDO 的单行布局，并允许在显示设置中恢复当前列表样式。

## Decision

显示偏好“置顶贴精简显示”通过 compact_pinned_topics 持久化，默认开启且尊重已保存选择。TopicCard 与 TopicTitleRow 共享 CompactPinnedTopicRow：置顶图标、分类图标、单行省略标题，以及按真实 unseen / unreadPosts 显示的蓝点和未读数量。两个未读条件独立成立时可以同时显示。仅响应接口 pinned=true，不从排序位置或标题猜测置顶；不增加网络读取。

关闭开关时，两种列表组件继续使用现有完整布局，并跟随“主题列表样式”。首页、分类、标签、搜索与个人话题的复用入口保持一致；精简行整行点击进入主题。

列表通过 LocalDisplaySettings 立即响应偏好变更，复用现有分类目录和默认图标。未知目录不编造分类名称；目录到达后更新实际图标及可访问描述。

## Existing note audit

- [主题展示](../../implemented/feature/2026-10-09-topic-presentation.md)：部分重叠，沿用分类元数据与两种列表样式。
- [移除列表菜单](../../implemented/simplification/2026-10-09-remove-list-actions.md)：部分重叠，继续不提供长按菜单或额外主帖请求。
- [登录态筛选](../../implemented/feature/2026-10-08-session-feed-filters.md)：保留服务端列表与账号边界，不改排序、分页及未读定义。
- 没有同主题旧提案或完全被取代的笔记。

## Alternatives considered

- 仅首页替换置顶项可以把变化限定在首页，但同一偏好在分类等列表会表现不一致；采用现有共用列表组件实现。
- 通过缩小完整卡片能保留作者和摘要，但无法达到用户要求的单行信息顺序；精简模式单独渲染，关闭后回到原布局。

## Verification

CompactPinnedTopicsUiTest 验证默认开启与持久化、已有显式设置保留、两种列表切换、普通帖子保持原样、未读标记独立隐藏、窄屏深色130%字号的一行省略与图标顺序、整行进入正确主题以及分类目录迟到更新。完整列表的菜单和作者行为仍由 TopicActionsUiTest 验证。

2026-10-13：本功能4项、原列表样式4项、完整列表操作1项及签名偏好1项共10项真机用例通过（artifacts/compact-signature-device.txt，14.309秒）。已查看离线 compact-pinned-feed-light.png 和 compact-pinned-narrow-dark.png，以及真实公开首页 compact-pinned-live.png；真实三条置顶均为单行，普通帖保留头像、摘要和统计。Debug、AndroidTest构建、7项关联JVM及Lint通过。覆盖前核对为0.2.2-dev / 2005，APK签名与原安装一致，全程install -r，未卸载或清除数据。

## Consequences

超长标题会省略，进入话题可查看全文。分类元数据尚未加载时先使用现有默认分类图标；元数据更新后自动恢复实际图标。真机验证使用同签名覆盖安装，保留账号数据。
