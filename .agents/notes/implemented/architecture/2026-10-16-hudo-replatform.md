# Agent Note: 虎Do —— 基于 kudo-android 重筑虎扑客户端

Status: implemented

## Problem

Huzai（com.huzai.app，main 分支）功能已对齐官方形态，但 UI 是自造转场与普通 Material 组件，观感不满足要求。kudo-android（Apache-2.0）同为社区站第三方客户端，具备液态玻璃底栏、页面化转场、阅读位置保留、后台探新、请求限流、单测与发布管线，但数据层整捆绑定 Discourse。

## Decision

在 `kudo-base` 分支上整体移植 kudo-android 源码树，逐步将其数据域替换为虎扑：

- 包名 `com.huzai.kudo`，与 `com.huzai.app` 同机并存；应用名「虎Do」。namespace 保持 `com.kudo.android` 不动，避免 371 个 Kotlin 文件的 R 引用全量重排。
- 底栏槽位重排：首页=虎扑综合流、分类=板块目录、中央钮=搜索（壳）、**通知槽位整体换成「比赛」**（匿名只读没有通知需求，该槽位天然闲置）、我的=设置/关于。
- kudo 的 Discourse 登录/发帖/通知消息总线保留壳体入口，提示「暂未开放」，不删——用户要求留壳。
- DoH+ECH native 层（Rust aarch64 so）当前保留、构建可用；后续若 CI 成本或维护负担过高可整体移除，网络层统一经 `network/DohNetwork` 接入点收敛，摘除面约 10 个文件。
- 请求治理复用 `RequestScheduler` + `RequestSchedulingInterceptor`（per-origin 并发 3、滑动窗口、429 冷却）；HupuCache 降级为磁盘缓存层置于 Repository 之下。
- 虎扑数据层（HupuApi/HupuParser/HupuMatchApi/HupuBoardApi/HupuTeamApi/HupuPrefs 等）从 main 分支逐批移植进 data 层，模型映射 Topic→HupuThread、Category→板块。
- 赛事组件（ScorePage/MatchDetailPage/BoardPage/ScorePickerPage 系列）作为新页面嫁接进 NavHost，适配 kudo 主题。
- CI：`.github/workflows/build-hudo.yml` 仅在 `kudo-base` push 时构建 debug APK，固定签名 `ci/hudo-debug.keystore`（约定口令 android/hudo-debug），资产固定名 `hudo-debug.apk` 覆盖发布到 `nightly-kudo` tag，与 main 的 `nightly-board` 并行不冲突。

## Alternatives considered

- **在 Huzai 现有代码上逐块搬 kudo 的 UI 模式**：增量安全，但 kudo 的玻璃底栏/页面容器/主题系统是一个整体材质体系，拆出来搬等于重写其一半 UI 层，且永远追不上其对细节的打磨。否决。
- **直接删除 DoH native 层再起步**：摘除面小但仍有 10 个文件与构建脚本耦合；先以带 Rust 的 CI 跑通基线再剪，风险更低。暂缓。
- **改名包名顺带统一 namespace**：语义更干净，但 R/BuildConfig 引用重排收益为零、diff 噪音巨大。否决。

## Consequences

收益：一次拿到成熟的产品骨架（导航、刷新、转场、测试、发布管线）；两个 app 同机对比验证设计优劣。
代价：分支与 main 几乎不共享代码历史，main 上的修复不会自动流入；kudo 的 Discourse 残留（composer、通知、DoH 设置页）在阶段清理前是可见死功能；Rust+NDK 使每次 CI 增加数分钟构建时长。
验证：首个 kudo-base 构建产出可安装 `hudo-debug.apk` 即阶段 1 完成；后续阶段验收以「信息流接通 → 赛事移植 → 双 app 对比」为序。

## Phase 2–4 verification (2026-10-17, run 483645e, CI green)

