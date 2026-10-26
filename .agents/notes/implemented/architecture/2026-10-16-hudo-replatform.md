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

## 频道切换顺滑 + coil2 图片解码修复

用户反馈「板块切换不顺滑 + 图片加载失败」，两个独立根因：

- **切换不顺滑**：`HorizontalPager` 默认 `beyondViewportPageCount=0`，相邻页在滑动手势中才现组整棵 LazyColumn（+图片）→掉帧；且 `select()` 挂在 `pager.settledPage`，动画落定才发网络请求 → 空页滑入、内容后蹦。修复：`beyondViewportPageCount=1` 预组合邻页；`select()` 改挂 `pager.targetPage`（手势提交翻页时即更新），取数与动画并行；`settledPage` 采集器保留只做头部折叠复位。targetPage 随拖动位置/速度连续重算，滑回自动翻回 select 原频道，`select()` 幂等（同 filter 早退、cached 命中不重取）。
- **图片加载失败**：`hupuui` 子树全部走 coil2（`coil.compose.AsyncImage/SubcomposeAsyncImage`），但 `KudoApplication` 只实现了 coil3 的 `SingletonImageLoader.Factory`——coil2 落默认 ImageLoader，无 GIF/WebP 动图解码器，虎扑评论区大量 `*.GIF` 表情必挂（解码错误非 4xx，`isRetryableImageError` 判可重试 → 白重试两次再出「图片加载失败」）。修复：`KudoApplication` 同挂 `coil.ImageLoaderFactory`，coil2 单例注册 `ImageDecoderDecoder`（API≥28）/`GifDecoder`，`callFactory` 复用 `container.imageHttpClient`（okhttp3.Call.Factory 双端通用）。另在 `parseHtmlBlocks` 出口对 `HtmlBlock.Image` url 过一遍 `normalizeImageUrl`（`//` 补 https、`http://` 升级 https 防明文拦截）——与 PlayerDetail* 各页同一约定。

## 频道顺滑补强 + 板面细节对齐官方（二轮反馈）

- **预取**：`FeedViewModel.prefetch(filter)` 把相邻频道首页数据装进 `_pages`（不动 `_state`，失败移除占位）；`FeedScreen` 在 `settledPage` 落定后对 index±1 两页预取——滑过去 `select()` 命中缓存即出内容。`setUser`/`refreshIfRequested` 清 `_pages` 时同步取消在途预取防串号。
- **官方比赛大卡 `BoardMatchCard`**：赛事名居顶 + 队名[队标30dp] 20sp 大比分 + 底行（进行中红/已结束→scoreCountText「N人评分」/未开始→开赛时间）；板块页热门条与首页 `SportChannelHeader` 共用（行间 0.5dp 分隔线），紧凑 `BoardMatchRow` 仍留给「全部赛程」平铺。
- **资讯 meta 行**：去掉 sparkles/气泡小图标，改官方式纯文字「亮 N · 回复 N · 时间」；标题 15→16sp。
- **比赛 Tab 默认频道**：`selectedGame` 初值由恒为热门赛事改为 `effectiveGames` 首枚 chip——首屏内容与第一栏对齐；空列表回落热门赛事。
- **头像→用户主页**：去掉 `onOpenUser` 的登录门（m.hupu.com/user/{euid} SSR 匿名可读；页内关注/私信各自仍有登录门）。帖子详情三处 + 评分详情 `openUser`/`openUserFromSheet` 全放行；`BoardUiState` 补 `userPageStack` + `BoardOverlays` 渲染 `UserProfilePage` 叠层（此前板块页详情连回调都没传，头像纯摆设）。
- **评论图紧凑**：`HtmlContent` 加 `compactImages`（回复/楼中楼 true）：图片宽 ≤42% 内容宽且 ≤160dp、高 ≤200dp——大尺寸表情/贴纸不再占满行宽；主楼正文图保持原样。
- **用户主页匿名可读**：`spaceUserInfo` 未登录/PC 端点失败时回退 `fetchUserHtml`+`parseUserProfile`（m.hupu.com/user/{euid} SSR 匿名可读，实测 200，内嵌 20+20 帖子/回帖）；页面去掉「请先登录」占位——恢复原有的 SSR 双 Tab 列表分支（此前 else-if 链使该分支死代码）；匿名态跳过 PC 分页请求。
- **feed 头像链路修正**：信息流帖子详情走 kudo `topic/{id}`→`TopicScreen`（非 hupuui 叠层），头像回调是 `nav.navigate("user/{username}")`；虎扑作者 `Member.username` 承载 euid（纯数字，见 `HupuBridge.toMember`），故在 `user/{username}` 路由按「全数字 = euid」分流到 `UserProfilePage`，非数字仍走 Discourse `UserScreen`。

## 板块目录换 ZonePage + 关注体系 + 综合按关注过滤

底栏「板块」由 Discourse 式类别目录（`CommunityDirectoryHost`，与虎扑结构不符）换成既有 hupuui `ZonePage`：13 大类 CateBar + 三列版块网格 + 话题流叠层 + 帖子详情三级页，原生覆盖 NBA 球队/五大联赛俱乐部/CBA/电竞/步行街子板块全树。

- **关注=收藏专区**：版块格子右上角加星标开关（`TopicCell` 增 `isFavorite/onToggleFavorite`）；`ZonePage.toggleFav` 统一写 `HupuPrefs.toggleFavoriteTopic`（原 TopicFeedOverlay 头部星标同路）；页头标题与文案改「板块/关注」，「收藏专区」虚拟 tab 改「关注」。
- **关注↔首页频道双向同步**：`toggleFavoriteTopic` 增/减收藏时同步增/删 `board:<url>` 首页频道（追加尾部）；`saveHomeChannels` 反向把 board: 频道集合写回收藏（`syncFavoriteTopicsFromChannels`，频道 label 兜底新条目名——单向派生无递归）；`clearHomeChannels` 以默认频道重同步。语义：板块关注与首页频道是同一份事实的两个入口。
- **「综合」按关注过滤**：`topics()` 的 latest 分支过 `filterLatestByFollowed`——编辑资讯 label（篮球资讯/英超/NBA…）按 `LATEST_LABEL_FAMILIES` 归内容家族，`followedLatestKeywords` 由生效频道（sport:→家族、board:→家族+板块名）+ 收藏板块名求放行集；命中家族但未关注→滤除，未识别 label 一律放行；过滤后 <5 条 fail-open 返回原列表。
- **失效信号**：`FeedViewModel.invalidateLatest()` 只作废 `_pages["latest"]`（在「综合」停留时立即 refresh），KudoApp `LaunchedEffect(homeChannelsVersion, favoriteTopicsVersion)` 触发——关注变化不动其他频道缓存。
- **ZonePage 取数回落**：`topicFeed(repo,url,page)` 统一 `topicPage`→`board()` 回落（板块页无分页：page>1 不回落直接空），覆盖首载/刷新/加载更多三处——与 `CommunityRepository.topics` board: 分支同一契约（收藏同步后「关注」tab 会出现 /all-gambia 类板块页 URL，原路径必空）。

## 官方形态对齐一期（底栏/频道条/热榜/频道管理/专区布局）

对照官方 app（com.hupu.games，userdebug 模拟器藏 su 后实测逐屏采集）做的首期对齐；保留比赛 Tab 与匿名可读，砍广告/深聊/送礼等。

- **底栏**：「板块」→「专区」，五槽顺序 `首页 | 专区 | 发布 | 比赛 | 我的` 与官方 `首页 | 专区 | + | 深聊 | 我的` 位置对齐（深聊=AI 功能不做，槽位留给比赛）。
- **首页频道条**：`热榜 | 推荐 | <我的频道…> | ☰`（`FeedFilter.LATEST.label`→推荐、`HOT.label`→热榜；尾部「类别」tab 移除——已由专区 Tab 覆盖；管理入口图标 Add→Menu）。`topics()` 的 HOT 分支不再取 /all-gambia 帖流，返回空——热榜是榜单页不走帖子流。
- **热榜频道 `HotChannelPage`**：自定义榜单页（非 FeedPage），数据源 = `boardPage("/all-gambia")` 的 SSR 内嵌块：`recommendations`→热帖榜（rank 红/橙/灰前三标色+💬数）、`hotTopics`→热门版块 3 列网格（logo+名+热度，点开 `category/{boardId}`）、`trending`→大家都在搜 FlowRow 词块（跳 `search?query=`——路由加可选 query 参数，`SearchViewModel(repo,accountId,initialQuery)` 原有第三参即接）。`parseBoardPage` 新增 `trending` 字段解析（`pageData.trending[].title`）。
- **频道管理 `HomeChannelsScreen`**：列表+上下箭头改官方式两段 3 列胶囊网格（LazyVerticalGrid）——「我的频道」=热榜/推荐固定灰丸 + 已选丸，「推荐频道」=+前缀丸点选即加；右上「编辑/完成」切换，编辑态丸右上 ✕ 徽标移除、**点选两丸换位**（高亮第一枚，官方拖拽排序的轻量等价）；写 `saveHomeChannels` 仍触发 board:↔收藏同步。
- **专区页布局**：`CateBar` 横滑条 → `CateRail` 左栏竖排导航（92dp 灰底、选中项白底+3dp 左红条、`animateScrollToItem` 跟随选中——`tabSwipeSwitch` 横滑切类逻辑不变），右侧 3 列版块网格原样；页头改「专区」。板块页 URL 回落同步补进 `topics(categoryId=)` 分支（topicPage→board()），热榜热门版块点进 `category/{boardId}` 走该路径。
- **遗留**：官方热榜另有「评分榜/话题榜」两段（评分数据接口未探）、频道页推荐/最新切换、版块页头部档案、详情回复排序——属二~四期。

## 官方形态对齐二~四期（赛事频道/版块页/赛程中心/热榜评分榜）

- **赛事频道二级页**（二期，commit 323a014）：频道头部加官方结构——近期比赛大卡 +「查看近期N场热门比赛」链接 + 圆形快捷图标行（赛程恒有、积分榜按频道配置出现）+「资讯」推荐/最新切换。赛程/积分榜点开为 `score/{game}`、`standings/{game}` 二级页（可返回、不切底栏）；「最新」态在 FeedScreen 按频道键存 `latest` 布尔并就地排序（置顶优先再按 bumpedAt 倒序），「推荐」保持服务端序。
- **版块页对齐**（三期）：`TopicFeedOverlay` 帖流首项插 `BoardProfileHeader`（logo52dp + 名称 + `countText`帖子数 + 简介 + 红色关注胶囊钮，与顶栏星标同源 `isFavorite/onToggleFavorite`）；`TopicFeedState` 增 `topicInfo` 字段承接话题页 SSR 档案（三处写站点同步填充）。帖行改官方平铺式 `FlatPostRow`（无卡片底、作者行+标题+「N回复 · N推荐」+右侧时间、底部 0.5dp 分隔线）——`FeedList` 加 `headerContent`/`flatRows` 两个参数，其他调用方不变。SSR 帖项无置顶/精华标记字段（实测 `titleFont` 恒空），置顶红标不做。红色「立即发布」FAB：未登录提示去「我的」登录，已登录提示发帖未开放（无发帖接口）。
- **详情页回复排序**：现状已覆盖官方四键——「回复 N」行内两套 SortBar 分段控件（全部/楼主 + 默认/最新/热门≈最亮/最早/最晚/楼主），无需再动。
- **赛程中心紧凑行**（四期）：`ScheduleList` 的每场大卡改为日卡内多行 `MatchRow`（左列开赛时间+赛事名、中列双队 18dp logo+队名+右对齐比分/胜方加粗、右列状态+`scoreCountText`评分人数），行间细分隔线；日期头/定位逻辑不变。大卡 `MatchCard` 保留未删（频道头部用的是另一组件 `BoardMatchCard`）。
- **热榜评分榜**（四期）：`HotChannelPage` 顶部插「评分榜」LazyRow——`HupuRepository.commonSubjects()`（score-home SSR，与比赛 Tab 虎扑评分同源）主题卡（官方 bgColorDay 底色 + 大分数 scoreColorDay + N人评分）；点击导航 `score/common`——`ScoreNavRequest.game=="common"` 特判为打开虎扑评分频道（`commonOpen=true`），复用 `score/{game}` 二级路由。
- **遗留**：评分榜卡片点击只落到评分频道首页而非具体主题详情（跨树导航代价大）；官方评分榜影视/游戏子榜切换未做；热榜话题榜以「大家都在搜」近似。

## 搜索路由改接虎扑原生页（对比报告二期落地）

对比报告（kudo-hupu-compare REPORT2）实测：`search` 路由挂的还是上游 Discourse `SearchScreen`（`ui/feed/`，依赖登录态），匿名用户点搜索即弹「请先登录 Linux.do」墙；而 `hupuui/pages/SearchPage`（bbs.hupu.com/search SSR，匿名 200、`searchRes.type=posts`）早已写好但只挂在未被路由的 hupuui `HomePage` 内部叠层里——纯死代码。

- **改法**：`KudoApp` 的 `search?query=` 路由弃用 Discourse `SearchScreen`/`SearchViewModel`，改挂 `hupuui.pages.SearchPage`；`onOpenThread` 走 `HupuIds.topicId(tid)`→`topic/{id}`（与热榜版块热帖同源路径）。`SearchPage` 新增 `initialQuery` 参数承接 `search?query=`（热榜「大家都在搜」入口）；落地页 `HistoryPanel` 增加「大家都在搜」热词块（`repo.board("/all-gambia").trending`，与热榜频道同一数据源，失败静默隐藏）。
- **未动**：`user-search/{username}` 路由仍走 Discourse `SearchViewModel`（用户帖内搜索，本就登录态功能）；`SearchScreen` 文件保留。
- **未实现**（匿名端点只返 `type:"posts"` 帖子结果，bbs SSR 无其他类型字段）：官方结果页的综合/评分/赛程/用户/问AI 多类聚合、赛事大卡、球员评分卡均不可达——官方那些结果来自 app 私有接口，不摸。官方落地页的榜单切换（话题/影视/游戏等）未接，热词已接。

