# Agent Note: 逐楼未读蓝点与实际阅读反馈

Status: implemented

## Problem

详情已有后台 timings 同步，但未解析逐楼 read，界面无法区分未读，也没有实际读到后的渐隐反馈。

## Decision

Post.read 保留 nullable 三态，仅登录且服务器明确 false 的楼层显示右侧蓝点。TopicReadingProgress 累计实际曝光达到1秒时独立发出一次楼层事件，不依赖5秒批量上传；正文末尾、遮挡、前后台等原有曝光规则继续成立。主帖顶部与完整回复作者行使用同一蓝点，实际阅读后用1800ms淡出。

详情 ViewModel 按账号和话题保存本会话已读楼层集合，与服务器字段分开，防止分页、祖先补齐或刷新晚到旧快照重新点亮。集合只影响即时反馈，后台写入仍由既有 TopicReadingSync 负责，只有服务器确认才失效首页缓存。新会话重新尊重服务端 read，不假定最高阅读楼层以下都看过。

## Related decisions

检索 read、阅读、timings、楼中楼。[实际阅读同步](../../implemented/bug-fix/2026-10-10-topic-reading-sync.md)部分重叠，保持计时和账号隔离；[楼中楼](../../implemented/feature/2026-10-10-nested-replies.md)保持摘要不计读。本篇新增即时反馈，不替换同步策略。无完全吸收或过时提案。

## Alternatives considered

- 收到上传成功后再淡出能直接反映服务器确认，但批量上传和离线网络会让读过的楼层长时间留蓝点，不能及时反馈实际浏览。
- 用 last_read_post_number 推断更低楼层全读，字段少且易合并，但深链跳楼无法证明中间楼层逐条读过。

## Testing

TopicReadingProgressTest 与 TopicViewModelTest 验证三态解析、1秒门槛、一次反馈与批量上传独立，以及晚到分页、刷新和跳过楼层。相关6类共34项 JVM 通过（artifacts/topic-flow-regression.txt）。首轮复现 TopicViewModelTest 未等待后台任务取消便 resetMain 的测试收尾竞争，改为 cancelAndJoin 后通过，失败证据 artifacts/topic-flow-checks.txt。

TopicReadingUiTest 的7项真机用例全部通过（artifacts/topic-flow-device.txt），验证服务器三态、访客隐藏、1800ms渐隐、真实曝光回调和摘要不计读，以及原有长文末尾、遮挡和账号销毁规则。渐隐用例补录正常/中间态截图后再次通过（artifacts/topic-flow-final-device.txt）；已查看 post-unread-blue.png 与 post-unread-fading.png。既有 TopicReadingSyncTest 继续验证后台表单、换号隔离与失败不重试。

本轮共29个不同真机用例通过：分页4、菜单10、阅读7、楼中楼3、详情5，分批日志不能将重复用例再次累加。Debug 构建与 Lint 通过（artifacts/topic-flow-final-build.txt），0错误、30既有警告和1hint。测试使用离线回调及 MockWebServer，未验证真实账号写入端到端。

## Consequences

本地反馈不是写入确认，网络失败后下次会话可能重新显示服务器未读。进程结束前最后计时仍可能来不及送达。

## First-floor clarification

The topic top bar does not render an unread indicator for the original post. Floor one is represented by the topic header, so showing the same indicator there duplicates the first-floor state and interferes with the header layout. Unread indicators remain available for later posts.