数据层桥接落地形态：`CommunityRepository` 保留为 UI 唯一入口，读路径全部转 `HupuRepository`（mobileHome 综合流 / all-gambia 热帖 / topic-daily / categories 板块目录 / topicPage 板块流 / threadDetail / threadReplies / floorReplies / search / userProfile），写路径保留 Discourse 签名直接抛 SIGN_IN——登录/发帖壳语义由异常承载，无需删代码。
`HupuIds` 负责 Int↔长串 id 双向映射；`posts()` 按 floor→页码反查 `threadReplies` 补齐 stream 占位项，真实 pid 经 `bindPid` 回写；`replies()` 楼中楼用 `floorReplies(tid,pid,maxpid)`，`subPids` 侧表记翻页游标。
AGP 9 迁移要点：删 `kotlin-android` 插件（内置 Kotlin）、`sdkDirectory` → `androidComponents.sdkComponents`、`jniLibs.srcDir` 不接受 Provider。
模拟器验收（redroid12, com.huzai.kudo + com.huzai.app 并存）：综合流/步行街/每日话题 feed ✓、板块目录=虎扑版块树 ✓、板块流 ✓、帖子详情（作者/时间/标签/正文 HTML）✓、比赛 Tab=频道条+赛程卡+资讯 ✓、NBA 积分榜入口 ✓、比赛详情（记分板+评分/数据统计/实录 Tab）✓、球队页（尼克斯榜单摘要+按月赛程+胜负标）✓、自定义评分频道（拖拽排序+勾选）✓、我的=登录壳+设置+关于虎Do ✓、虎仔独立可启动 ✓。`npm run verify-notes` 137/137 过。
遗留壳（运行时抛「请先登录」属预期）：个人中心列表、通知、书签、发帖编辑器、关注/点赞写操作、楼中楼深层「展开」递归。标签目录为空（虎扑无对应物）。

## 体验打磨批（a1f985b, CI green）

- 关闭 GitHubUpdates 自动检查（check 桩抛异常，checkOnce 静默吞掉，永不弹「发现新版本」）。
- 卡片作者行：author 为空（综合流无作者字段）时整体隐藏——兜底「虎扑」文案与 DO 占位头像均无信息量；真名（虎扑足球资讯等）照常显示。
- 查看数：`views>0` 才渲染 Stat（综合流无 read 字段自动隐藏；板块流有真实查看数继续显示）。
- 视频帖：`toTopicDetail` 给主楼 cooked 前置 `<video src poster>`，RichContent video 分支带 poster 生成 Embed 卡片（「播放视频」，点击跳外部播放器/浏览器）。
- 移除 Discourse Boost 火箭按钮（主帖+回复两处）；楼层定位条/跳楼输入 `floorEnabled=false` 隐藏（保留回复/点赞主操作）。
- 表情：`hupuEmojiize` 归一——CDN emoji/emotion 图补 `class="emoji"` 走渲染器行内小图路径；`[令牌]` 文本替换为同路径 emoji img（映射复用发帖器的 HUPU_EMOJI 28 表情表）。
- FeedFilter.TOP「每日话题」删除（无对应官方一级筛选）；板块目录按 name 去重（虎扑大类树同板块多分组重复）。
- 性能：debug 构建开 R8+shrinkResources（签名不变，`install -r` 仍互通），缓解液态玻璃+debug 字节码叠加的卡顿。

## 上游取长补短批（e7274a0+a4590f6, CI green）

上游 kudo-android b258155→c328d5c 共 20 提交评审后选择性移植（仓库无共同祖先，逐文件搬运而非 git merge）：
- **移植**：正文选区引用（`PostQuoteSelection`/`PostQuotes` + SelectionContainer 注入 RichContent/SpoilerContent）——长按正文/回复/楼中楼出现「引用/复制引用」菜单；`postQuoteMarkdown` 已从 Discourse `[quote=...]` 语法改为中文纯文本 `引用 @作者 发表于 N楼：` + `>` 引用块（复制引用可外贴；「引用」动作仍走编辑器登录壳）；回复按钮文字→`ChatBubbleOutline` 图标（48dp 命中区+testTag）。
- **拒绝**：解决方案标记/状态切换、回应汇总+历史面板、标签本地搜索、置顶系统通知、昵称徽章/头像挂件、点赞历史——全部依赖 Discourse 服务端语义（solution/accepted_answer/reactions/notification types），虎扑无对应数据契约，移植即成死代码。
- **暂缓**：NSFW 图片模糊开关——上游按 Discourse 附件标记判定，虎扑图片无 NSFW 元数据；要做需先定内容分类策略。
- **已含**：标题 emoji 修复、代码复制图标化、面板自适应高度等渲染修复——hupuEmojiize/IconButton 已先行覆盖或属上游 Discourse 特有组件。
模拟器验证：选区菜单四项（Copy/Select all/引用/复制引用）✓、「已复制引用」toast+剪贴板写入 ✓、回复图标按钮 ✓。

## 应用内更新管线（v0.2.0 首发）

