# Agent Note: MessageBus用户通知协议

Status: implemented

## Problem

首页的MessageBus只发出列表变化信号，无法传递通知计数、已读状态和消息落点。另写传输容易绕过跨域凭据约束。

## Decision

Background MessageBus polling treats one Cloudflare challenge as transient: it backs off and retries silently, and only exposes the manual-verification event after two consecutive challenges. A successful response resets the consecutive-failure count, so a short-lived edge challenge does not interrupt an otherwise usable session.

DiscourseMessageBus共享同一长轮询实现，FeedUpdates与NotificationUpdates各自拥有频道游标和取消作用域。通知订阅使用会话notification_channel_position，并订阅论坛及聊天提醒；NotificationCursors在游标之后解析计数、last_notification、recent与目标字段，忽略其他账号频道和重复游标，接受服务端重置头。首次成功轮询标记initial，重连保留游标并标记reconnect，供账号层建立历史基线和补齐状态。

推送客户端仍无Cookie、不重定向、不共享主站拦截器，凭据只来自同源HTML的共享会话键。会话解析增加后台总未读和通知频道位置，Notice保留聊天频道与消息ID；缺失字段兼容旧接口和夹具。当前传输契约不改变首页可见性控制。

## Related decisions

检索MessageBus、通知与会话。与[首页更新](../feature/2026-10-08-home-live-refresh.md)部分重叠并保留安全、退避、取消边界；与[运行通知方案](../../implemented/feature/2026-10-10-runtime-local-notifications.md)部分重叠，本篇承担传输协议，系统展示及账号应用层由后者记录。没有完全吸收或过时提案。

## Alternatives considered

- 独立复制首页传输能避免改动旧入口，但凭据、限流和取消规则会分叉；抽出共同轮询并保留两种业务接口。
- 在首页Unit信号后拉消息最少新增接口，但无法保留游标身份、实时recent已读和聊天落点；使用结构化批次。

## Consequences

通知协议可独立离线测试，不使用recent查询的last_seen副作用。首页与消息拥有各自长轮询，后台只保持消息订阅；未来如需减少连接数可合并传输，但不能混用业务可见性。

## Testing

MessageBusTest covers transient recovery and the two-failure threshold; DiscourseClientTest covers HTML-only challenge markers and challenge-like text inside JSON.

NotificationProtocolTest覆盖会话总数、三个频道、游标去重和重置、recent标读、聊天目标及跨域无Cookie。MessageBusTest保持原重连、取消与限流回归，执行日志artifacts/notification-protocol-tests.txt。