## 批A-1：TopicScreen 详情底栏对齐 + 回复排序（信息流路径）

REPORT2 实测确认：信息流/搜索点帖走 `topic/{id}` → `ui/topic/TopicScreen`（Discourse 形流式楼层），此前笔记「详情回复排序已覆盖」说的是 hupuui `ThreadDetailPage`（专区版块/评分/历史叠层路径）——两条详情栈并存，信息流这条才是对比证据里缺排序的那条。

- **常驻底栏**：`TopicBottomBar` 本就存在（写回复 + 楼层索引 + 动作组三枚液态胶囊），但 `a1f985b`「去楼层条」把 `floorEnabled=false` 关掉了页码件——批A-1 重新置 true（官方「N/M页」对应物，点开展开 `FloorNavigationBar` 跳楼层）。动作组在回应钮后补「💬回复计数（`highestPostNumber-1`）+ ↗分享」对齐官方 👍💬↗。
- **回复排序 `ReplySortBar`**（TopicControls.kt）：`回复 N` + 四胶囊 `最亮/最早/最晚/楼主`，插在 OP 与首条回复之间（`items()` 拆成 `number==1` + sort 项 + `number>1` 三段，`postItem` 共享 lambda）。**仅作用于已加载楼层**：`displayPosts` 对 `posts` 做 partition 后重排/过滤——最亮=`likes` 降序、最早=楼层序（默认）、最晚=`number` 降序、楼主=`author.username==OP` 过滤；选中非默认时条下显式提示「排序仅作用于已加载楼层」，不伪装服务端排序。索引类引用（scrollToPost/navigatePost/mainVisible/previous 触发集）同步改用 `displayPosts`。
- **未对齐**：官方是服务端分页排序（翻页即整帖序），我们流式增量加载只能排已加载段——DECISIONS 见批A总结；`ThreadDetailPage` 路径未动（它已有自己的排序条）。

## 批A-2：推荐流 ♥50 高频重复——确认为服务端真实值，不改解析

REPORT2 观察「推荐流各卡 ♥50 恒定、回复数各异」。实测当前 m.hupu.com 首页 SSR（`__NEXT_DATA__.props.pageProps.res`，30 条）：`lights` 字段字符串有 18/30 条恰为 `'50'`，其余 48/45/33/37/41/19/46/39 等——分布与设备观察（50 高频 + 偶见 42/43/49）完全吻合。`parseMobileHome` 用 `toIntOrNull ?: 0`，缺失/空串→0 而非 50，`compactNumber` 也不会把 0 显示成 50——解析链无默认值污染。**结论：`lights` 在移动站 SSR 中被服务端截顶在 50（热帖真实亮数远高于此），50 是服务端下发的真实字段值，不改数据、不显示加号伪造。** 若官方 app 显示更高数值，走的是私有接口真实计数，不在可达范围内。

## 批A-3：赛程展开态返回键——复现定性为「层级被跳过」而非栈损坏

实测复现链（redroid，dump+截图取证）：频道首页 → 点快捷「赛程」→ `score/{game}` 二级页（BoardContent 板块视图）→ 点「查看全部赛程」→ 就地展开紧凑全赛程 → **返回** → dump 显示直接落回频道首页资讯流（非赛程页）。定性：`showAllMatches` 是 `BoardUiState` 的就地展开态（非导航层），返回键直接弹出整个 `score/{game}` 二级页——栈本身没坏，但用户钻入的「全部赛程」层级被返回跳过，随后按旧坐标点「积分榜」自然点到资讯流帖行。**修法**：`BoardContent` 内加 `BackHandler(enabled = showAllMatches) { showAllMatches = false }`——返回先收起展开态（收起时 LaunchedEffect 已回顶到「近期比赛」），再按返回才退页；与同页 openedThread/standingsOpen/embedPage overlay 各自的返回拦截同一约定。证据存 `kudo-hupu-compare/crash3/a3_*.xml|png`。

## 批A-4：频道快捷行层级——非 WebView/自绘，补合并语义与 testTag

REPORT2 记录「赛程/积分榜快捷入口 dump 中 bounds=[0,0][0,0]」。复核源码+新 dump（`crash3/a3_nba3.xml`）：`SportChannelHeader` 是纯 Compose 原生布局，`ShortcutIcon`=Column(clickable)+Icon+Text——语义本来就产生，只是①Text 节点的 bounds 是字形小框而非整列触控区、②LazyColumn 里部分可见/刚出视口的 item 会报 [0,0][0,0]（Compose 对离屏已组合项的固有行为），不是层级缺失。**修法**：ShortcutIcon 列改 `clickable(role=Button) + semantics(mergeDescendants){contentDescription=label} + testTag("shortcut-赛程|积分榜")`——uiautomator 得到单个可点节点、bounds=整列触控区；「查看近期N场」行与「推荐/最新」chip 同步补 role/merge/testTag。定位策略：优先 `testTag`/`contentDescription` 文本匹配（如 `shortcut-积分榜`），坐标兜底用整列 bounds 中心。

## 批B-1：联赛数据网络层 HupuDataApi/HupuDataParser（私有 mobileapi 匿名只读）

批B 获准使用私有赛程接口读数（仅限本单）。探测结论：**games.mobileapi.hupu.com 的 basketballapi 系列与 match-api.hupu.com 的 matchallapi 系列不需要 §9 签名**——官方数据 H5 页（offline-download.hupu.com/online/prod/300003 系列）同域同参匿名调用，参数形态全部照抄其 JS 包；`basketball/public/` 老路由才要求旧签（实测「签名验证不正确」），一律不碰。

- **新文件**：`HupuDataApi`（两个 BASE + 公共参数封装，HupuCache 内存缓存，只暴露 fetch→String?）与 `HupuDataParser`（JSON→模型，失败归零不编造）。签名逻辑零行——探针证实可达即不实现，避免维护死代码。
- **已验证端点→字段映射**：getDataTabList→tabId/tabName/tabLinkType/tabUrl；teamStandingList→rankTypeListMap(E,W)+divRankTypeListMap(6分区)+competitionSeasonStageType；switchableRankSeasonList→seasonData(season/seasonType/select)，rankType 枚举 NBA_TEAM_RANK/NBA_PLAYER_RANK（clientId=106819853 为官方 H5 实参）；teamSeasonStatsAllRank→39 项各 top5；playerSeasonRank→dimensionList(基础/进阶)→itemList→rankInfoList(top5)；draftYearConfig→年份表、players/draft→draftMap(按轮)；allInjuryList→playerInjuryDTOList+teamInjuryPlayerListMap；teamMatchQuarterStats→home/awayQuarterScore 数组；matchallapi/oddsScheduleList→dayGameData（复用 HupuMatchParser 行结构，含 competitionName/subscribeInfo）。
- **模型扩展**：`HupuMatch` 补 `businessType`(basketball=可拉单节比分)/`competitionName`/`subscribe`；新增 HupuDataTab/HupuSeasonOption/HupuRankCategory/HupuStandingsBoard/HupuPlayerRankBoard/HupuDraftBoard/HupuInjury/HupuQuarterScore。
- **未接**：`basketball/public/` 签名路由族；singleMatch 的订阅/预约写接口（subscribeInfo 仅读展示）；WNBA——leagueTypeName invalid，端点不认；足球/电竞榜走各自 matchallapi 数据 tab（H5 或后续）。

## 批B-2：赛程行预约占位 + 详情记分板单节比分

- **预约（展示占位，不可点）**：实测 `getScheduleListByTagForH5` 行内 `subscribeInfo`——NOTSTARTED 场 `showSubscribe=true`、完赛场 false。`HupuMatch.subscribe` 映射 `showSubscribe/didSubscribe/subScribeTitle/subScribeDesc`；`MatchRow` 右列在 `show=true` 时渲染主题色「预约」胶囊（纯展示）。订阅接口为登录态写操作，**未接**，不伪造已约态。
- **评分人数**：赛程行 `scoreCountText`（"2.0万人评分"）此前已映射渲染，本批仅复核，无改动。
- **单节比分**：`MatchScoreboard` 加 `quarterMatchId`（ScorePage 在 `businessType=="basketball"` 时传 matchId）；`ScoreboardBanner` 在已有比分（开赛）时 LaunchedEffect 拉 `repo.quarterStats`，直播态随比分变化重拉，渲染 `QuarterTable`（节次表头 1-4 + OT/OT2… + 总分列，胜方加粗）。接口空/非篮球比赛不显示。

## 批B-3：赛程中心数据 tab（官方 getDataTabList 轨道）+ 积分榜升级

- **tab 轨道**：`ScorePage` 频道条下新增 `LeagueDataTabBar`——`repo.leagueDataTabs(selectedGame)` 返回 >1 个 tab 才显示（nba 9 个：全球|赛程|球队榜|球员榜|选秀|伤病|对阵图|荣誉榜|历史榜；cba 7；lpl 3；epl 仅 1 不显示）。官方 tab 序原样，不编类别。
- **落地分派**（`ScoreDataPages.kt`）：`linkType=schedule`→主视图（原 BoardContent/ScheduleList）；`oddsSchedule`→`GlobalScheduleView`（oddsScheduleList，code 从 tabUrl 解出 basketballLottery，复用 ScheduleList 紧凑行）；h5 且 url 命中 `300003/<league>/(team|player|draft|injury).html`→原生页（同一批 basketballapi 数据）；其余 h5→`EmbedWebPage` 盖入（选中态留赛程，关页即回）。nba 对阵图/荣誉榜/历史榜、cba 国内球员/新秀/日榜、lpl 两页均走 embed。
- **积分榜（球队榜）`StandingsBoardView`**：左栏类别 = rankTypeListMap(东/西)+divRankTypeListMap(六分区)+「技术统计」(teamSeasonStatsAllRank 39 项各 top5，单项一个分组表)；顶部赛季 chip 条 = switchableRankSeasonList（rankType 枚举 `${league大写}_TEAM_RANK`，nba/cba 均实测）。**三处共用**：数据 tab 球队榜、`standings/{game}` 路由页（NBA 分支）、板块内积分榜盖入（`LeagueStandingsOverlay`，NBA 分支替换原 SSR 版——SSR nbaStandings 仅足球/回退路径仍用）。
- **球员榜 `PlayerRankView`**：赛季条（`*_PLAYER_RANK`）+ 维度条（基础/进阶）+ 每统计项 top5 卡。
- **选秀 `DraftView`**：年份条（draftYearConfig）+ 按轮分组顺位表（draftMap）。
- **伤病 `InjuryView`**：按队分组列表（playerInjuryDTOList 平铺→groupBy teamName）。
- **未接**：h5-only 榜单无匿名原生数据（对阵图/荣誉榜/历史榜/日榜/国内球员/新秀），按规则内嵌官方 H5；单维度全量球员榜（playerSeasonRank 只给 top5）未见可用端点。

## 批B-4：频道快捷行 2→5（数据 tab 深链）

官方 NBA 频道快捷位 = 赛程/数据/主队/战术百科/2K手游（主队要登录、战术百科无匿名数据、2K手游是联运广告位——三者均不编）。虎Do 诚实 5 位 = **赛程/积分榜/球员榜/选秀/伤病**，全部由匿名原生接口支撑。

- **入口规则**（`SportChannelHeader`）：`repository.leagueDataTabs(sportKey)` 解出频道实际拥有的原生 kind（nativeDataTabKind 命中 team/player/draft/injury），有则渲染对应 ShortcutIcon——官方没给该 tab 的项目自然不出现（如 cba 无「选秀」tab 时该行是 4 项，不硬凑 5）；无数据轨道的频道回退旧形态（赛程 + 有积分榜才加）。
- **深链**：`score/{game}?dtab={kind}` 路由参数 → `ScoreNavRequest.dataTab` → `pendingDataTab` 由 leagueTabs 加载效应兑现——kind 命中 tab 则选中（h5-only 则直接开 EmbedWebPage），未命中回落默认赛程，不伪造入口。
- **图标**：积分榜=BarChart / 球员榜=Leaderboard / 选秀=EmojiEvents / 伤病=Healing（material-icons-extended 现成）。
- `CommunityRepository.leagueDataTabs` 透传（与 sportMatches/boardPage 同惯例）。

## 批C-1：视频/图片/个人信息/排布 现状审计（先审后改）

对照官方 8.2.66.10232（screenshots2/ dumps2/）与 PRODUCT_SCOPE.md，逐项审计结论：

### 视频
| 位置 | 现状 | 结论 |
|---|---|---|
| hupuui 详情主楼（`ThreadDetailPage`→`HupuVideoPlayer`） | ExoPlayer：播/暂停/进度条/倍速/全屏（自管旋转+沉浸）/黑边裁剪/抓帧探针/黑匣子 | ✅ 达标 |
| feed/搜索路径详情（`topic/{id}`→`TopicScreen`→`RichContent`） | `HupuBridge.toTopicDetail` 把 `thread.video` 前置 `<video src poster>` → `parseArticle` 映射 `ArticleBlock.Embed` → `OneboxPreview` 卡 → `openLink` → WebView/外链 | ❌ 非内嵌播放：跳走即停、无暂停/进度、形态不对 |
| 楼层/楼中楼视频 | 回复 contentHtml 含 `<video>` 时：feed 路径同上一张 Embed 卡；hupuui `HtmlContent` 只渲染文本+图片 → 静默丢弃 | ❌ feed 仅卡片；hupuui 丢弃 |
| 信息流卡 | SSR res[] 无 video 字段 → 无播放标识 | ⚠️ 无数据可标，不编 |

### 图片
| 位置 | 现状 | 结论 |
|---|---|---|
| feed 正文图（`ArticleImage`） | 加载圈 + 「加载失败点击重试」+ 点击重试 + ImageGallery 缩放查看 | ✅ |
| hupuui 正文图（`ThreadDetailHtml`） | SubcomposeAsyncImage + ImageErrorBox + 自动重试（`isRetryableImageError`）+ 全屏查看 | ✅ |
| 头像/缩略图/队徽（64 处裸 `AsyncImage`） | 失败 = 透明空白洞，无可辨识空态 | ⚠️ 头像应给默认人形兜底；列表小图给灰底占位 |
| 大图查看 | feed `ImageGallery` + hupuui 全屏查看均可进退 | ✅ |

