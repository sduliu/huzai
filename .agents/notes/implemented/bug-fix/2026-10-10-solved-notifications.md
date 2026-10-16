# Agent Note: 问题解决通知使用真实标题与状态

Status: implemented

## Problem

真实通知 notification_type 为14，即 custom，并不是独立 solved 类型。data.message 是 solved.topic_solved_notification，data.title 是翻译键 solved.notification.topic_solved_title，真实主题名称位于 data.topic_title 和顶层 fancy_title。当前客户端把翻译键显示为标题，并回退到“有新的动态”，也没有状态图标。

## Decision

仅对 custom 类型且专用 message 匹配的解决通知设置 isSolved。该通知显示真实 topic_title，缺失时使用 fancy_title，再缺失时显示“话题已解决”；描述说明操作者将话题标记为已解决。保留真实 topic_id、post_number 和已读状态，头像右下角补蓝色勾选，与右上角未读黄点独立显示。

不把所有14号通知都当作解决通知，也不把未知插件翻译键交给客户端猜测翻译。普通回复、赞、徽章和其他自定义通知沿用现有解析及分组。

## Existing note audit

- [个人中心与消息分组](../../implemented/feature/2026-10-10-member-account-dashboard.md)：部分重叠，补齐通知内容和状态图标，不改变完整分页、四组筛选和点击才标记已读。
- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，保留服务器通知类型与导航数据，专用事件显式识别。
- [引用分组](../../implemented/bug-fix/2026-10-10-discourse-quotes.md)：同一轮报告的另一缺陷，机制无关，独立提交。
- 未发现同主题旧方案，无完全吸收或过时提案。

## Alternatives considered

- 把14号统一映射为“话题已解决”：实现最短，但14号是通用插件类型，会误标其他插件事件，必须结合 message。
- 仅替换已知翻译键的中文：能消除裸键，但仍丢失实际帖子标题和状态语义，模型保留解决状态并使用主题标题。

## Verification

使用真实JSON字段的离线夹具先复现标题错误（artifacts/solved-notice-repro.txt）。修复后全部172项JVM单测通过（artifacts/quote-solved-final-build.txt）；后续引用折叠Saver调整及测试夹具修正后，引用和通知12项定向单测再通过（artifacts/quote-solved-checks.txt）。覆盖真实标题、HTML解码、缺失标题回退、其他14号事件、错误类型、原始楼层和已读状态。

最终11项真机测试通过（artifacts/quote-solved-final-device.txt），涵盖解决通知蓝色勾选、未读黄点、点击进入3003776的34楼、点击前零写请求、点击后只标记一次、已读后保留解决图标，以及消息分组、访客恢复、引用折叠状态和动图签名。与此前28项正文/相册/SVG回归合计39项不同用例。所有写请求通过离线客户端拦截；真实账号接口核查仅GET，不点击真实通知。solved-notice-fixed.png已人工查看，使用结构与真实返回一致的离线夹具。

首轮新增点击测试把CSRF夹具地址写成/session/csrf.json，真实客户端请求/session/csrf时中断；修正夹具后完整11项通过，没有放宽产品断言（失败记录artifacts/solved-notice-fixture-endpoint.txt）。最终Debug与AndroidTest构建通过；Lint零错误，30项既有警告及1项提示，本次新增状态集合警告已经修正。

## Consequences

解决通知显示真实主题和可访问的状态标识，与未读状态独立；其他custom事件保持原有语义。代价是显式维护已核对的插件事件契约，插件未来改变message时退回普通通知，避免猜测带来错误状态。真正没有标题数据时显示状态文案，不为补齐标题额外读取主题。
