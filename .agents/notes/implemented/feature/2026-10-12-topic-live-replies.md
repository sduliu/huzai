# Agent Note: 话题新回复长轮询与增量续载

Status: implemented

## Problem

话题详情的楼层目录只来自进入或主动刷新时的快照。即使阅读到末尾，其他用户随后发布的回复也无法自动出现。直接整页刷新会重置已加载的分页和阅读现场。

## Decision

复用 DiscourseMessageBus 的独立无 Cookie 传输，订阅当前话题的 `/topic/{id}` 频道。新建回复、服务器要求刷新流以及游标失效触发检查；游标对所有目标频道消息推进，重复和其他频道消息忽略。首次建立及重新连接都检查缺口，保留共享会话凭据、DoH 路由、取消和退避规则。

页面可见且处于 RESUMED 时订阅，离开详情或进入后台取消。每批通知合并约3秒后读取一次最新话题目录，保留已显示正文、点赞等已确认写入、阅读状态及定位目标。已加载窗口覆盖原目录末尾时，最多追加20条新回复；仍有旧楼层待加载时仅扩展目录，后续沿原分页顺序续载。追加后不主动滚动，不把获取的新楼层计为已读。

检查与刷新、跳转按代次隔离；分页及写入进行中先等待，等待期间收到的新通知保留下一轮检查。失败保留正文与目录并退避重试，推送端点的验证失败停止本轮订阅，不据此判断主站登录已失效。

## Existing note audit

已检索活跃笔记。与[首页实时提示](../../implemented/feature/2026-10-08-home-live-refresh.md)部分重叠，复用传输但不改变首页刷新策略；与[到底续载](../../implemented/feature/2026-10-10-topic-continuation.md)部分重叠，为其提供新增楼层目录；与[连续跳转窗口](../../implemented/bug-fix/2026-10-11-topic-jump-window.md)部分重叠，保留连续窗口与双向分页；与[DoH 网络](../../implemented/feature/2026-10-12-doh-network.md)部分重叠，新增订阅同样注入网络路由。没有完全吸收、过时提案或需要归档的旧决定。

## Subscription contract

TopicUpdates.observeTopic 提供可取消的检查信号流。DiscourseMessageBus 实现单话题订阅，TopicMessageBusCursors 对 created 及 reload_topic/refresh_stream 发信号，其他消息只推进游标。初连、重连及状态游标变化同样发出信号。

## Incremental state contract

TopicViewModel.setUpdatesActive 管理可取消的订阅任务，恢复时立即排队补查。CONFLATED 信号队列合并约3秒内通知，检查进行中收到的信号留待下一轮。快照只扩展既有目录尾部，不恢复已过滤的中间不可访问楼层；已加载到原末尾时获取最多20条新正文，其他位置只更新目录。读取过程不覆盖现有正文和操作状态，刷新或跳转后丢弃旧代次结果并重新检查；失败保留阅读现场并退避重试。

KudoApp 的话题路由按账号创建 ViewModel，注入 DiscourseMessageBus 与网络路由。ObserveTopicUpdates 通过 LifecycleResumeEffect 订阅当前 RESUMED 页面，暂停、离开或验证遮罩阻断阅读时取消；恢复后补查遮挡期间的新增回复。

## Reply presence contract

正在回复采用 Discourse Presence 协议：登录后 GET /presence/get，channels[] 为 /discourse-presence/reply/{topicId}，获取 users 和 last_message_id；将 /presence/discourse-presence/reply/{topicId} 加入当前话题的同一次 MessageBus poll。连续游标的 entering_users、leaving_user_ids 更新名单，重复消息忽略；游标跳跃、服务器重置或重连重新读取快照。当前账号从名单剔除，同一用户多会话按ID去重。此功能只读取其他用户状态，不向 /presence/update 上报输入。