### 个人信息（「我的」页）
| 现状 | 结论 |
|---|---|
| 未登录：登录入口卡 + 全部设置组（官方是整页登录墙——我方优势，保留） | ✅ |
| 已登录：头像+昵称+「已登录」→ 点开 `UserProfilePage` 全量信息（Lv徽章/粉丝/关注/发帖回帖/等级进度） | ⚠️ 卡片本身不含 粉丝/关注/Lv（官方我的页有这些计数行）；需点开二层页 |

### 排布（字体/样式/显示方式）
| 页面 | 差距 |
|---|---|
| 首页推荐流卡 | 官方平卡：头像+昵称+日期行 → 标题 → **全宽大图/三图行** → 灰底神回复预览卡 → 版块徽标+👍💬↗；我方圆角卡：标题+右侧 100×76 缩略图 + ♥💬作者·时间 meta。**数据约束**：SSR res[] 无 author/header/神回复字段（只有 badge/label/lights/replies/source/tid/title/type/url）→ 作者行与神回复卡无法复刻不编；可复刻 = 平卡+标题(16sp/500)+大图或三图行+版块chip+👍💬 |
| 详情页 | 标题/正文字号与官方接近（官方标题≈17sp bold、正文≈16sp 宽松行距）；作者行徽章/版块蓝链/属地已有 | 基本对齐，微调 |
| 热榜/频道/专区/版块/赛程 | 批A/B 已对齐 | — |

### 实施计划
- C2：feed 路径 `<video>` → `ArticleBlock.Video` → 内嵌 `VideoPlayer`（复用 `hupuui/player`：VideoHost+PlayerStage，全屏走自包含 Dialog）
- C3：头像兜底人形 + 列表小图灰底空态（热点调用点：楼层头像/详情作者/我的卡/版块标/搜索图）
- C4：已登录账号卡补 关注/粉丝/Lv 行（`parseSpaceUserInfo` 同源接口）
- C5：`FeedItem` 官方平卡复刻（单图全宽/三图行需 `HupuThread` 补 `covers`+`picType`，解析器保留 source[] 全量）

## 批C-2：信息流详情路径内嵌视频播放（ArticleBlock.Video + RichVideoBlock）

审计发现的真问题比预期更深：`HupuBridge.toTopicDetail` 把 `thread.video` 前置为 `<p><video src poster>`，而 `parseArticle` 的 `<p>` 分支只拆 `img` —— **视频在 feed 路径被整段吞掉**（连 Embed 卡都不出）。裸 `<video>`（非 <p> 包裹）才走到 Embed 卡 → openLink → WebView 播放（跳走即停、无控件）。

- **`ArticleBlock.Video(url, poster)`**：`"video"` normalName 直出；`<p>` 分支补 `element.select("video")` 与 img 同列拆出 walk（保序：video 在其所在段落位置渲染）。
- **`RichVideoBlock`**（`ui/topic/RichVideoPlayer.kt`）：复用 `hupuui/player` 三件套——`VideoHost`（块内 remember，滚出 LazyItem 即 release，不为低频视频帖维护页级映射）、`VideoPlayer`（封面+播放键→ExoPlayer 起播，控件含暂停/进度/倍速/全屏钮）、`FullscreenVideo`（自管旋转+沉浸式）。全屏容器用 `Dialog(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)`——TopicScreen 是 NavHost destination 无页级 overlay 位，Dialog 即满屏；返回键走 `BackHandler`+Dialog dismiss 同路径退出。
- iframe/audio 仍走 Embed 卡（无内嵌播放器语义，不变）。
- `UserSignature` exhaustive when 补 `is ArticleBlock.Video -> Unit`（签名里不出视频）。
- 单测：`hupuVideoBlockIsExtractedFromParagraphWrapperAndBareElement` + `videoBlockKeepsTextOrderAndIframeStaysAnEmbedCard` 覆盖 <p> 包裹/裸元素/保序/iframe 回退。
- 已知限制：滚出视口释放后滚回从头起播（不做进度保活）；hupuui HtmlContent 楼层视频仍为丢弃态（回复视频罕见，未接）。

## 批C-3：图片失败兜底（HupuAvatarImage/HupuIconImage/HupuThumbImage）

`hupuui` 下 64 处裸 `AsyncImage` 失败 = 透明空白洞（头像直接消失）。新增 `components/FallbackImages.kt` 三个 `SubcomposeAsyncImage` 包装（签名与 `AsyncImage` 对齐，调用点只换名）：error 槽渲「灰底 + 语义图标」——头像→人形、版块标/队徽→球标、列表缩略图→图片标（官方同款灰底占位语义）。

- **已换 43 处热点**：楼层/楼中楼/详情作者/签名引用头像、我的页账号卡、私信与消息列表头像、用户主页头图与粉丝头像、黑名单页头像、信息流作者角标；赛程行/记分板/板块头/专区网格/选版块/发帖选版/比赛详情队标与版块标；资讯卡/搜索卡/信息流缩略图。
- **保留不换**：`UserProfilePage` 封面缩略图（刻意 `onError→coverOk=false` 整块撤除，注释 1.190 已有取舍）、正文大图（已有 ImageErrorBox+自动重试）、帖子正文 `ArticleImage`（feed 路径已有重试+提示）、贴纸/表情（独立加载链）。
- 失败图标取 `fillMaxSize(0.55f)` 居中控件，不动调用方的 clip/size modifier。

## 批C-4：我的页账号卡补个人信息（关注/粉丝/Lv/入站天数）

已登录账号卡原只有 头像+昵称+「已登录」，个人信息要再点一层才见。现登录后 `LaunchedEffect` 拉 `repo.spaceUserInfo(prof.euid)`（先读 `cachedUserInfo` 即渲、静默失败落回「已登录」）：昵称右侧渲染 `levelDesc` 胶囊、副行改「关注 N · 粉丝 N · 加入虎扑N天」——与官方「我的」页的计数行同构（官方另有点击跳转计数页，我们保持整卡进 `UserProfilePage`，不拆三个入口）。未登录卡形态不变（优势保留）。

## 批C-5：信息流大卡官方平卡复刻（FeedItem 重写 + res[] source[] 全量）

对照 `hupu_04` 官方信息流：`FeedItem` 从「圆角卡：标题+右缩略图+♥💬作者·时间」重写为官方式平卡——

- **平卡全宽**：去圆角/卡片间距改 10dp 页面底色缝；FeedList 大卡模式 `contentPadding` 左右清 0（`flatRows` 版块行不受影响，headerContent 只在 flatRows 用到）。
- **自上而下**：作者行（avatar 28dp+名 13sp+时间 11sp，**仅解析器带 author 的流渲染**，SSR res[] 无作者字段的流不出现）→ 标题 16sp/Medium/22sp 行高/2 行 → desc → **图区**：`covers≥3` 三图行（4:3、4dp 缝、圆角 6dp）、1~2 图全宽 16:9（圆角 8dp）→ 底行：左版块 chip（灰底胶囊 11sp）+ 右 👍💬（亮数金）。
- **数据**：`HupuThread.covers`（新字段）=`res[].source[]` 全量（`cover` 仍取首图保持旧契约）；FeedItem 无 covers 时回落 `cover` 单图——话题流/搜索/资讯等只带单图的流仍出图。
- **官方有而我们没有且编不出来的**：作者行（res[] 流无）、灰底神回复预览卡（无字段）、↗转发计数（无字段）——三处均不渲染占位，在审计节已记。
- `MetaRow`（旧左图右文 meta）随重写删除；`FlatPostRow`/版块资讯行不动。

## 批D-1：首页推荐流平卡复刻（TopicCard 重写，真首页路径）

批C-5 的 FeedItem 平卡只落在 hupuui FeedList 路径；首页推荐流真实组件是 `ui/feed/FeedScreen` → `ui/components/Common.kt#TopicCard`（圆角卡+右缩略图），验收证实平卡未落首页。本项将 TopicCard 重写为官方平卡：

- **全宽无圆角**：去 18dp 圆角+内 margin 样式；`FeedScreen` 列表 contentPadding 左右 8→0、条目缝 8→10dp（sport-header 自带 horizontal=12 不受影响；`TopicListStyle.TITLE` 传统行模式不变）。搜索页(自带 12dp 容器)/个人页(1dp 缝)两处复用点保持兼容。
- **结构**：作者行（avatar 28dp+名 13sp/Medium+右对齐相对时间 11sp，author==null 的流不渲）→ 标题 16sp/Medium/22sp lh/2行 → 摘要 13sp/18sp/1行 → 图区：`images≥3` 三图行 4:3/4dp缝，否则单图全宽 16:9 → 底行 版块chip(`CategoryInline` 保「分类图标：」语义)+右对齐 👍💬（views 官方卡不显示，去除）。
- **数据**：`Topic.images`（参数表末位，位置构造兼容）=`HupuThread.covers`/`HupuBoardNews` 单图；空则回落 `mediaUrl(image)`。
- **契约保留**（androidTest 既有）：testTag `topic-image-{id}`(index0)/`-{i}`，失败出「加载失败，点击重试」+`imageLoadErrorText` 诊断行(在重试行下方)，点击重试消费事件不进详情；加载成功按 intrinsicSize 渲 `ImageAspectBadge`(宽图/长图)；删死代码 `TopicThumbnail`。
- 未变：置顶 pin 图标、未读徽章、标签胶囊（去重版块名后仍渲）。

## 批D-2：内嵌视频可定位语义 + 挂载条件单测

Muse 真机验证缺定位手段（内嵌 <video> 帖子难找）。本次只加语义与测试，不改播放行为：

- **`VideoPlayer` 帧盒**（feed 路径与 hupuui 详情页共用）：`testTag("hupu-video-player")` + semantics `contentDescription="视频"`、`stateDescription=待播放/已起播`（读 `host.started` 可观测态）。播放/暂停/全屏钮原有 cd「播放」「暂停」「全屏/退出全屏」不变。
- **`RichVideoBlock`**（feed 详情路径专属）：容器 `testTag("article-video")` 区分于详情页直接挂载。
- **单测** `videoMountsOnlyWhenHttpSourceExists`：挂载条件= src（或 `<source>` 兜底）非空且 `webUrl` 解析为 http(s)——空 src/无 src/javascript: 不出 Video 块；`<source>` 兜底与 `<p>` 包裹同条件覆盖。外链视频（微博等文本链接）仍走链接，不冒充内嵌。

## 批D-fix：首页推荐流空列根因修复（脏 m-home 缓存 + 下拉不透传 forceNetwork）

真机 A/B：批D包首页推荐「暂时没有可显示话题」+ 下拉仍空，批C包同时段正常。逐链排查：EmptyState 仅在 `topics=[] && error==null` 时出现 → `topics()` 静默返回空页；批D 仅改 UI 与 Topic 末位字段，数据生产者零改动 → 指向**既有缺陷**而非批D回归：

- **根因**：`fetchMobileHomeHtml` 把「含 `__NEXT_DATA__`」的响应直接写入 `HupuCache["m-home"]`（列表 TTL 10min、带磁盘持久化）。首拉若命中含 NEXT_DATA 但 `res` 为空/变体的页面（WAF 壳、A/B、跳转页），脏 HTML 缓存 10min；且 `FeedViewModel.refresh → repository.topics → hupu.mobileHome()` 链**从未下传 refresh**，下拉刷新永远读缓存——首页被脏缓存饿死。
- **修复（三层）**：
  1. `HupuApi.fetchMobileHomeHtml` 缓存门槛加 `"res"` 必须出现——空壳页不再入库；
  2. `CommunityRepository.topics(..., refresh)` 末位参数下传到全部 hupu.* 调用；`FeedViewModel.refresh(manual=true)`（下拉）传 `refresh=manual` → forceNetwork 绕缓存，首拉/探针仍走缓存；
  3. `topics()` 的 latest 分支自愈：`mobileHome` 空且非强网时 `forceNetwork` 重拉一次再判空。
- 不伪造数据：重拉仍空则返回空（EmptyState 保留），不报假错误。
- 未接：`HupuRepository` 是 final 具体类（构造可注入但不可 mock 子类），自愈路径无 JVM 单测；board/topicPage 等同样受益 refresh 下传，但各自的「空壳缓存」未做同类入库门槛（`res` 门槛只加在 m-home，其他端点结构不同不泛化）。

## 批E-1：真实样本全链验证内嵌视频（结论：解析+桥接+挂载判定全通）

Muse 找不到内嵌视频帖是「样本难找」而非链路不通。用 2026-10-26 抓到的真实帖子（`/tmp/b-lol.html` 列表中 `hasVideo:true` → tid=643103358，详情 SSR PC 页 130,857B）做端到端证据链：

- **样本**：`detail.thread.hasVideo=true`、`video=v.hoopchina.com.cn/.../wz_transcode.mp4?auth_key=…`、`videoCover=i5.hoopchina.com.cn/...jpg`；`content` 正文是 slate HTML（视频是独立字段，不在正文 HTML 里）；回复楼 14 条含 0 个 `<video>`（8 条含 `<img>`）。
- **主链路两条都通**：
  1. hupuui `ThreadDetailPage` → `ThreadDetailHeader`：`thread.hasVideo && video.isNotBlank()` → 直接挂 `VideoPlayer`（ExoPlayer，封面+起播+全屏已齐）。
  2. feed `TopicScreen`：`toTopicDetail()` 把 `thread.video`/`videoCover` 前置成 `<p><video src poster>` → `parseArticle` 出 `ArticleBlock.Video`（`webUrl` 过滤空/非 http(s)）→ `RichVideoBlock`（`article-video` tag）→ 同一 `VideoPlayer`。