- `GitHubUpdates` 指向 sduliu/huzai `releases/latest`，资产名 `hudo_{ver}_release_arm64-v8a.apk`；`KudoSourceUrl` 一并换仓（关于页源码/外链同源）。
- `HomeUpdateViewModel` 恢复真实检查：首页 Tab 激活+resume 时 `checkOnce`（会话内仅一次），新版即弹 `UpdateSheet`；「关于→检查更新」手动入口走 `UpdateDownloadEnvironment` 应用内下载+sha256 校验+拉起安装器。
- 发布管线 `release.yml`：`v*` tag 触发，`release_version.py` 由 tag 推 versionCode/Name；release 包用 `ci/hudo-debug.p12` 签名（与 nightly 同身份，`install -r` 互通，不引入第二把密钥）；`prepare_release.py package` 校验签名指纹+包名（com.huzai.kudo）+非 debuggable+单 ABI 后产出 .sha256；发布标题「虎Do x.y.z」。
- 门禁裁剪：上游 release 的 `testDebugUnitTest`/`lintDebug`/`cargo test` 未纳入——201 个单测绑死 Discourse 契约，虎扑后端下大面积失效，套件分诊单独立项；当前门禁=verify-notes+构建+签名/APK 校验（与 nightly 实际覆盖对齐）。
- 版本号策略：nightly 恒 0.1.0（run_number 作 versionCode），正式版 vX.Y.Z 单段数字 tag；首发 v0.2.0 让 nightly 用户可感知升级。

## CI 事故复盘与预防（v0.2.0 发版）

事故链：v0.2.0 首 tag 因 `prepare_release.py` 正则只认 `Signer #1` 而 apksigner 37.0.0 输出已改为 `V2 Signer:`，签名校验失败，重打 tag ×3 才定位修复——每次失败 GitHub 都发邮件。
根因：把 SDK 包从上游 build-tools;35.0.0 换成 37.0.0 时，没有检查消费该工具 stdout 的脚本；且改发布管线前没在本地跑 `python3 -m unittest discover -s scripts/tests`（当时 27 个失败本可提前暴露）。
修复：signer 正则兼容新旧两种格式并补 V2 Signer 回归测试；test_prepare_release/test_release_version/test_release_notes 全部对齐 huzai 仓名/包名/资产名；test_release_workflow 按虎Do 实际契约重写并新增 `test_package_step_uses_the_installed_build_tools`（安装的 build-tools 版本与 package 步骤引用必须一致）。
预防规则（已写入 AGENTS.md）：改 CI/发布脚本或 toolchain 版本 → 本地先跑 scripts/tests；升级工具版本 → grep 所有解析其 stdout 的脚本。

## 首页频道条（综合+自选板块/赛事频道）

官方形态对齐：顶部频道条「综合 | 自选频道 | 类别」，左右横滑复用既有 HorizontalPager+FeedTabBar；右端「＋」进频道管理。
- 频道键 schema：`latest`（综合，固定第一）/`board:<板块url>`（社区板块→topicPage 分页）/`sport:<GAMES id>`（赛事频道）。`FeedViewModel.select` 对 board:/sport: 前缀放行；`_pages` 多页缓存天然生效。
- 持久化：`HupuPrefs` `home_channels_v1`（[{k,l}] JSON）+ `homeChannelsVersion` 状态信号（KudoApp 按版本 remember 重读）；`effectiveHomeChannels()` 默认 NBA/国际足球/步行街/英雄联盟/CBA。
- 数据契约迁移：`HupuBoardCfg/HupuBoardStanding/HUPU_BOARDS` 从 hupuui.pages.BoardPage 上移到 `hupu/HupuBoards.kt`——频道 feed/赛程候选池属数据层，BoardPage/ScorePage/MatchDetailPage 改 import（同模块 internal 不变）。
- sport: 频道 feed：`tagCode` → `boardNews`（游标 `n<pre>|<anchor>`）/`soccerSsr` → `soccerNews()` /无 tagCode → `topicNews(cfg.newsTopic)`（游标 `p<N>`）；不可开帖的运营位（`openableAsThread=false`）过滤，`HupuBoardNews.toTopic()` 桥接。
- 赛事头部 `SportChannelHeader`：复用 `BoardMatchRow` + BoardContent 同款「进行中→今天/最近比赛日→未来」≤4 场挑选；赛程走 `CommunityRepository.sportMatches`（与比赛 Tab 同一条 matchSchedule）。
- 跳转协议 `ScoreNavRequest(game, matchId?, nonce)`：`ScorePage(request=)` —— LaunchedEffect(nonce)+`handledNonce`(rememberSaveable) 幂等（Tab 出/入组合会重放 effect）；`externalPinned` 使外部频道不受「不在自选→回落」重置影响，手动切频道解除 pin。点比赛行=切比赛 Tab 并直开该场详情（soccer 的 outBizNo 换钥匙逻辑在 openMatch 内已自带）。
- 频道管理 `HomeChannelsScreen`（kudo MaterialPageScaffold 风格）：综合固定行+已选（上下移/移除）+候选池（全部 HUPU_BOARDS 去掉「热门赛事」聚合+categories() 板块树）。
- 本地构建环境记录：本机无 Android SDK，已装 ~/android-sdk（cmdline-tools+platform android-37.0+build-tools 36/37）；AGP 要求平台目录 `platforms/android-37` 且无 package.xml（`platforms;android-37.0` 包目录名不一致会判 inconsistent location 拒扫）；Kotlin 编译验证用 `ANDROID_HOME=~/android-sdk sh gradlew :app:compileDebugKotlin -x buildDoh`（buildDoh 依赖 NDK/Rust 工具链未装，属预期跳过项）。