头像变化立即更新，不等待新回复的3秒合并窗口，也不触发话题目录查询。断线、暂停、退出或更换账号清空头像；Presence 无权限、关闭或读取失败不阻断原有新回复订阅，失败后退避补查。匿名不读取受限 Presence。显示头像时文字仅隐藏绘制、保留布局占位，最多3个重叠头像覆盖在占位区域，不参与按钮宽度计算；超过3人在第3个头像上覆盖 +N（5人是+2）。头像使用 PLAIN，不加载身份或状态角标。按钮内边距、宽度与相邻楼层按钮的位置不随人数改变；窄屏头像覆盖原图标占位，保留按钮原宽度。点击仍打开回复框，无人时恢复原文字或图标。

TopicState.replyingUsers 保存当前订阅名单，TopicActivity 将目录补查信号与头像快照分开。ViewModel 的订阅代次隔离暂停前的清理任务，旧任务不能清空新订阅名单；源完成、异常、页面暂停均清空名单，并保留已加载正文和阅读状态。

协议依据为 Discourse 的 presence service、PresenceController、PresenceChannelStateSerializer 和 discourse-presence 插件。此扩展沿用当前笔记的频道、生命周期及账号边界；活跃笔记检索无独立 Presence 决策需要归档。新增协议、状态及头像布局验证随各小单元记录。

## Alternatives considered

- 收到通知后整页刷新可直接得到最新内容，但会替换已加载窗口和阅读现场；只合并目录及新正文。
- 固定频率轮询详情实现简单且不依赖订阅，但没有新回复时也持续请求；采用频道通知与恢复后的补查。
- 仅按通知中的帖子ID直接追加更省一次目录查询，但断线或游标重置会遗漏消息，也可能跨过尚未加载的旧楼层；以服务器目录确认顺序与缺口。
- Presence 单独开长轮询可保持接口独立，但会为同一详情增加连接、凭据刷新和取消路径；将其并入当前话题 poll，仅拆分事件类型。
- 定时读取 Presence 快照能绕过增量游标处理，但持续消耗主站请求且状态延迟取决于周期；使用初始快照及消息增量，发生缺口再补查。

## Testing