- **持久化单测** `HupuDataParserTest.videoThread_realSample_flowsThroughBridgeToNativePlayerMount`：fixture 用样本真实字段值，断言 `parseThreadDetail` 出 hasVideo/video/videoCover、`toTopicDetail().posts[0].cooked` 经 `parseArticle` 出恰好 1 个 Video 块且 url/poster 与源站一致、正文 TextBlock 保留。
- **HtmlContent 楼层丢弃结论**：批C 记录的「hupuui HtmlContent 不渲 `<video>`」只影响回复楼。真实样本回复零 `<video>`，主链路（主楼视频）不受影响，维持不修复（楼层视频为罕见边缘，修复收益为负——HtmlContent 是纯文本+图渲染器，加视频渲染需为每楼层持播放器生命周期）。
- **外链视频不变**：`<a>` 文本链接仍走 `openLink`，不出 Video 块；iframe/audio 仍 Embed 卡。

## 批E-2：图片兜底可测试化（纯函数映射 + hupu-fallback-* 定位标签）

`FallbackImages.kt` 重构：兜底分支「渲哪个图标」收敛为纯函数 `fallbackIcon(HupuImageFallback)`（Avatar→Person / Icon→SportsBasketball / Thumb→Image，三者互异）与 `fallbackTag`（`hupu-fallback-avatar|icon|thumb`）；`ImageFallbackBox` 图标挂对应 testTag，uiautomator 可判兜底态。

- **JVM 单测** `FallbackImagesTest`：三类→三互异图标的映射、tag 命名、enum 穷尽（新增种类强制补映射）。`ImageVector`/`materialIcon` 构造是纯 Kotlin（PathBuilder 不触 android.*），可跑 JVM。coil `SubcomposeAsyncImage` 何时进 error 槽是库行为，不在 JVM 覆盖。
- **androidTest** `FallbackImagesUiTest`：三类组件喂 `http://127.0.0.1:1/unreachable.png`（连接拒绝、离线可复现）→ 断言对应 `hupu-fallback-*` 节点 `assertIsDisplayed`——真机侧证明「error 回调确实渲了兜底分支而非透明洞」。
- **刻意取舍保留**：`UserProfilePage` 封面缩略图失败仍「整块撤除」（`onError→coverOk=false`，注释 1.190）——84×60 占位洞会压窄标题换行，撤块优于空洞；该项不接兜底组件，笔记留痕为设计决定而非缺陷。

## 批E-3：我的页登录账号卡复核 + 缺字段降级

逐链核查结论（源码级，未登录真机不可验）：

- **无空指针面**：`spaceUserInfo` 双源（PC getUserInfo→移动 SSR 回落）全链 `opt*`+try/catch 出 null；`fetchSpaceApi`/`fetchUserHtml` 网络异常吞为 null，`RequestThrottle.acquire` 走 `delay`（挂起、取消安全）；`HupuCache.get` 磁盘读 try/catch。`HupuUserProfile` 字段全非空默认值。
- **点开卡片** → `userPageStack.add(euid)` → `UserProfilePage`：null profile → `failed` 态（可重试），不崩。
- **补三处缺字段降级**（不改登录/未登录行为）：
  1. `prof.euid` 为空串（cookie 缺字段时 `optString`→""）→ 跳过 `euid=` 无效请求，点击改为 toast「账号资料缺失，请重新登录」而非打开注定失败的 UserProfilePage；
  2. `prof.name` 空 → 显示占位「虎扑用户」；
  3. `ownInfo` 四字段（levelDesc/following/followers/regTimeStr）全空 → 统计行回落「已登录」——缺字段不再冒充「关注 0 · 粉丝 0」真实计数（0/0 真实账号的 regTimeStr 非空，不受影响）。

## 批F-1：球员榜全量（playerSingleDimensionSeasonRank 分页盖入页）

批B 的 playerSeasonRank 每项只给 top5；其 `allUrl` 字段指向 `games.mobileapi.hupu.com/template/index?model=basketball/rank/index#/playerRank`——抓该页 JS 实参找到背后的全量端点 `basketballapi/playerSingleDimensionSeasonRank`（参数：competitionLeagueType+competitionType+rankType+season+competitionStageType+playerType=normal+pageNo）。匿名实测（2026-10-26）：20 行/页，ptsAvg 连续取到 rank~300+，空页即止；官方 rank/index 页 scroll 只翻到 pageNo 5（~100 行），酷DO 拉到空页为真正全量。

- `HupuDataApi.fetchPlayerSingleDimRank(league, rankType, season, stageType, pageNo)`；`HupuDataParser.parsePlayerRankPage` → `HupuRankItem`（rankInfoList 行解析与整版共用 `rankEntry`）；空页返回空 leaders 区分「榜尾」与「网络失败」（非 JSON/失败才 null）。
- `HupuRankItem` 增 `hasMore`（官方下发，top5 是否有全量可展开）。
- `PlayerRankView`：统计项卡底部 `hasMore` 时出「查看完整榜单 ›」→ `PlayerRankFullOverlay`（LeagueStandingsOverlay 同款滑入骨架：SecondaryPage 计数+PredictiveBack+CenteredTopBar「{项}榜」）→ `PlayerRankFullList` pageNo 续载（derivedStateOf 滚动距底 5 项触发），footer 三态（转圈/失败重试/「已加载全部 N 人」）。
- 单测 `playerRankPage_parsesSingleDimensionPageAndEmptyPageMeansListEnd`：真实字段 fixture + teamShortName→teamName 回落 + 空页=榜尾 + 非 JSON/false→null。

## 批F-2：多赛事赛程详情链审计 + 预约文案可点

匿名实测（2026-10-26，getScheduleListByTagForH5 + getDataTabList）逐项结论：

- **缺 scoreItemKey 的行全部是 NOTSTARTED/CANCELLED**（global.json 314 场：COMPLETED/INPROGRESS/PENDING 全带 key，NOTSTARTED 236 行 scoreItemKey=null）——评分详情页是赛后生成的，服务端对未开赛本无此页，非端点缺失。官方未开赛行的唯一链接是 `liveRoomLink`（huputiyu://bball/live=直播间，**直播属排除项**不接）。按状态分类：缺 key=未开赛/取消（设计使然）、无端点（前瞻详情不存在）、需登录（订阅写接口不碰）。
- **WNBA**：getDataTabList?enType=wnba 现返回 3 tab（赛程=原生 schedule/季后赛对阵图=h5 WebView/资讯=h5 WebView）；businessId=wnba 赛程 102 场、100 场已赛带 key → 详情链已通，批B「不认」是当年 tab 空，现已可用，无需改代码。
- **带 key 行三 bizType 均可点**：basketball_match / common_sports_first（土超等 48 行，getSelfByBizKey 匿名实测 200）/ football_match（epl 已赛 101 行带 key 直接开，无 key 行走 footballScoreNo 换取链）。
- **可改进点**：预约 chip（批B-2 占位）现可点——弹官方订阅文案「预约提醒：{subScribeDesc/Title}」（真实字段只读展示，HuzaiToast，不伪造订阅态、不写接口）。
- **未接**：CANCELLED 与个别 COMPLETED 无 key 行（网球 14 场/乒羽 1 场）——服务端同样无评分页，只能展示，记入缺口。

## 批F-3：详情页楼层区对照补齐（相关推荐/客户端标签/隐藏占位）

对照官方详情 JSON（detail 根键：breadCrumb/desc/hot/isRecommended/keywords/latest/lights/replies/thread/title/user/visibleRange）逐项核对：

- **相关推荐/最新发帖（新接）**：`detail.hot`/`detail.latest` 为同区帖摘要数组（tid/title/cover/lights/replies/url/topic，与 threadFrom 同 schema）——此前未解析未渲。现 `HupuThreadDetail.hot/latest` 落模型；详情 LazyColumn 主楼与回复数行之间插「相关推荐」「最新发帖」卡（各取前 4 行：标题 1 行 + 点亮·回复计数，formatCount）。
- **嵌套详情页**：点推荐行 → `NestedThreadOverlay` 自包含盖入页（数据/翻页/排序/楼层缓存全自理——宿主 detail 是单槽 tid 键控状态，共用回调链会互相污染）；逐层叠加、返回逐层弹出（后注册的 PredictiveBackHandler 天然优先），`pageZIndex` 参数让嵌套层（40f+i）压过宿主楼层 sheet（3f+depth），递归打开下级推荐免费获得。
- **客户端标签（新接）**：reply.client（ANDROID/IPHONE）→ 元信息行「来自Android/来自iPhone」。
- **隐藏楼层（新接）**：reply isDelete/isHidden/hidePost>0/isAudit 任一 → `hidden`，占位「该回复已被删除或隐藏」不渲正文。
- **已覆盖无需改**：图片查看（ImageViewer zIndex10）、点亮/回复/楼中楼/排序/只看楼主、引用楼、投票、锁定态、用户主页。
- **仍记缺口（无字段不伪造）**：神回复预览卡（detail 根无对应字段）、转发数（thread 无 share/repost 字段）、楼层序号角标（官方有 #N，我方无——reply JSON 无楼层序号字段，翻页合并后更无法推导，不接）。

测试：ThreadDetailRelatedTest 4 例（hot/latest 解析与空回落、client/hidden 各 flag）。

## 批F-4：热榜对照补齐（官方 tag 热搜榜 + tag 聚合页）

