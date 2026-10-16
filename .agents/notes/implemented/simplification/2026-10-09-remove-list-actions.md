# Agent Note: 移除主题列表操作菜单

Status: implemented

## Problem

列表打开操作菜单时，需要额外读取主帖才能确定收藏状态、举报权限和编辑历史权限。用户提供实际加载截图，明确取消列表的这组功能，避免阅读列表时出现等待中的菜单。

## Decision

图片预览入口由[封面进入详情](2026-10-10-feed-image-opens-topic.md)进一步收窄，列表图片统一打开主题；本篇取消三点和长按菜单的决定仍有效。

信息流 TopicCard 和传统 TopicTitleRow 删除三点按钮及长按菜单，只保留进入主题、打开作者和图片预览。首页、分类和搜索复用这两个组件，因此同时生效；置顶标记保留。

详情页继续使用已加载主帖打开复制、收藏、按需编辑历史和举报菜单。公共操作状态与网站忽略用户契约保留，设置中的已有忽略用户恢复入口仍然有效。

## Related decisions

检索活跃笔记中的菜单、举报、历史、收藏、首页与列表关键词。[主题操作](../../implemented/feature/2026-10-09-topic-actions.md)部分重叠，本篇只替代列表入口和其按需加载决定，保留原来的最强理由及详情、举报、历史契约。[网站忽略作者](../../implemented/feature/2026-10-09-ignore-author.md)部分重叠，保留隐藏与恢复契约。[主题展示](../../implemented/feature/2026-10-09-topic-presentation.md)部分重叠，只移除操作按钮，分类、标签和置顶继续显示。其余首页布局与头像笔记不改变，没有完全吸收或过时提案。

## Alternatives considered

- 保留打开时按需读取主帖：不预取全部列表项，能获取真实操作状态，也限制额外请求数量；但菜单仍有等待过程，用户明确取消列表操作，因此移除入口。
- 每条列表预取主帖：打开菜单可直接显示真实状态；代价是为未打开的主题产生请求，而且用户需要取消功能，预取不能满足这次范围。

## Testing

RMX2202 / Android13 的23项真机回归通过（32.384秒，artifacts/remove-list-actions-device.txt）：TopicActionsUiTest 8项、TopicListStyleUiTest 3项、TopicDetailUiTest 5项、TopicPresentationUiTest 7项。新用例覆盖两种列表、普通和置顶主题无三点及长按语义、实际长按不调用菜单、主题点击和头像回调保留；详情复制、收藏同步及历史权限继续通过。

已查看 artifacts/remove-list-actions-ui/ 的 topic-list-no-actions-light.png、topic-list-no-actions-dark-large.png、topic-actions-detail-dark-large.png，确认无列表三点，置顶保留，深色130%文字和详情操作可读。夹具不包含真实网络图片，图片预览实现未修改。

实际公开首页截图 artifacts/remove-list-actions-real-home.png 已查看：真实 Neo 置顶卡片仅保留置顶标记，三点消失，远程头像、封面、分类和标签继续显示；验收后应用停留在首页。

Debug、AndroidTest 构建和 Lint 通过（0错误、30警告、1提示，artifacts/remove-list-actions-build.txt）。本次没有数据逻辑改动，使用界面回归，不重复运行此前的123项JVM测试。APK 使用原安装证书覆盖安装，未清理数据或修改签名配置；分发包 artifacts/kudo-no-list-actions.apk 的 SHA256 为 0b708b9b9ddc9a1b77c0bf5ce6ab35c584b450d2edbdb329fdb5360db34b76ff。

## Consequences

列表不能直接收藏或举报，需要进入详情。列表不再提供新建忽略用户入口，已有忽略集合和设置恢复功能继续有效。公共菜单仍支持原有内部模式，但实际列表组件不再连接操作控制器；以后恢复入口需要重新评审加载体验。
