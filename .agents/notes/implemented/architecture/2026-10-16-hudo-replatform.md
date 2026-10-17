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