匿名实测官方 `m.hupu.com/hot`（2026-10-26）= 微博式**热搜 tag 榜**：`pageProps.res` 30 条/totalElements≈50，行字段 rank/tagName/heat/icon/competitionType/tagUpdateDesc；官方页 JS（hot-*.js）渲染形态=排名（top3 着色）+话题词+🔥热度，点击 `huputiyu://bbs/topicTag?tagId=`，页尾 `huputiyu://search/hotTagList`「前往虎扑APP获取更多精彩内容」——**后 ~20 条为 App 专属，匿名上限=首屏 30 条**（探测 /api/v2/*/hotTagList 均 400 未知错误）。酷DO 原热榜频道只有评分榜/热帖榜/热门版块/大家都在搜，**缺官方主形态热搜榜**。

- 接：`hotTags()`（fetchMobileHtml("/hot")→parseHotTags）→ HotChannelPage 首段「虎扑热榜」官方同形态行（rank 着色 + tagName + 🔥formatCount(heat)）。
- 接：tag 点按 → `TagFeedOverlay` 全屏盖入页（BackHandler 收起）：`tagPage()`=m.hupu.com/tag/{id} SSR topicTopInfo（banner/bannerRgb/tNum/pv 头）+ topicThreadList 首屏 20 帖；滚动底 4 项触发 `tagThreads()`=`/api/v2/bbs/tagThreads?tagId=&tagType=（空=非播客）&page=N&pageSize=20`（官方页 JS 同款，匿名实测 200）续载，tid 去重合并；帖行=头像+昵称+时间/标题/摘要2行/右小图/👍💬↗ 计数，点击 `tid.toIntOrNull` 走既有 openTopic 详情链。
- 网络层新增 `fetchMobileHtml(path)`（m.hupu.com+MOBILE_UA+NEXT_DATA 门槛+`m:` 前缀缓存键，与 bbs.hupu.com 的 fetchHtml/user 键不冲突）与 `getMobileJson`（同源 JSON，不缓存）。
- 缺口（如实）：热榜 31-50 名（`search/hotTagList` 为 App 内路由，无匿名 HTTP 端点）；`aggregationType/competitionType` 分类筛选（官方页未提供 tab，字段已透传未渲）；tag 关注数交互（写接口不碰）。

测试：HotRankParserTest 5 例（hot 解析与空回落、tagPage 头部+帖行+picList、tagThreads 分页 code 门槛与非 JSON）。

## 批G-1：视频帖列表可达性（hasVideo 全链标识）

闭环路径核实：批 E 已证详情两条链都挂播放器（hupuui `ThreadDetailHeader` hasVideo&&video→VideoPlayer；feed `toTopicDetail` 前置 `<video>`→RichVideoBlock），真机找不到的根源是**列表无视频标识**。真实样本 `/tmp/b-lol.html`：46 帖中 9 帖 `hasVideo:true`（常见量），官方 SSR 标题行挂 `iconshipin2` 视频角标——官方形态实证，本批对齐而非自创。

- `Topic` 末位加 `hasVideo`（位置参数安全）；`toTopic()` 桥入。
- `FeedUi` 新增 `VideoPlayBadge`（图区居中 ▶ 黑透圆钮，`testTag("video-badge")`+cd「视频」供 uiautomator 定位）与 `VideoTagChip`（无图帖「视频」chip，`testTag("video-tag")`）。
- 三处列表接标识：hupuui `FeedItem`（≥3 图首格/单图叠 ▶，无图底行 chip）、`FlatPostRow`（计数行前置 chip）、feed 壳 `TopicCard`（同两态）。
- 详情链零改动；外链视频仍走链接不出块；`NestedThreadOverlay` 复用 ThreadDetailOverlay 自动继承。
- 测试 `VideoMarkerTest`：toTopic hasVideo 透传 + 视频帖 cover 保留（角标依赖图块存在）。
- 记录：搜索结果 `HupuSearchItem` 是另一模型未带视频字段——搜索列表不标，记缺口不伪造。

## 批G-2：图片兜底真机证据——等价验证 + 覆盖面扩展

真机路径对执行者关闭：redroid `127.0.0.1:5555` 在线但规则禁装/卸/清（`am instrument` 需装测试 APK），`emulator-5554` 未授权。**结论写死：兜底真机渲染本批未验到**，证据由等价链路补齐，真机执行留给 Muse：`adb install -r -t app-debug-androidTest.apk && adb shell am instrument -w -e class com.kudo.android.FallbackImagesUiTest com.huzai.kudo.test/androidx.test.runner.AndroidJUnitRunner`。

等价验证三层：
- error 槽契约：`SubcomposeAsyncImage(error=...)` 是 coil 库稳定契约（coil3 `Error` state → error 槽渲染），我方责任面=error 槽内渲什么——已收敛为纯函数 `fallbackIcon`/`fallbackTag`（批E `FallbackImagesTest` 3 例全绿）。
- 调用点审计（2026-10-26 全库扫描）：兜底包装 `HupuAvatarImage/HupuIconImage/HupuThumbImage` 共 **49 处**正文/头像/徽标调用全走 error 槽；裸 `AsyncImage` 44 处分布在 28 文件，逐文件核查=表情/贴纸网格（HupuEmoji/StickerSearch/ThreadDetail 表情面板）、小徽标角标（heroIcon/teamLogo 18-20dp 装饰标）、本地 URI 选图预览（ReplyAttachment）、视频 poster（HupuVideoPlayer）——均非帖子正文/头像主链路，失败空白洞影响为「小图标消失」级，刻意不套兜底（表情格子塞灰块更难看），如实记为残余面。
- 本批扩面：批F `TagFeedOverlay`/`hotTopics` 4 处裸 AsyncImage（banner/logo/头像/pic）补齐为对应兜底包装——新写代码不再新增透明洞。

取舍：`UserProfilePage` 封面缩略图「失败整块撤除」维持批E 判定不变（84×60 灰洞压窄标题换行，撤块优于空洞）。

## 批G-3：预约对话框确认——toast 升级为只读 AlertDialog

复核链：`ScheduleMatchRow` 预约 chip（`m.subscribe?.show == true` 才渲，`showSubscribe` 门槛）→ 批F-2 实现是 `HuzaiToast`，与任务书「对话框」口径不符 → 升级为 `AlertDialog`：title=`subScribeTitle`（空→「预约提醒」）、text=`subScribeDesc`（空→「暂无预约说明」）、唯一动作「知道了」关闭。

- **只读确认**：clickable lambda 只置 `showSubscribe=true` 本地态，无 repository/API 调用；`HuzaiToast` import 一并移除——该文件从此零网络触点。
- **字段来源**：`HupuMatchParser` `subscribeInfo`→`HupuSubscribe(show=subScribeTitle非空或showSubscribe, subscribed=didSubscribe, title=subScribeTitle, desc=subScribeDesc)`，官方字段名与注释对齐。
- **测试**：`scheduleSubscribe_mapsOfficialFieldsAndNullsWhenAbsent`（真字段 fixture：未赛带 info→show/title/desc/didSubscribe=false；无 info 行与已赛空 info 行→null）。
- 真机点按验证仍归 Muse（同 G2 设备约束）；对话框语义与官方订阅动作刻意脱钩——订阅是登录写接口，排除项。

## 批H：androidTest 编译修复（源码漂移 + R8 缺类）

Muse 验收发现 `assembleDebugAndroidTest` 全量编译被陈旧测试阻断。逐文件处置（2026-10-26）：

- `FallbackImagesUiTest`：`KudoTheme` 签名要求 `settings: DisplaySettings` 无默认——3 处补 `KudoTheme(DisplaySettings())`（与 CommunityDirectoryUiTest 同款写法）。
- `TopicActionsUiTest`：2 处 `DisplaySettings(..., compactPinnedTopics = false)` 删该参数（字段已随 `1771f08` 移除，测试不依赖它）。
- `CommunityDirectoryUiTest`：删 `embeddedDirectoryConsumesInnerSwipesAndReturnsEdgeSwipesToHome`——依赖 `FeedScreen.categoryContent` 嵌入槽，该槽随 Phase1 移动端首页重构移除（首页不再 feed+内嵌目录），集成对象本身不存在；其余 7 测直挂 `CommunityDirectoryScreen` 不受影响。文件内留注释说明。
- `CompactPinnedTopicsUiTest`：删 3 测（compactRows/disablingCompact/preferencesDefault——测试 `compactPinnedTopics` 字段、CompactPinnedTopicRow 的 `topic-pinned-*` tag、「置顶贴精简显示」开关，全部随 `1771f08` 移除）；保留 `missingCategoryUsesFallback...`（独立用例）+ fixture/capture。
- R8：`debug.isMinifyEnabled=true` 使 test APK 也走 shrink，androidx.test/okhttp3.tls 引用的编译期注解（errorprone/animal-sniffer）按缺失类报错 → `app/proguard-androidTest.pro` + `testProguardFiles`（只作用 test 变体，主 APK 规则不动）。
- 原则：不动主代码迁就测试；移除用例=特性已删无对齐对象，非掩盖失败。

产物：`app-debug-androidTest.apk`（`com.huzai.kudo.test`，debug 签名）；Muse 执行 `adb install -r -t <test.apk> && adb shell am instrument -w -e class com.kudo.android.FallbackImagesUiTest com.huzai.kudo.test/androidx.test.runner.AndroidJUnitRunner`。

## 批I：测试 APK 运行时缺类修复（dex 级证据链）

崩溃：`am instrument` 启动即 `NoClassDefFoundError: Lkotlin/LazyKt` @ `TestDirCalculator` <clinit>。**根因（dex 表级证实，非猜测）**：app/test 共享构件不进 test dex（AGP dedup），而主 APK 的 R8 把 kotlin 多文件门面水平合并——`LazyKt`→`LazyKt__LazyKt`+`LazyKt__LazyJVMKt`（主 APK 两 dex 连 type_id 都无 `Lkotlin/LazyKt;`），测试 dex 仍按规范名引用 → 全 classpath 无定义。

- `proguard-androidTest.pro` 加 `-dontshrink/-dontobfuscate/-dontoptimize`：AGP 9.1 DeviceTest API 无 per-variant minify 开关，testProguardFiles 是唯一作用域；测试 APK 无 shrink 收益，keep 清单是对「全保留」的脆弱近似。`-keepattributes` 保注解/签名/内部类元数据。
- **但 dontshrink 只保输入类——dedup 在 R8 之前已把共享构件剔出输入**（新旧 dex 类数恒 6798 证明）。真正修复：`libs/` 下以**文件依赖**形式嵌入 `kotlin-stdlib-2.4.10.jar` + `kotlinx-coroutines-core-jvm-1.10.2.jar`（与 test 类路径解析版本一致）→ 构件身份与共享 GAV 不同绕过 dedup → `Lkotlin/LazyKt`/`Intrinsics`/`BuildersKt`/`JobKt` 等 canonical 类由 test dex **自身定义**（8753 类）。
- coroutines jar 带入 JVM debug-agent 对 `java.lang.instrument`/`sun.misc.Signal` 的引用（仅 -javaagent 场景可达）→ 补 4 条 dontwarn。
- 自证（dex 表直查）：LazyKt 旧包=引用无定义，新包=**DEF**；8 探针类全 DEF；剩余未解析引用=编译期注解（errorprone/Composable/Jetbrains）+ JDK-only + **他测试引用的 app 已改名类**（`CommunityDirectoryScreenKt`/`DirectoryTag` 在主 APK 已 rename——`CommunityDirectoryUiTest` 等直调该类名的测试仍不可跑，属 debug-minify 的固有局限，`-e class` 过滤下不阻塞兜底测试；mockwebserver WebSocket 路径同理）。`FallbackImagesUiTest` 所需 app 类（`KudoThemeKt`/`FallbackImagesKt`/`HupuImageFallback`/`DisplaySettings`）均 canonical DEF 于主 APK。
- 回退：删 `libs/*.jar` + build.gradle 两行 files() 依赖 + 本笔记段。

## 批J：instrument 启动 IllegalAccessError——撤销嵌入，debug 主包保留 kotlin canonical

批I 的 libs/ 嵌入引入第二份 stdlib：**stdlib jar 里 `CollectionsKt__CollectionsKt` 等 `__` 多文件部件类是 package-private**（javap 实证：`class` 无 public），而主包 R8 合并后的版本被改成 public 供 androidx 调用——instrument 双类加载器同进程，主包 `ProcessLifecycleInitializer` 解析到测试 dex 的包私有副本 → `IllegalAccessError`（AppInitializer 阶段）。单独主包启动正常，因单 dex 内自洽。

根治（替换批I 方案）：**debug 变体主包保留 kotlin/kotlinx canonical 类**——`app/proguard-debug.pro`：`-keep class kotlin.** { *; }` + `-keep class kotlinx.** { *; }`，接入 `debug.proguardFiles`。主包代码不再被重写到 `__` 合并类，只调 public 门面（`CollectionsKt`/`LazyKt`），测试 dex 按同一规范名从主包解析——单一 stdlib 来源，无副本可撞。libs/ 嵌入 jar 与对应 dontwarn 同步撤销。

- 影响面：只动 debug 构建配置；release `proguardFiles` 链未动，正式包零影响。代价=debug 包 kotlin 类不再被 R8 合并/裁剪，dex 变大（19,673 类 vs 原 18,343）且失去部分内联优化——nightly 走 debug 变体，属可接受的测试友好性交换；如夜间构建性能敏感可再议缩小 keep 面。
- `-dontshrink/-dontobfuscate/-dontoptimize` 保留在 test 变体：防 R8 把「未被静态引用的测试类」当死码裁掉（junit 反射发现入口）。
- 自证（dex 表直查）：主包 `LazyKt`/`CollectionsKt`/`CollectionsKt__CollectionsKt`/`BuildersKt`/`JobKt`/`Intrinsics` 全部 DEF；test dex kotlin.* 类定义=0（纯引用，单源解析）；`TestDirCalculator`/`ActivityScenario`/`AppInitializer`/`ProcessLifecycleInitializer` 链路类全部可解析；兜底测试调用树（`KudoThemeKt`/`FallbackImagesKt`/`DisplaySettings`/coil2 `SubcomposeAsyncImageKt`/M3 组件）全 canonical DEF。
- 残余（如实）：test dex 引用的个别共享类主包仍无定义——`androidx.concurrent.futures.CallbackToFutureAdapter`、compose-ui `DialogWindowProvider`/`PixelMap`（兜底测试路径不经过，其他测试若触达仍可能缺）；`com.kudo.*` 被改名的类（`CommunityDirectoryScreenKt`/`DirectoryTag` 等）→ 直调这些类名的旧 androidTest 仍不可跑；mockwebserver WebSocket/okio 路径、junit 可选依赖（easymock/jmock，junit 自带容错）。完整 instrument 跑通与否最终仍需 Muse 真机确认。
- 回退：删 `proguard-debug.pro` + `proguardFiles` 引用行 + 本笔记段（恢复批I 状态须另还原嵌入）。

## 批K：compose 测试框架类被裁——一次性审计扩展 keep 边界（不再打地鼠）

第三化身：`ClassNotFoundException: androidx.compose.ui.platform.InfiniteAnimationPolicy$DefaultImpls` @ `AndroidComposeTestRule` 初始化——runner 已启动，用例建规则时缺 compose-ui-test 框架传递需要的、App 不直接引用被主包 R8 裁掉的类。

**一次性审计方法**（可复用）：test dex type_ids − test class_defs − app class_defs，剔除 JDK/框架/纯编译期注解 → 批J 组合下真实缺失仅 34 类，按包归因：androidx.compose×11、androidx.concurrent.futures×4、com.kudo 改名类×4、okhttp3-WS×8+okio×1、junit 可选×4、框架自带 org.json/org.w3c×3。

- **keep 边界选型**：`-keep class androidx.** { *; }` + `-keep class com.kudo.android.** { *; }` 并入 `proguard-debug.pro`——androidx/kotlin/kotlinx = 测试类路径与主包的全部共享库空间，com.kudo 成员级 keep 使测试按源码名调 app 方法（顺带根治批J 记录的「直调改名 app 类的测试不可跑」残余面）。边界覆盖缺失类别而非类名，后续漂移自动吸收。
- **自证（dex 表）**：崩溃类 `InfiniteAnimationPolicy$DefaultImpls`、`CallbackToFutureAdapter`、`CommunityDirectoryScreenKt`、`DirectoryTag`、`LazyKt`/`CollectionsKt` 全部 appDEX DEF；真实缺失 34→15，残余=okhttp3-WS/okio（mockwebserver WS 专用路径，现无用例触达）、easymock/jmock（junit 可选容错）、org.json/org.w3c（Android 框架 bootclasspath 自带）；`DialogWindowProvider` 为 ui-test 与 app compose-ui 版本偏差引用（两 dex 均无定义，兜底路径不经过）。
- **代价**：debug 主包类定义 18,343→41,461（androidx+app 全保），放弃该部分 shrink/内联；APK 增大。仍只作用 debug 变体，release 零影响。
- 回退：删 proguard-debug.pro 中批K 两行（回批J 状态）或整文件（回批H 状态）。

## 批L-1：专区页「广场/关注」切换 + 本页版块搜索（search2 type=topic）

对齐官方专区页顶层结构（Muse 2026-10-26 实测缺口）：

- **广场/关注**：`SortBar` 段控复用为顶层切换（plaza/follow），放 `PageHeader` 下。广场=原左栏+版块网格；关注=本地 `HupuPrefs.favoriteTopics` 全宽网格（匿名可用；官方云端关注需登录态，不做）。原左栏「收藏专区」入口移除（`CateRail.showFavorites=false`、FAV_CATE_ID 不再入滑轨）——顶层关注覆盖同一功能，避免双入口。
- **版块搜索**：顶栏右侧 `SearchOutlined` → `ZoneSearchOverlay` 盖入页（zIndex 3，盖过话题流/详情盖入页）。数据走 `m.hupu.com/api/v2/search2?type=topic`（实测匿名可读）：`id`→topicId、`title` 剥 `<font>` 高亮、`content`→desc、`img`→logo；无 url 字段，用 `/{topicId}` 数字路径兜底（实测 /127 → 湖人专区 SSR）。400ms 防抖实时搜；结果点击优先按 topicId 映射本地类目 url。
- 新接口通道：`HupuApi.fetchMobileJson`（m.hupu.com/api/v2/ 家族只读 GET，桌面 UA + 全局节流 + 登录 cookie 透传）。
- **嵌套注释坑**：KDoc 里写 `api/v2/*` 的 `/*` 会开嵌套注释、吞掉后续函数到下一个 `*/`（恰在字符串 `"*/*"` 里闭合）——语法报错全是指向函数体的 "Expecting member declaration"。教训：注释内避免 `/*`/`*/` 字符序列。

## 批L-2：版块页档案补齐——JRs 成员数 + 版主团队/招募标记

官方版块页档案头的匿名可达部分（Muse 实测缺口中的可兑现集）：

- **JRs 成员数**：`m.hupu.com/zone/{topicId}` 的 `pageProps.zoneData.followedUserNum`（如 "277.5万"）。取数路径：`fetchMobileHtml("/zone/{id}")` 提取 `"buildId"` → `getMobileJson("/_next/data/{bid}/zone/{id}.json?id={id}")`。buildId 缓存于 `HupuRepository.cachedZoneBuildId`，JSON 404 时清空重取一次（部署滚动）。
- **版主团队 + 招募标记**：话题页 SSR `$$data.admins`（与 topic 平级的顶层节点）——`adminApplicable=1`=招募版主、`adminList`=版主（roleName/userName/header/url）。解析进 `HupuTopicPage.boardInfo` → `TopicFeedState.boardInfo` → `BoardProfileHeader` 下新增「专区信息」行（前 6 个版主头像 + 名单 + 红底招募标记，纯展示不可点）。
- 成员数惰性拉取：`TopicFeedOverlay` 挂 `LaunchedEffect(topic.topicId)` → 父级 `zoneMemberTexts` map 按 topicId 缓存，失败不写入（下次进入自动重试）。
- `fetchMobileJson`（批L-1 误加的封装）已删除——与既有 `getMobileJson`（批F-4）重复，统一用后者。

## 批L-2b：版块页缺口的匿名源核查结论（不做项）

2026-10-26 经 Resin 代理对 bbs/m 站实测逐项核查（桌面 UA 同 app 取数通道）：

- **精华 tab**：无匿名源。话题页 SSR tabs 实测只有「最新回复/最新发布/24小时榜」（lakers、502 同构）；`lakers-digest`/`lakers-essence` 等 URL 变体返回 404 兜底页（category=虎扑社区）；`topicThreads` API 的 type/sort/sortType 参数全部无效（回包恒同列表）；`games.mobileapi.hupu.com` 的 bbsthreadapi 家族回 `code:-24`（需 §9 签名，按批B 结论不碰）；`bbs.hupu.com/pcmapi/pc/bbs/v1/*` 回 AS021999。
- **真实经历 tab**：同上无源——官方 app 的 zone 内 tag 过滤面，无公开的 zone→tag 映射。
- **置顶标**：SSR `threads.list` 项无 isTop/isPin 字段（键集实测：author/cover/createdAt/hasVideo/lights/read/repliedAt/replies/tid/title/titleFont/topic/topicId/url，titleFont 恒空）；SSR 数据里无独立置顶数组。
- **版规正文**：`admins` 节点仅 title/adminApplicable/adminList，无 rules 文本或链接；全页无 guize/rule 字段。已兑现的部分=版主团队 + 招募标记（L-2）；申请版主与版规内容属登录态/无源，不做。

结论：以上四项无匿名可读数据源，按规则不伪造、不占位——等官方接口面变化或登录批次再评估。

## 批L-3：悬浮层遮挡——内容底部留白避让

真机现状：KudoApp 的 `GlassNavigation` 药丸**始终可见**（SecondaryPage.count 写而未读——本宿主没接隐藏逻辑），二级盖入页上的悬浮元素会与它叠层。本批只加留白，不动药丸样式/位置：

- 详情页 `LazyColumn` bottom：140dp → `64dp + LocalQuickScrollBottomPadding`（≈操作条高 + 导航药丸+手势区占用，navBars+88dp），末行可滚到两条悬浮层之上。
- 版块页 `「立即发布」FAB` bottom：24dp → `LocalQuickScrollBottomPadding + 8dp`（原值时 FAB 下半被药丸遮住一半）。
- 版块页 FeedList：新增 `bottomInset` 参数（默认 140dp 保既有调用方），版块页传 `LocalQuickScrollBottomPadding + 72dp`（FAB 高度+间隔）。
- 遗留：`SecondaryPage.count` 在本宿主（KudoApp/GlassNavigation）无消费者——若后续接上「二级页打开隐藏导航」机制，三处留白可再收紧；当前留白对药丸不可见场景也安全（只是底部稍空）。

## 批L-4：NBA 频道快捷行核查（无需改动）

Muse 要求核查「层级稀疏 + 快捷行项数」——先查代码与接口现状再定：

- **项数**：快捷行 = 赛程 + 数据 tab 深链项（`leagueDataTabs` → `nativeDataTabKind` 命中才渲染）。2026-10-26 实测 `getDataTabList?enType=nba`：命中 kind = team/player/draft/injury 四项 → 行内 5 条（赛程/积分榜/球员榜/选秀/伤病），对齐官方 5 位；against/honor/history tab 属 h5 内嵌轨道，不进快捷行（按既有设计）。
- **层级稀疏**：`ShortcutIcon` 已 `semantics(mergeDescendants=true)` + `testTag("shortcut-{label}")`（批A-4 修复）——uiautomator 每入口单节点、bounds=整列触控区，不再稀疏。
- **降级链**：dataKinds 空（接口失败/无轨道频道）→ 回退 赛程+积分榜（hasStandings 有值时），不留空行。
- 结论：代码与实时接口均已达官方形态，本批零改动。

## 批N-1：信息流卡复刻——作者行/日期/转发数按 tid 惰性补齐

官方信息流卡顶行（头像+昵称+日期）与底栏第三计数（转发）在综合流数据源里都没有：m.hupu.com 首页 `res[]` 键集实测只有 badge/isNews/label/lights/replies/source/tid/title/type/url；/all-gambia `pageData.threads` 同样无 author。

- **作者行+日期**：复用 1.222 黑名单补作者机制（`/api/v2/threads?tid=`），去掉黑名单门槛改为全量补齐；单次请求新增 `createdAtFormat` 一并回传（`threadCardHead`）。缓存值从 `puid|euid` 扩为 `puid|euid|name|avatar`——旧两段值视为「展示不完整」再补一次（兼容迁移，无版本位）。
- **转发数**：`m.hupu.com/api/v2/bbs-thread/{tid}` 的 `data.t_detail.share`（实测匿名可读，同接口还带 is_top/hits）。`HupuThread.shareNum` 以 **-1=未携带** 区分「没数据」与「0」——未补齐不占位，补齐后显示真实值（含 0）。转发数易变不落盘，进程内按 tid 记一次。
- **成本**：每条 tid 两个接口（threads?tid= + bbs-thread）同批并发，4 条/批 + 批间 120ms——沿用既有节流口径；作者缓存命中后只剩转发数请求。
- `FeedItem` 底行：`shareNum >= 0` 时追加 `IosShare + formatCount`。
- 右上「···」菜单未做：官方为举报/拉黑入口，本端黑名单交互另属账号页，范围外。

## 批N-1b：神回复预览块无匿名源（不做）

官方信息流卡的灰底神回复块（回复者+亮了[N]+引用正文+「去看 N 条亮回复」）字段 `lightReplyResult{replyUserName,pid,totalCount,content}` **仅存在于 `api/v2/bbs/walkingStreet/threads`**——该接口固定只下发步行街主干道（topicId 参数实测被忽略），接不进首页综合流。res[]、`threads?tid=`、`bbs-thread/{tid}`、detail SSR、`reply/list` 实测均无同义字段；本地按「最亮回复」推导语义不等价（totalCount=亮回复总数非列表可算）且每卡一次全量回复请求不可行。结论：不做，等官方接口面变化。

## 批N-2：详情页头部按官方复刻——标题→作者行→正文→文末meta

- **排布**：原「作者行→标题→正文」改为官方「标题(20sp粗体/lh27)→作者行(头像+昵称+发布时间+右侧「版块名 >」)→正文→文末meta」。作者行并入发布时间后，meta 行改为官方口径「发布于{地} 阅读 {数}」（字段缺失段不显示、全缺整行不渲染）。
- **版块链接**：`HupuUiSignals.zoneBoardRequest` 信号总线——详情页置 `HupuTopicInfo` → KudoApp `LaunchedEffect` 切底栏 Tab 1 → ZonePage 消费并 `openTopic`（复用既有盖入版块流，零新页面）。版块 url 缺失时回落 `/{topicId}` 数字路径。
- **正文**：`HtmlContent` 加 `bodyFontSp/bodyLineHeightSp` 参数（默认 15/24 保回复语境），主楼传 17/28（官方 17-18sp 宽松行距）。
- **话题词/链接**：span.link 从「主题色不可点」改为 `addLink(LinkAnnotation.Url)` + 固定蓝 `HUPU_LINK_BLUE=0xFF3B82F6`（官方蓝链色，与主题色解耦）——点击经 LocalUriHandler 开系统浏览器（v1；官方为应用内打开，内嵌路由留待后续）。bold+link 共存时 bold 样式叠加。

## 批N-3：详情底栏——悬浮药丸改官方通栏固定底栏

Muse 实测悬浮毛玻璃药丸滚动中途盖正文（批L-3 只修了末尾留白）。按任务默认对齐官方：悬浮药丸 → **满宽不透明通栏**（surface 底 + 0.5dp 顶分隔线），上层仍负责药丸/navBars 避让 padding 与出入场动画（回复框/楼中楼/全屏时整条移出，逻辑零改动）。

- 内容对齐官方「页码 | 我来评论 | 点赞 | 评论 | 转发」：页码=`replyPage/replyTotalPages`；「我来评论」=灰底圆角框点击开回复框（替代原「写评论」液态按钮）；点赞/评论计数保留点击语义（推荐、跳评论区）。
- **转发数**：`repo.threadShareNum(tid)`（bbs-thread）进入详情即惰性补，进程内按 tid 缓存；未到只显图标。底栏分享动作仍是系统分享（不变）。
- **收藏保留**：官方底栏无收藏项，但本端收藏仅此入口可达——转发右侧保留，如实记差。
- 悬浮条时期的 drawBackdrop/圆角药丸/LiquidButton/InteractiveHighlight 全部移除；`backdrop` 参数删除，调用方 actionBackdrop 仍服务楼层 sheet 等其他悬浮层。
- 遮挡结论：通栏不透明且常驻，正文滚动在栏顶硬切——等同官方视觉；滚动中途不再有半透明药丸浮在正文上。

## 批N-4：首页顶栏搜索条官方化（纯样式）

官方首页顶栏为「虎扑logo + 灰底搜索框（放大镜+提示）+ 红底『搜索』按钮」。原实现是右侧液态玻璃放大镜图标键——改为官方形态：灰底圆角长框（36dp×190dp，放大镜+「搜索」提示）+ 右端红底「搜索」胶囊（品牌红 `HUPU_RED=0xFFC01E2F`）。只改样式不改入口——点击仍开 `SearchPage` 盖入页，未接搜索词直填（功能不变）。宽 190dp 为固定视觉宽：PageHeader trailing 槽是自适应宽，标题「虎扑」+话题条下宽度受限，190dp 与官方条视觉比例接近且不与标题挤位。

## 批Nfix-0：真实组件归属表（先核查再改——批N 返工前置）

Muse 2026-10-26 真机验收：批N 的 N1/N4 改进了**无挂载点的死组件**，N2/N3 只覆盖版块路径详情。
以下为代码核查（非猜测）得出的「用户路径 → 实际组件」映射，后续对齐工作一律以本表为准：

| 用户路径 | 挂载链 | 实际渲染组件 |
|---|---|---|
| 首页信息流 | `KudoApp "root"` Tab0 → `FeedScreen` → `FeedPage` items | **`ui/components/Common.kt TopicCard`**（Discourse `Topic` 模型，经 `HupuBridge.toTopic()` 桥入；作者行代码已有但 `topic.author==null` 时整行不渲染） |
| 首页顶栏 | `FeedScreen` 头部 Column | `FeedScreen.kt:147-184`（logo + 灰底搜索 pill + 「搜索」文字） |
| 首页帖详情 | `NavHost "topic/{id}"` → `TopicViewModel`→`CommunityRepository.detail` | **`ui/topic/TopicScreen` + `TopicPost` + `TopicTopBar` + `TopicBottomBar`**（主楼 number==1 无作者行；底栏为液态按钮组浮层） |
| 专区页 | `KudoApp` Tab1 | `hupuui/pages/ZonePage`（二级页 `TopicFeedOverlay` 版块流用 `FeedList`/`FeedItem`——活着） |
| 版块/历史/消息帖详情 | `ZonePage:555` / `HistoryPage:358` / `MessagePage:637` | **`hupuui/pages/ThreadDetailOverlay`**（批N 的 N2/N3 已生效路径，保留不回退） |
| 比赛 Tab | `KudoApp` Tab3 | `hupuui/pages/ScorePage` |
| **死代码** | 全仓库仅 `HupuApi.kt:431` 一句注释提及，无任何调用方 | `hupuui/pages/HomePage.kt`——批N-1/N-4 在此做的改动不产生任何运行时效果，批Nfix-5 回退 |

关键事实：
- `CommunityRepository` 是本端唯一社区仓储——读路径（话题流/详情/搜索）全部桥到 `HupuRepository`，`Topic`/`TopicDetail`/`Post` 是通用渲染模型；虎扑字段经 `HupuBridge` 映射进该模型（`author→Member`、`lights→likes`、`createdAtText→bumpedAt`）。
- `toMember()` 此前把 `Member.avatar` 置空——桥入作者头像全丢（影响所有用到作者头像的渲染位）。
- 主导航药丸 `GlassNavigation` 渲染在 `"root"` composable 外层 Box（`KudoApp.kt:257`），ZonePage 的盖入详情在它**下层**——这是批N-3 通栏底栏与药丸重叠的成因；`NavHost` 目的地页面（topic/category 等）不经过 root composable，天然无药丸。

## 批Nfix-1：真实信息流卡复刻——作者行/日期/转发数（移植 N1 到 TopicCard 路径）

批N 的补齐驱动挂在死代码 `hupuui/HomePage` 上；真实信息流是 `FeedScreen→FeedPage→TopicCard`（Discourse `Topic` 模型）。本次把同一套匿名源（`threads?tid=` 作者+createdAtFormat、`bbs-thread` share）落到真实管道：

- **模型**：`Topic.shareNum: Int = -1`（-1=未携带，卡上不渲染转发位）；`toMember()` 修复 `avatar=""`——此前桥入作者头像全丢，现传 `author.url`（`mediaUrl` 对绝对 URL 透传，`Avatar` 直接可渲）。
- **补齐驱动**：`CommunityRepository.enrichTopics()`——缺什么补什么（author==null / shareNum<0 / bumpedAt 空）；作者缓存复用 `HupuPrefs` 同一 `puid|euid|name|avatar` 格式（与死代码路径同 store，命中即零请求）；`createdAtFormat` 仅在 bumpedAt 为空时兜底写入（`TopicCard` 作者行 `relativeTime(...).ifBlank{bumpedAt}` 原样显示中文文本）。
- **接入点**：`FeedViewModel.enrichFeed(filter)`——refresh/more/prefetch 落页后异步补齐（首屏不被请求拖慢），结果按 id 合回 `_state` 与 `_pages`；热榜（hot 频道）不走帖子卡、跳过。
- `TopicCard` 底行 `shareNum>=0` 时追加 `Share + compactNumber`（官方「赞/评/转发」三计数）。
- 成本与批N 相同：每 tid 两接口、4条/批+120ms 节流；作者缓存命中后只剩 share 请求。

## 批Nfix-3：首页帖详情复刻——作者行版块链接 / 文末 meta / 通栏底栏（移植 N2+N3 到 TopicScreen 路径）

首页详情真实组件是 `ui/topic/TopicScreen`（Discourse `TopicDetail` 模型），批N 改的 `hupuui/ThreadDetail*` 只在专区盖入路径活着。移植：

- **作者行+版块链接**：标题下新增官方形态作者行（头像+昵称+`TopicAuthorMetadata` 发布时间），右侧「版块名 ChevronRight」→ `openCategory(detail.categoryId)`（`category/{id}` 推入版块流页，替代批N-2 的 zoneBoardRequest 信号——首页详情是 nav 目的地，直接推页更简）；话题 tags 保留为 FlowRow（原分类 chip 已并入作者行链接）。
- **文末 meta**：`TopicDetail.location`/`readCount` 新字段，`toTopicDetail()` 桥入 thread.location/read；主楼卡文末「发布于{地} 阅读 {数}」（缺段不显示、全缺不渲染，`topic-meta` testTag）。
- **通栏底栏**：`TopicBottomBar` 悬浮液态按钮组 → 满宽不透明 `Surface`+0.5dp 顶线；内容「页码(开楼层跳转) | 我来评论灰框(开回复编辑器) | 点亮 | 评论数 | 转发+数 | ☆收藏」；楼层编辑态整栏替换 `FloorNavigationBar`（三段滑出动画→透明度交叉）。testTag 兼容：`topic-reply`/`topic-floor-index`/`topic-share`/`topic-reply-count` 保留，新增 `topic-bookmark`/`topic-board-link`/`topic-meta`。
- **转发数**：`detail()` 内与正文并发取 `hupu.threadShareNum`→`TopicDetail.shareNum`（-1 只显图标）；点亮=主帖 `TopicReactionButton`；「写回复」液态按钮改名「我来评论」语义不变。
- **收藏保留**（官方无此位，本端收藏主帖可达入口，如实记差）；自帖编辑入口随 `ownFirst` 保留。

## 批Nfix-4：专区详情打开时隐藏主导航药丸（层叠修复）

成因（批Nfix-0 表）：`GlassNavigation` 渲染在 `"root"` composable 外层、ZonePage 的 `ThreadDetailOverlay` 下层——批N-3 通栏底栏贴底后与药丸同边重叠。方案取「详情打开时隐藏主导航药丸」而非「底栏抬到药丸上方」：

- 官方详情是推入页本无底栏导航，隐藏形态对齐官方；抬栏方案会让通栏浮在半空且仍需正文避让，治不了重叠根因。
- 实现：`HupuUiSignals.zoneDetailOpen`（可观察 state）——ZonePage `LaunchedEffect(openedThread != null)` 置位 + `DisposableEffect` 兜底复位；KudoApp `if (!zoneDetailOpen) GlassNavigation(...)`。
- 影响面：仅专区 Tab 详情盖入期间；版块流/搜索盖入层不动药丸（版块页 FAB 避让沿用批L-3）；`NavHost` 目的地详情（首页路径）本不经 root composable，不受影响。
- `LocalQuickScrollBottomPadding`（navBars+88dp）在详情打开期间保留——列表末留白偏大但无害，不回改。

## 批Nfix-5：死代码处理——HomePage.kt 批N 改动回退

`hupuui/pages/HomePage.kt` 经核查为**死代码**（全仓库仅 `HupuApi.kt:431` 一句注释提及，无任何调用方；历史挂载点在虎仔移植 `1a8db37` 后未再接线）。批N-1/N-4 在其中做了作者补齐与搜索条改动——「看起来做了、实际没跑」。

- **决定：回退**（`git checkout 94218d5^ -- HomePage.kt`，文件回到批N 前状态）。理由：死组件上留完整实现会误导后续批次以为首页管道走这里（批N 返工正是这么发生的）；若将来重新接线，批Nfix 已在真实组件上给出可对照实现。
- **不回退的部分**：`FeedItem` 的 shareNum 显示位（`FeedUi.kt`——经 `FeedList` 在 `BoardPage`/`UserProfilePage` 活着，shareNum=-1 时不渲染，数据一旦补齐即自然生效）；`HupuThread.shareNum`/`threadCardHead`/`threadShareNum`（现由 `CommunityRepository.enrichTopics`/`detail()` 真实调用）；`HupuPrefs` 作者缓存格式（真实路径复用同一 store）。
- 连带损失如实说明：HomePage 内 1.222 黑名单补作者逻辑同被回退——该页本就不可达，无运行时影响。

## 批O-0：首页信息流卡「无作者行/转发数」诊断（先核查再修——批O 前置）

Muse 2026-10-26 真机验收批Nfix：推荐流（latest，FeedScreen→FeedPage→TopicCard）25s+ 滚动后仍无作者行、无转发数。`TopicCard` 的渲染门槛是 `author != null` / `shareNum >= 0`——「没显示」=「补齐结果没合回来」，渲染层无缺。

**诊断结论 1（构造路径）**：推荐流 `Topic` 的构造链是 `FeedViewModel.refresh → loadTopics("latest") → CommunityRepository.topics → hupu.mobileHome → parseMobileHome → HupuThread.toTopic()`，全程无第二个拷贝点。Resin 实测 `m.hupu.com/` SSR `res[]` 键集 = `{badge, isNews, label, lights, replies, source, tid, title, type, url}`——**列表源根本没有 author/createdAt/share**，`toTopic` 产出 `author=null / bumpedAt="" / shareNum=-1` 是如实映射而非途中丢字段；作者/日期/转发的唯一来源就是 `enrichTopics`（批Nfix-1 设计无误）。

**诊断结论 2（enrich 跑了但合不回来——交付时序缺陷）**：`enrichFeed("latest")` 确被 `refresh()` 调用（`_state.update` 之后，source=首屏 30 条）；两个匿名接口本身可用（Resin 实测：`threads?tid=` → `author.header`/`createdAtFormat`/`createdAt`(ms)；`bbs-thread` → `t_detail.share`）。真正缺陷：
- `threadShareNum` 走 `getMobileJson` → **全局 `RequestThrottle(1000ms)`**（CAS 时间片，全应用共享）：30 条 share 请求被串行成 ≥30s；
- `enrichTopics` 是**全量交付**——`coroutineScope` 跑完所有批才返回新列表，`enrichFeed` 才 merge：快的作者/日期（`fetchThreadAuthorJson` 刻意不走全局节流，4并发/批 ≈ 2-4s）被 share 拖住，**25s 观察窗内屏幕上零变化**；
- `more()`/手动 `refresh()`/`invalidateLatest` 先 `enrichJobs[filter]?.cancel()` 再重跑 → 翻页/下拉把已跑进度清零；用户其他导航（详情/专区）抢占同一全局节流位进一步后延 share；
- 次级缺陷：作者缓存命中分支跳过 `threadCardHead` → `createdAtFormat` 永不再取 → **缓存命中后日期永远不显示**（`puid|euid|name|avatar` 四段值没有日期位）。

修复方向（批O-1）：(a) `enrichTopics` 改**渐进交付**——每批完成即 `onBatch` 回调，ViewModel 逐批 merge 上屏（作者行 2-4s 可见，share 按节流节奏每批 4 条渐入，不再全量等待）；(b) 作者缓存扩第 5 段 `createdAtMs`（epoch ms，转 ISO 走 `relativeTime`，不会变陈旧的相对文本），旧 4 段值视为「作者命中、日期缺」→ 仅为日期补一次 `threadCardHead`；(c) share 仍走节流通道（防频控不动），靠渐进回调与作者行解耦。

## 批O-1：补齐链路修复——渐进交付 + 日期入缓存

- **`enrichTopics(topics, onBatch)`** 增可选回调（默认 no-op，兼容老调用方）：每批 4 条 await 完即 `onBatch(out.toList())`；`FeedViewModel.enrichFeed` 把回调接到逐批 `apply()`（同一份 byId merge 逻辑），enriched 终值再 apply 一次兜底。作者行（`fetchThreadAuthorJson` 无节流）≈ 2-4s 可见；share 随 `getMobileJson` 的 1s 全局节流按批渐入，不再拖住作者行。
- **作者缓存 5 段** `puid|euid|name|avatar|createdAtMs`：`threadCardHead` 返回升 `Triple(author, createdAtFormat, createdAtMs)`；命中且 `createdAtMs>0` → `bumpedAt=Instant.ofEpochMilli(ms).toString()`（ISO，`relativeTime` 恒定准确，不变陈旧文本）；命中作者但缺日期段（旧 4 段值）→ 仍为日期补一次 `threadCardHead` 并改写 5 段；`ms<=0` 时回退 `createdAtFormat` 原文显示。
- **share 不动节流**：`threadShareNum` 仍走 `getMobileJson`（防频控常设），总量 ≥30s 不变——但渐显可见且与作者行解耦；`more()`/`refresh()` 重跑 enrich 时，已带 share≥0/作者缓存的条目在 pending 过滤中自然跳过，重放成本低。
- 测试：`ThreadExtrasParserTest` 加 `createdAtMs` 例（正/空/脏值），4/4 绿。
- 回归面：未触碰 NF2（FeedScreen 搜索条）/NF3（TopicScreen 详情）/NF4（zoneDetailOpen 信号）——改动文件为 HupuParser/HupuRepository/CommunityRepository/FeedViewModel/ThreadExtrasParserTest，与三处零文件交集。

## 批P-1：首页详情正文话题词——链接分类器 + 官方蓝链样式

**证据基线（Resin 匿名实测，先核查再改）**：官方详情正文话题词 = 虎扑站内链接，官方 App 固定蓝、可点、应用内打开。桌面 SSR `pageProps.detail.thread.content` 抽样（9 帖：新闻 3 + 步行街用户帖 6）正文/回复均无 `<a>`、无 `#xxx#` 文本——话题词以 `<a>` 出现时走本路径；`thread.topic`（话题区元数据：topicId/name/url）与移动端 `moduleConfigList.topic.tags[].url = "huputiyu://bbs/subject/{topicId}"` 是官方话题位数据源（批P-2 接）。官方渲染事实：移动 SSR `topic` 模块位于正文前、蓝字可点。

- **新文件 `hupu/HupuLink.kt`**：`HupuLink.destination(raw)` → `HupuDestination{Thread(tid)/Board(boardUrl)/User(euid)/Tag(tagId)}`，承载归一化绝对 URL。规则：`/{tid}.html`（含版块前缀与 `-{page}` 翻页尾）、`m.hupu.com/bbs/{tid}(.html)` → Thread；单段版块路径（`/all-nba`、`/502`）、`m.hupu.com/bbs/{board}` → Board；`my.hupu.com/{euid}`、`m.hupu.com/user/{euid}` → User；`m.hupu.com/tag/{id}`、`?tagId=`、`huputiyu://bbs/subject/{id}` → Tag。非虎扑域/多段不可判定/非 http 一律 null → 走既有分流。HOSTS 仅 `bbs/m.hupu.com`（`www.hupu.com` 资讯域不进 bbs 路由）。
- **共享蓝常量**：`HUPU_LINK_BLUE = 0xFF3B82F6` 上移到 `hupuui/theme/Color.kt`（原 `ThreadDetailHtml.kt` internal 定义删除、改 import——专区路径渲染零变化，仅常量归位）。理由：任务要求样式常量集中一处，双渲染器（RichContent/ThreadDetailHtml）共用。
- **`RichContent` `<a>` 分支**：`HupuLink.destination(href)` 命中 → `LinkAnnotation.Url(dest.url)` + `SpanStyle(color=HUPU_LINK_BLUE)` **无下划线**——官方话题词形态；未命中走原路（`contentUrl` 可解 → 主题色+下划线；不可解 → 裸文本）。`dest.url` 已归一化为绝对 URL，LocalUriHandler→openLink 拿到的是合法绝对地址。
- 测试 `HupuLinkTest` 8/8：帖（前缀/翻页/移动/相对）、版块、用户、tag 三种形态、外链/伪协议/多段不可判定负例。

## 批P-2：话题词应用内打开——openLink 站内分发 + tagpage 信息流 + 话题区标签位

**路由取舍（DECISIONS 同步）**：`openLink` 在 `appLinks` 之后、`fallbackUrl` 之前插 `HupuLink.destination` 分发——
- Thread → `topic/{HupuIds.topicId(tid)}`（本端详情）
- Board → `category/{HupuIds.boardId(boardUrl)}`（版块流，topicPage/board 自动回落）
- User → `user/{euid}`（用户主页，euid 直喂 `repository.user`）
- Tag → `tag/{slug}?id={HupuIds.id("tagpage:$tagId")}`（复用 tag 路由；见下）
- null（外链/多段不可判定/伪协议）→ 原 `fallbackUrl → openContentLink` 分流（应用内 WebView/invalidLink），零回归。

**tagpage 信息流通道**：`tag/{slug}?id={id}` 路由的 `FeedViewModel(tagId)` → `tagTopics → topics(filter, categoryId=id)` 原本只认 `board:` 键；`topics()` categoryId 分支首查 `HupuIds.key(id)` 的 `tagpage:` 前缀 → `hupu.tagThreads(tagId, page)`（批F-4 已验匿名接口，`code=200` 实测）→ `HupuTagThread.toTopic()` 新桥接（userName/header 有、euid 无 → Member.username 置空防误跳用户页）→ cursor `p{N}` 走 nextPage。tagThreads 无 filter 语义——TagFeedScreen 各 Tab 同源（如实记差）。

**话题区标签位**：`thread.topic`（SSR 字段：topicId/name/url，实测 `184/足球话题区//topic`、`60/影视区//ent`）→ `toTopicDetail().tags`：`TopicTag(name, slug=name, id=HupuIds.id("tagpage:$topicId"))` → 详情头部既有 FlowRow 上屏；`TopicTagChip` 加 `accent` 参数（详情页传 `HUPU_LINK_BLUE`：蓝字+10% 蓝底——官方话题词为蓝字可点，chip 化是本端标签行既有形态）；topicId 空则不产出（不可路由不显示）。官方深链 `huputiyu://bbs/subject/{topicId}` 与 `m.hupu.com/tag/{id}` 同目的地，分类器已并案。

## 批P-3：同渲染器元素核对（@用户/普通链接/图片/视频占位）

匿名样本实测（桌面 SSR `detail.thread.content` + `replies[].content`，9 帖/≈60 楼）：

| 元素 | 实测标记 | 本端现状 | 官方对照 | 处置 |
|---|---|---|---|---|
| @用户 | `<a class="u" href="https://my.hupu.com/{euid}" uid=...>@名</a>`（实见于回复） | 原先主题色+下划线→系统浏览器 | 官方蓝色、应用内开用户页 | **已随批P-1/2 生效**：my.hupu.com → User 目的地 → 蓝链无下划线 → `user/{euid}` 应用内 |
| 普通外链 | 样本 0 例（正文几乎无 `<a>`） | 主题色+下划线→openLink→应用内 WebView | 官方正文链接蓝色、应用内浏览 | **确认差异已改**：链接色统一 `HUPU_LINK_BLUE`，保留下划线作与站内话题词的视觉区分；分流不变（应用内 WebView） |
| 图片 | `<img>` → `ArticleBlock.Image`+画廊 | 全屏画廊查看器 | 官方全屏查看 | 无差异，未动 |
| 视频占位 | `thread.video`/`iframe` → `ArticleBlock.Video`→`RichVideoBlock` | 内嵌可点播放卡 | 官方内嵌播放 | 无差异，未动 |
| `#xxx#` 纯文本话题词 | 样本 0 例 | — | — | 无匿名样本证实标记形态，不做文本扫描，如实记差 |

## 批Q-0：专区冷启动 ~10s 定性 + 导航重构挂载点清单（先核查再改）

**冷启动定性（代码核查，非猜测）**：专区目录 = `ZonePage → repo.categories() → HupuApi.fetchHtml("/all-gambia")`（桌面 SSR ≈280KB）→ `parseCategories`。链路三处成本，主因是**全局节流排队**而非网络/解析本身：
1. `fetchHtml` 第 367 行 `throttle.acquire()` **先于缓存检查**——`HupuApi.throttle` 是全应用共享 `RequestThrottle(1000ms)` CAS 时间片（非 FIFO）；
2. 冷启动并发面：`mobileHome`（不走节流）+ 批O `enrichFeed` 的 ~30 个 `bbs-thread` share 请求（走 `getMobileJson` → 同一节流）+ 目录请求——目录要在 ≥30 个排队请求里 CAS 抢片，等待随队列长度线性增长 ≈10s，与 Muse 真机观察吻合；热切换快是因队列已排空+10min 磁盘缓存在命中；
3. `HupuCache` 对 `/all-gambia` 走 LIST_TTL=10min——重启间隔 >10min 必走网络（280KB，秒级）；磁盘缓存在但短命；图标走 Coil 磁盘缓存，非瓶颈。

**修复方向（批Q-1）**：目录解析结果（`List<HupuCategory>`，≈254 项、目录级低频变更）以 JSON 持久化进 `HupuPrefs`——冷启动同步读盘立即上屏（<2s 达标），随后 `categories()` 原通道后台刷新合回（SWR）；节流/网络队列不动，因首屏不再依赖它。下拉刷新（`cateRefreshTick>0`）走 `refresh=true→forceNetwork` 强制刷新。不伪造时间戳：prefs 存 `savedAt` 仅作存档，上屏内容随刷新自然替换。

**导航重构挂载点清单（Q-2 范围钉死）**：
| 点 | 位置 | 处置 |
|---|---|---|
| 底栏 5 项（首页/专区/发布+/比赛/我的） | `ui/components/Glass.kt` GlassNavigation | → 3 项（首页0/专区1/我的2)，`actionIndex=-1`，删 `onCompose`/`action` 参数 |
| `when(selected)` 挂载 | `KudoApp.kt:231` | 删 `3 -> ScorePage(request)`；`4 ->` 归位 `2 ->` |
| `scoreRequest` 状态 | `KudoApp.kt:112` | 删（只服务旧 Tab 挂载） |
| 通知落点 `{ selected = 3 }` | `KudoApp.kt:178` | → `{ selected = 0 }`（路由随后盖入，行为不变） |
| 「底栏中间按钮」设置项 | `SettingsScreen.kt` 行+dialog 分支 | 删行+sheet 分支；`NavigationAction` 枚举/`DisplaySettings.navigationAction`/`navigation_action` pref 读写随 Q-2 整删（存量键无害残留，不再解码） |
| `score/*` 深链路由 | `KudoApp.kt:288` | **保留**——频道顶部比分卡目标页 |
| 频道顶部比分卡 | `SportChannelHeader`（批B 已建） | **已是官方形态 A**：`BoardMatchCard` 比分行→`score/$key?match=`、「查看近期N场热门比赛」→`score/$g`、快捷行/资讯切换——无需重建，只验回归 |
| zoneDetailOpen 药丸隐藏 | `KudoApp.kt:269` | 保留（专区仍在） |
| 版块「立即发布」FAB | `ZonePage.kt:827` | 保留——发帖唯一入口（任务指定） |
| 专区搜索 boardSearchOpen | `ZonePage.kt:116` | 不动 |
| `editor()` 其他入口 | 版块页 createTopic FAB、草稿续写 | 不动 |

## 批Q-1：专区目录持久化 SWR——冷启动立即上屏

- **`HupuPrefs` 新增 `zone_categories_v1` JSON 存储**：`loadZoneCategories()/saveZoneCategories()/zoneCategoriesSavedAt()`；codec `encode/decodeZoneCategoriesJson`（internal 可单测；无 cateId 大类/无 url 话题丢弃，坏 JSON 空表）。
- **写入点唯一**：`HupuRepository.categories()` 解析成功即落盘——`ZonePage` 与 `CommunityRepository.categories()` 共用通道自然受益。
- **SWR 失效策略**：缓存**不设硬龄**——目录级低频变更；每次进专区 `LaunchedEffect(cateRefreshTick)` 先 `loadZoneCategories()` 同步上屏（`loading=false` 达标冷切 <2s），随后 `repo.categories(refresh=tick>0)` 原通道后台合回自然替换；下拉刷新 tick>0 → `refresh=true→forceNetwork` 强制网络、不读缓存；`savedAt` 仅排查用不作文末展示（不伪造新鲜度）。
- **不绕节流**：`fetchHtml` 全局 1s 节流/10min HTML 缓存均不动——冷启动首屏不再依赖该通道，排队影响降级为后台刷新延迟。
- ZonePage 改动仅 `LaunchedEffect` 内 6 行播种；骨架仅在没有盘缓存时出现（同原语义）。
- 测试：`ZoneCategoriesCodecTest` 3/3（roundTrip/坏条目丢弃/坏 JSON）。

## 批Q-2：底栏三项导航——官方形态 A

- **底栏**：`GlassNavigation` 5 项（首页/专区/发布+/比赛/我的）→ **3 项（首页 0/专区 1/我的 2）**；`onCompose`/`action` 参数删除，`LiquidBottomTabs(actionIndex=-1)` 让拖拽落点直接选中（`candidate != -1` 恒真、preview 恒发，无动作槽特判）。签名变 `(selected, onSelect, backdrop, mode, modifier, homeHasUpdates, unreadCount)`。
- **未读徽标随迁**：`MessageCountBadge` 原挂已删「消息」槽位导致死参——现挂「我的」图标右上（index 2，`unreadCount>0` 才渲染，sampling 态不重复打 tag），未读可见性不回退。
- **比赛迁移**：`when(selected)` 删 `3 -> ScorePage` 挂载与 `scoreRequest` 状态；赛程能力全部走 `score/*` 深链——频道顶部 `SportChannelHeader`（批B：`score/$key?match=`/`score/$g`/`standings/$g`/`dtab`）+ 热榜快捷行 `score/common`，即任务指定「频道顶部比分卡」形态，数据源零改动。
- **发帖**：底栏 `+`/搜索动作删除；唯一入口 = 版块页红色「立即发布」FAB（`ZonePage.kt` 登录门控不变）+ 草稿续写 + `compose?` 深链。
- **死参清理**：`NavigationAction` 枚举、`DisplaySettings.navigationAction`、`navigation_action` pref 读写全删（`DisplaySettings` 调用点全具名参数，删中间位参安全；存量 pref 键无害残留）。SettingsScreen「底栏中间按钮」行与 dialog 分支同删。
- **通知落点**：`IncomingNotification` 的 tab 回调 `{selected=3}→{0}`——通知路由盖入后底层 Tab 落首页（原语义=消息 Tab 已不存在）。
- **androidTest 适配**：`NavigationActionUiTest` 整删（测的是被删功能）;`CommunityUiTest`/`NavigationTapUiTest`/`NavigationMaterialUiTest`/`HomeRefreshUiTest`/`NotificationUiTest`/`DeepLinkUiTest` 按 3 项语义重写断言与手势坐标（槽位 fraction 映射 n/3，索引 4→2/3→1，`published` 计数删除）；徽章测试因徽标迁「我的」而继续有效。

## 批R-0：话题聚合页空白定性——topicId/tagId 命名空间错配

**实测证据（Resin 代理，匿名 GET，2026-10-26）**：
| 探测 | 结果 |
|---|---|
| `bbs.hupu.com/643295837.html` SSR `detail.thread.topic` | `{topicId:"502", cateId:"8", cateName:"NBA", type:1, name:"篮球资讯", url:"/502", fid:"4860"}`——Muse 看到的 chip 本体 |
| `m.hupu.com/api/v2/bbs/tagThreads?tagId=502&tagType=&page=1`（批P 现行路径） | `{"code":200,"data":{"threadList":[],"nextPage":false}}`——**空**，即真机空白根因 |
| `tagThreads?tagId=41333`（`threadKeywordData` CATEGORY tagId 对照） | `threadList` 有数据——证明 tagThreads 的 `tagId` 属于 `threadKeywordData` tagId 命名空间，与 `thread.topic.topicId` **不同名** |
| `bbs.hupu.com/502` SSR | `$$data.topic.{topic,threads,tabs}` 完整命中 `parseTopicPage` 形状：50 条/页×20 页、排序 tabs（最新回复/最新发布/24小时榜） |
| `bbs.hupu.com/502-2` | `current:2`、50 条——`topicPage(url,page)→"{url}-{page}"` 分页规则兼容 |
| `bbs.hupu.com/topic`（184 足球话题区 `url:"/topic"` 特例） | 同样返回完整 `topicPage`（50 条×20 页）——`topic.url` 是通用落点 |

**根因**：`HupuThread.topic.topicId` 是**话题区页 id**（SSR `topic.url` 直接给出 `/{id}` 路径），批P-2 把它错接进 **tagThreads 的 `tagId`** 命名空间（该空间属于 `threadKeywordData.tagMap[].tagId`）。接口返回 `code:200 + threadList:[]`，页面呈现空态。

**修复方向（批R-1）**：chip `id` 从 `HupuIds.id("tagpage:{topicId}")` 改为 `HupuIds.boardId(topic.url)`（`board:` 键）→ `topics()` 自动走 `topicPage(url)` 专区话题页通道，分页/排序 tab 数据齐备；`topic.url` 空时回落 `"/{topicId}"`。正文 `?tagId=`/`/tag/{id}` 链接的 `tagpage:` 通道**保留**——那才是 tagId 命名空间的正确用户。

## 批R-2：正文站内蓝链可达性核查与专区路径接线补齐

**四条分发的端到端结论（代码路径 + 匿名实测）**：
| 目的地 | 分类器输入（实测形态） | openLink 路由 | 匿名数据源 | 结论 |
|---|---|---|---|---|
| Thread | `/{tid}.html`、`/all-nba/{tid}.html`、`m.hupu.com/bbs/{tid}[.html]` | `topic/{HupuIds.topicId(tid)}` | `detail.thread` SSR | ✅ 可达 |
| Board | `/502`、`/topic`、`/all-gambia`、`m.hupu.com/bbs/{board}` 单段 | `category/{boardId(url)}` | `topicPage` $$data（/502 实测 50×20 页） | ✅ 可达 |
| User | `my.hupu.com/{euid}`（@mention `class="u"` 真实标记）、`m.hupu.com/user/{euid}` | `user/{euid}` | `m.hupu.com/user/{euid}` SSR `userInfoData`+`threadList`（euid=22230928576422 匿名实测有数据） | ✅ 可达 |
| Tag | `/tag/{id}`、`?tagId=`、`huputiyu://bbs/subject/{id}` | `tag/`→`tagpage:{tagId}`→`tagThreads` | `tagThreads?tagId=41333` 实测有数据 | ✅ 可达（tagId 命名空间正确） |

**发现并修复的真实缺口**：专区路径（`hupuui` 详情，`HtmlContent` 供 ThreadDetailHeader/Floor/Reply 用）的 `LinkAnnotation.Url` 依赖 ambient `LocalUriHandler`——全应用无自定义 provider，点按直跳**系统浏览器**，四类分发在该渲染器不可达。修复：KudoApp 的 `ZonePage()` 挂载点外包 `LocalUriHandler provides {openLink}`，专区下所有正文链接/话题词/@用户随即走 `openLink`→`HupuLink` 分发；首页 RichContent 路径自供 `linkHandler` 不受影响（本就可达）。外链行为不变：非虎扑域仍由 `openLink` 落 `fallbackUrl→openContentLink`。