## 首页资讯样式对齐官方（缩略图+置顶展开+频道内容分工）

官方形态核实（bbs.hupu.com 桌面页 + 分类树实测）：列表图统一为右侧 100×76 缩略图（BoardNewsRow 早就是这版）；置顶区是多条完整条目而非单条摘要；首页赛事频道帖流=社区版块帖（湿乎乎 /vote、足球话题区 /topic），「比赛」Tab 的 BoardPage 承载编辑资讯（newsList）——两侧内容不重复。

- `TopicCard`：有图条目改为「左标题+摘要 / 右 104×78 `TopicThumbnail`」（替换原整宽 1.9:1 `TopicPreviewImage`，后者已删；`imageAspectLabel/ImageAspectBadge` 仍供 RichContent 正文图角标用）。
- 置顶精简渲染整体下线：删 `CompactPinnedTopicRow`、`DisplaySettings.compactPinnedTopics`（含 prefs 读写）、设置页开关、TopicCard/TopicTitleRow 的早退分支——置顶项以完整卡片呈现（📌 图标仍在作者行末）。
- `HupuBoardCfg.boardUrl`：首页 sport: 频道帖流板块映射（nba→/vote、soccer→/topic、cba→/cba、epl→/yingchao、lol→/lol、lpl→/822、kog→/kog、cs2→/csgo、val→/803、pubg→/pubg、pubgmobile→/gp、tennis→/tennis、tabletennis→/pingpang、badminton→/yumaoqiu、olympics→/753、wnba→/wnba、cuba→/cuba、worldcup→/soccer；lck/commonhotsports 无对应版块留空）。映射取自 /all-gambia 分类树实测列表——此前随手猜的 epl/lpl slug 并不真实存在。
- `topics()` sport: 分支新序：boardUrl 非空 → topicPage/board 回落取板块帖流（板块页无分页，p>1 直接空页终止避免重复）；仅首页失败时回退原 newsList 链。
- 首页头部：`ic_linux_do` 残留图标换为官方虎扑 logo（drawable-nodpi/ic_hupu.png，来自 w1.hoopchina.com.cn/images/m/hupu_logo_new.png，88px）；搜索占位文案由 linux.do 口号「真诚·友善·团结·专业」改为「湿乎乎 · 足球 · 电竞 · 步行街」。

## 列表标签去重 + 表情/贴纸尺寸还原

- `TopicCard`/`TopicTitleRow`：tagDetails 中与版块同名的标签不再重复渲染（虎扑帖的 `topic.name` 即版块名，作者行已显示）。
- `RichContent`：虎扑 CDN 图文件名内嵌原图尺寸 `_w_X_h_Y_`，`<img>` 无 width/height 属性时据此还原 ratio 与 displayWidth；宽 ≤320px 的贴纸/表情按约 1/3 原宽限宽（48–106dp，对齐官方贴纸观感），大图行为不变。无尺寸信息的图维持整宽。
- 楼中楼入口确认：`replyNum` → `Post.replyCount` → `ReplyPreview` 灰卡预览 +「查看 N 条回复」链接 → 弹层分页，链路完整（对应官方「查看N条回复 >」）。
