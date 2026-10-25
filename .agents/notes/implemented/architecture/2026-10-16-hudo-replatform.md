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