- Presence 协议新增 TopicPresenceTest 6项，连同 TopicMessageBusTest、MessageBusTest 共16项通过，覆盖初始名单、自身过滤、多会话去重、加入离开、游标缺口补查、首次连接失败恢复、权限降级、匿名与凭据隔离。日志 artifacts/topic-presence-transport-tests.txt。
- 页面状态新增3项 Presence 用例，TopicUpdatesTest（11项）与 TopicViewModelTest（9项）共20项通过：头像事件不查目录、不改阅读状态，暂停清空、恢复换用新名单，源异常清空但不覆盖正文。日志 artifacts/topic-presence-state-tests.txt。
- Presence UI 在 RMX2202 / Android 13 上与既有追加、生命周期、按钮宽度、窄屏楼层控件共7项回归通过。覆盖1..5人切换、最多3头像、5人+2、重叠坐标、没有身份/状态角标、按钮仍可回复、无人恢复文字和关闭话题禁用。280dp、1.3倍字体的深色模式在三种玻璃材质下均无控件重叠或楼层文字溢出。日志 artifacts/topic-presence-device-tests.txt。
- 增加可控图片加载后重新运行3项 Presence UI 用例，验证头像图像像素完整显示、没有角标图像请求，通过。截图 artifacts/topic-presence-five-light.png、artifacts/topic-presence-five-liquid-narrow-dark.png；日志 artifacts/topic-presence-image-device-tests.txt。
- 当前手机账号的真实 Presence 快照和合并 poll 在线探针1项通过，话题2977660当时 users=0。多人到达、离开与+2布局采用模拟数据验证，没有向社区发布输入状态。日志 artifacts/topic-presence-online-probe.txt。
- Presence 版 Debug 使用既有签名、adb install -r 保留数据覆盖安装；Release 构建、签名和版本/ABI检查通过，仍为0.2.0/2001，仅arm64-v8a。产物 artifacts/release/0.2.0/topic-presence/kodo_0.2.0_release_arm64-v8a.apk。
- 按钮占位回归先复现：普通布局1人时宽度294px变240px，窄屏5人时144px变180px，两项宽度断言失败，日志 artifacts/topic-presence-width-repro.txt。修复后文字透明但保留测量，头像匹配文字区域，窄屏覆盖图标区域且不改内边距；新增0..5人宽度及相邻楼层坐标断言。Presence UI、既有按钮宽度及窄屏楼层控件共5项真机回归通过，日志 artifacts/topic-presence-width-device-tests.txt；截图 artifacts/topic-presence-width-light.png、artifacts/topic-presence-width-narrow-dark.png。此次只改变布局，不重跑已通过的无关网络协议测试。
- 占位修复的正式包构建与签名、版本、ABI校验通过，仍为0.2.0/2001、arm64-v8a，产物 artifacts/release/0.2.0/topic-presence-width/kodo_0.2.0_release_arm64-v8a.apk；开发版核对原签名并保留数据覆盖安装。
- TopicMessageBusTest 与既有 MessageBusTest 共10项通过，覆盖频道隔离、游标去重与重置、初始及重连补查、取消及凭据隔离。日志 artifacts/topic-live-transport-tests.txt。
- TopicUpdatesTest、TopicViewModelTest、TopicFloorNavigationTest、TopicResumeReadingTest 相关 JVM 测试通过；补充刷新竞态后 TopicUpdatesTest 全部8项通过，覆盖末尾追加、前面阅读不跨楼层、通知合并、刷新/跳转竞态、失败不丢正文、暂停取消及恢复补查。日志 artifacts/topic-live-state-tests.txt、artifacts/topic-live-state-final-tests.txt。
- RMX2202 / Android 13 真机运行 TopicLiveRepliesUiTest（2项离线场景）、TopicContinuationUiTest（4项）、TopicBackwardPaginationUiTest（6项），共12项通过。追加前后第10楼的屏幕坐标误差不超过2px；暂停后订阅数归零，恢复无通知也补查，前面阅读时仍按21..40顺序加载。截图 artifacts/topic-live-before.png、artifacts/topic-live-after.png，日志 artifacts/topic-live-device-tests.txt。测试用本地拦截数据，不向社区发帖。
- 显式启用 liveReadOnlyProbe 后，当前手机登录态成功读取话题2977660（目录1636项）并收到 MessageBus 初始补查信号，在线探针1项通过，日志 artifacts/topic-live-online-probe.txt。未发帖、未上报阅读时长，也未观察真实新回复事件。
- Debug 核对既有签名后以 adb install -r 覆盖安装，保留已安装开发版数据。Release 构建与签名校验通过，参数0.2.0/2001，仅arm64-v8a，产物 artifacts/release/0.2.0/topic-live-replies/kodo_0.2.0_release_arm64-v8a.apk。

## Consequences

新回复能进入当前阅读目录，保留分页窗口、已确认操作和阅读锚点；代价是当前详情新增一条独立长轮询连接，以及通知后的目录补查。Presence 共用该连接，但进入和恢复时增加一次名单读取。实时更新受网络、MessageBus 和主站接口可用性约束，新回复有约3秒通知合并和请求延迟，正在回复名单按推送即时更新。目录补查读取话题快照，但不替换现有正文；本功能不实现所有编辑、删除和点赞的实时同步。推送端点连续验证失败会停止订阅，重新进入或恢复页面时再尝试。

真机自动化验证只覆盖本地模拟新增回复和多人正在回复；在线只读探针用于检查当前会话、Presence 快照和订阅连接，不保证测试期间恰好出现服务器新回复或正在输入的用户。用户开启隐藏 Presence 时不会出现在服务器名单中，未登录或无频道权限时按钮使用原样式。若以后需要完整实时编辑、删除同步或向 Web 上报 App 输入，应重新审视目录合并、正文替换和输入状态上报契约。
