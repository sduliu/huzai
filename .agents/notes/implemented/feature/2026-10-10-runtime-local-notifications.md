# Agent Note: 运行期间接收消息并显示本地通知

Status: implemented

## Problem

应用已有首页 MessageBus 和通知分页/标读接口，但没有运行期间的消息订阅、系统本地通知、消息入口未读数字，以及系统通知点击后的原生落点。用户明确以 App 运行期间接收为范围，不接厂商推送。

## Decision

参考 FluxDO e1bd839 的通知频道与本地通知协议，独立实现 Kotlin 代码。用户消息订阅不依赖是否打开消息页或当前主标签；登录账号变化取消旧订阅、清空旧计数及本地通知。前台运行期间接收，后台进程存活时尽力接收；不增加常驻前台服务或开机自启，进程被杀或强停后停止，回到前台补齐列表与计数。

订阅 /notification/{id} 获取全量未读计数、last_notification 与 recent 已读状态，/notification-alert/{id} 接收论坛提醒，/chat/notification-alert/{id} 接收聊天提醒。复用既有 MessageBus 的共享会话凭据、无Cookie推送客户端、游标、取消和退避约束，不将主站Cookie发到推送域名。首页 FeedUpdates 的可见性及差异检查规则继续成立。

初始未读数来自当前会话字段，实时计数优先，迟到的同账号旧会话值不能覆盖实时结果。数字角标使用 LogoYellow 背景和 Ink 文字，0时隐藏，超过99显示99+，不以已加载一页的数量代替后台总数。系统本地通知使用Android通知渠道与Android13+运行时权限；未授权时应用内消息与角标继续工作。

首次打开只建立历史未读基线，不批量弹出旧通知。提醒按账号与稳定消息身份/频道游标去重，重连不重复弹。只在明确打开消息或全部已读操作时提交标读；收到通知、查看列表或从系统通知栏划掉通知不等于已读。后台确认后更新本地已读、计数并取消对应系统通知；失败保留未读并提供重试，跨账号回包不能改当前状态。网页端标读通过通知频道同步，后台拉取使用普通分页，不使用有last_seen副作用的recent请求。

点击系统通知依次尝试帖子具体楼层、私信或聊天对应目标，无法定位时回消息页，再无法导航则打开App。冷启动等待导航与会话准备，热启动同样处理且每次只消费一次；账号不匹配时不向另一账号泄露或标读旧消息。可定位目标加载失败仍提供返回消息页的入口。

## Reference and existing note audit

