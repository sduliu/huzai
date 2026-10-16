# Agent Note: 详情作者与回应状态

Status: implemented

## Problem

详情页将楼主重复放在正文，顶部没有关注入口；操作栏只认赞，无法区分 L 站的其他回应。旧 TopicViewModel 忽略服务器返回值、自行翻转赞和计数，分页又用请求前的快照覆盖已更新帖子，容易显示错误状态。用户要求作者进入固定标题栏、右侧关注和更多，并按酷安同机截图缩小写回复区域。

## Decision

详情读取、主帖补齐和作者关注资料分开加载。楼主身份来自第一楼或 details.created_by；深链补齐主帖供底部操作使用，不将该主帖插入正在阅读的回复区域。关注状态绑定当前账号和作者身份，KudoApp 的详情 ViewModel key 包含当前账号 ID；资料失败有独立重试，不能让成功加载的正文变为错误页。

短按撤销当前回应，无回应才添加 heart；长按打开服务器 valid_reactions 定义的“回应”选择面板。写入使用服务器完整帖子，操作前重新读取当前楼层，避免旧 UI 快照重复翻转；同楼层写入互斥。分页合并时读取最新状态，保留并发写入结果。已补齐主帖首次进入分页列表时也使用最新 pinnedOriginal，不能让请求前的副本盖掉其回应。刷新在写入期间不启动，写入在整页刷新期间不启动。

作者头像、昵称和时间进入固定标题栏，关注与更多位于右侧。正文保留主题标题，回复各自保留作者。底部沿用官方LiquidButton材质、最小高度48dp、文字不加粗和20dp细线笔形图标；操作组现由[帖子Boost](2026-10-10-post-boosts-and-length.md)与[液态楼层索引](2026-10-10-topic-floor-navigation.md)部分接管。写回复仍按内容自然撑开，窄屏和大字号的图标模式、组间距和中央进度由楼层索引笔记约束。

TopicControls 集中标题栏、回应入口、底栏与回应选择。更多和回应使用既有 LiquidModal；正文玻璃采样层与包含底栏的模态采样层分开，弹窗遮罩不进入源图。弹窗显示和退出期间，背景语义、焦点和操作不可穿透。关注不能作用于本人，按钮按作者资料的权限显示；回应按 can_undo 决定能否撤销或更换。

## Existing note audit

- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，该篇负责网络字段和路径，本篇负责状态合并与界面。
- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，保留原生渲染和页面整体边界，详情交互由本篇负责。
- [官方玻璃组件](../../implemented/architecture/2026-10-07-backdrop-demo-navigation.md)：部分重叠，保留材质和手势来源，只更新详情尺寸事实。
- [行内表情](../../implemented/bug-fix/2026-10-09-inline-emoji-images.md)：部分重叠，回应选择复用同一站点图片组件，不引入字体依赖。

## Alternatives considered

- 继续本地翻转 liked 并加减计数：反馈即时且请求后处理少；无法表达其他回应、服务器权限和同时发生的计数变化，改用返回帖子。
- 把关注资料和主帖补齐作为详情成功的前提：数据完整后才展示最简单；深链或关注资料请求失败会阻塞可读正文，改为辅助状态。
- 用固定小高度压缩写回复：外形紧凑且易对齐；会裁切放大字号，使用与酷安相同的最小高度并调整宽度、边距和内容重量。

## Verification

服务器结果被忽略、分页覆盖写入的两项测试先失败，输出 artifacts/topic-state-repro.txt；深链 pinnedOriginal 进入分页列表时被旧副本覆盖也单独复现，输出 topic-pinned-repro.txt。TopicViewModelTest 的 6 项与完整 76 项 JVM 测试通过，覆盖连续点击、旧快照取消、失败保留状态、关注确认、辅助资料失败和深链主帖。最终构建和 Lint 通过，0 errors / 28 existing warnings，日志 topic-final-build.txt。

RMX2202 / Android13 第一轮详情与 Surface Liquid Button 的 10 项真机测试通过，21.283秒，日志 topic-ui-instrumentation.txt。固定楼主与唯一作者、关注切换、短按取消爱心/其他回应、长按仅打开选择、后台隔离、更多动作、权限、深链动作对象和行内图片已验证；三种玻璃材质的深色130%字号继续通过原有文本边界检查。行内图片使用确定性 PNG 夹具验证加载和排版，不把夹具色块当作真实表情截图。

最终详情、Surface、头像与社区共28项真机回归通过，59.081秒，输出 topic-final-instrumentation.txt。写回复内容起点靠左收尾后，Debug及测试APK构建通过，Surface的5项再次通过，10.395秒，输出 topic-aligned-build.txt / topic-aligned-instrumentation.txt；正文、状态与协议代码沿用前述76项JVM及28项真机结果。

真实详情截图为 topic-detail-after.png，同机酷安参考为 audit-coolapk-topic.png；完整对比 topic-detail-comparison.png 与底栏三段对比 topic-footer-comparison.png 已人工检查。原回复按钮408×144px、左/下边距42/36px；现在398×144px、边距60/60px，酷安为400×144px、边距60/60px。文字起点同为180px，结构化测量保存在 topic-reply-measurements.json。真实回应面板 topic-responses-real.png 确认 distorted_face、贴吧和B站图片表情正常，线上只打开面板取图，写入仍使用夹具验证。

分发包为 artifacts/kudo-topic-detail.apk，保留数据覆盖安装；安装包与分发包SHA256一致，73ecb3aedcd214f67c4ea35358eaf685cc9602e9375cf900884d65de78cddfc9，证据为 topic-apk-verification.json。

## Consequences

撤销受站点 can_undo 和时限约束，无法撤销时保留当前回应。网络超时不能判断服务器是否已写入，不自动重试，刷新负责重新确认。关注资料或主帖辅助加载失败时，对应操作暂不可用。