只读核对 FluxDO 的 [notification_providers.dart](https://github.com/Lingyan000/fluxdo/blob/e1bd839/lib/providers/message_bus/notification_providers.dart)、[chat_notification_alert_provider.dart](https://github.com/Lingyan000/fluxdo/blob/e1bd839/lib/providers/chat/chat_notification_alert_provider.dart)、[local_notification_service.dart](https://github.com/Lingyan000/fluxdo/blob/e1bd839/lib/services/local_notification_service.dart)、[通知接口](https://github.com/Lingyan000/fluxdo/blob/e1bd839/lib/services/discourse/_notifications.dart)及独立后台保活服务；源码仅作协议参考，位于忽略的 artifacts/fluxdo-notification-reference/。

已检索通知、MessageBus、角标、后台和FluxDO笔记。[首页实时提示](../../implemented/feature/2026-10-08-home-live-refresh.md)部分重叠，继续约束传输与首页差异检查，本篇新增用户消息作用域；[个人中心和消息分组](../../implemented/feature/2026-10-10-member-account-dashboard.md)保留分页分组、私信/聊天区分及明确点击才标读；[解决通知](../../implemented/bug-fix/2026-10-10-solved-notifications.md)保留类型识别；[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)保留会话与草稿隔离。没有完全吸收或过时提案。

## Alternatives considered

- 定时拉全部通知实现简单，但持续空请求且与实时计数容易竞态；使用站点实际通知频道，必要时合并拉取补齐。
- 完整沿用FluxDO的Android前台服务可以提高后台连接持续性，但会产生常驻运行通知，超出当前按运行期间接收的默认方案，待用户明确选择再扩大范围。
- 只统计当前30条未读无需扩展会话字段，但会低估实际总数，采用后台计数。
- 系统通知点击只打开App最稳妥，但会丢失消息上下文，作为最后回退而非默认落点。

## Implementation

AccountViewModel在账号作用域持续收集NotificationUpdates，页面与前后台生命周期不启动重复订阅。通知和计数通过NotificationLedger保留版本，首批历史不提醒；旧会话不回刷实时值，分页已读只接受请求之后更晚的实时状态。后台明确标读成功才修改本地和系统通知。无ID的提醒通过已加载列表或最多三页普通分页关联真实通知ID，不能用帖子ID猜通知ID；关联不到仍打开目标内容，不执行扩大范围的标读。

主导航和消息标题显示黄底黑字角标，三位以上显示99+。MainActivity在冷/热启动接收私有token，IncomingNotification等会话与导航准备后消费一次；先建立消息页返回路径，再定位帖子楼层或聊天消息，账号不匹配直接回消息页且不标读。聊天按锚点读取前后消息并定位，只在页面前台且消息实际可见700ms后单调上报频道已读位置；失败显示错误，用户刷新可重试。

系统平台实现见[本地通知投递](2026-10-11-local-notification-delivery.md)，协议与安全边界见[通知频道](../architecture/2026-10-10-notification-stream.md)。设置提供通知管理入口，Android13+登录后仅首次主动请求权限，拒绝不影响应用内消息。

## Testing

- NotificationProtocolTest与MessageBusTest通过，保留跨域无Cookie、游标、取消、限流和重连回归，日志artifacts/notification-protocol-tests.txt。
- NotificationLedgerTest、RuntimeNotificationsTest、AccountViewModelTest和AccountMessagesTest共14项JVM通过，覆盖初始化抑制、去重、换号、计数优先级、确认前失败保持未读、并发已读和聊天读回执。日志artifacts/notification-read-fixed.txt。
- 新分页被旧实时未读覆盖先在artifacts/notification-read-race-repro.txt失败；按每条阅读事实的请求版本修复后通过，同时覆盖旧分页不能覆盖较新标读。
- 真机LocalNotificationsTest和NotificationUiTest共4项最终通过，覆盖实际系统投递、权限拒绝、私有目标冷恢复、可访问角标唯一与黄底黑字、0/99+、冷会话等待、帖子楼层/聊天消息/换号/无目标回退及单次消费。日志artifacts/runtime-notification-final-device.txt。
- ChatReadUiTest真机通过：定位第10条后仅标可见10至20范围，没有直接标读已加载的第50条。个人中心5项新增/更新回归同批通过，日志artifacts/runtime-header-chat-device.txt。
- 设置面板7项与底栏触摸2项回归通过，和新增通知4项合计13项，日志artifacts/runtime-notification-device-fixed.txt；个人中心菜单4项与按钮设置2项此前相关真机回归通过。
- 真机完整个人中心展开与吸顶截图、等级同行、完整角标和独立主页箭头已查看；按钮切搜索后确认进入“搜索社区”，随后恢复默认发帖。artifacts/runtime-member-square.png、runtime-member-compact.png、runtime-search-action.png及runtime-native-search-layout.json保留证据。没有为测试发送真实私信或标读真实社区通知；真实新消息抵达尚无端到端样本。

- 最终Debug、测试APK与Lint通过，0 errors、37 warnings、1 hint，日志artifacts/runtime-delivery-build.txt与runtime-final-lint.txt；verify-notes通过。覆盖安装保留账号和草稿，构建、artifacts/kudo-runtime-update.apk和设备安装包SHA256一致：1DB249CE079EEF34D8EF7605B4C1B1474DE692055E01E521EA957064D7B6C800，记录artifacts/runtime-apk-verification.txt。默认navigation_action已恢复COMPOSE。

## Consequences

后台进程存活不代表Android一定允许持续网络，休眠或系统回收会延迟或停止接收；没有厂商通道和常驻服务时不承诺离线到达。通知提醒payload可能缺少通知ID，需要保留频道游标并与通知列表关联，不能猜ID向后台标读。系统桌面图标角标由启动器控制，黄色黑字要求应用内入口实现。通知权限和频道设置由系统管理。
