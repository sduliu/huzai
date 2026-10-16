# Agent Note: 全屏图片手势与窗口状态

Status: implemented

## Problem

横滑相册已经保留当前图片和来源定位，但简单切换覆盖层不能表达从缩略图放大、返回另一张图的连续关系。旧缩放围绕画布中心且拖动无边界，顶部按钮占用全屏阅读空间。

## Decision

图片平移结束后使用 Compose 密度适配的衰减动画继续滚动；纵向用于长图，横向用于已放大的宽图，普通默认比例的横滑仍由 Pager 处理。新触点、双指缩放、离页和关闭均取消旧惯性；每轴到实际图片边界立即停止，不通过回弹或缩放改变边界。拖动中的缩放语义使用派生状态，避免平移时重建图片请求或扩大重组范围。真机帧耗时仅代表实测设备与夹具，不代替一加 13 验收。

Android 15 的自适应刷新率可能使自定义内容默认按 Normal 绘制；图片触摸和惯性期间通过 Compose `preferredFrameRate(FrameRateCategory.High)` 申请高刷，结束立即移除偏好，交由系统决定实际刷新率。只改变正在移动的图片节点，不固定整个窗口或切换设备显示模式。依据为官方 [ARR for Compose](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate#arr-for-compose)；旧系统由 Compose 兼容，当前 Android 13 实测不证明 Android 15 或厂商高刷策略已生效。

沿用[相册会话](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)及同窗口覆盖层。双指缩放围绕触点中心，图片平移按实际 Fit 图片边界限制；未放大时横滑交给 Pager，向下拖动开始关闭预览，取消或距离不足恢复。单击、下拉或系统返回关闭；长按信息与浏览器入口由[图片导出菜单](2026-10-10-image-export-menu.md)部分替代，提供分享与保存。可访问操作保留关闭、翻页与缩放，背景动作不能穿透。纯黑全屏与单图不显示页码的布局来自同机酷安截图。

关闭先锁住当前索引与交互，再恢复进入前的系统栏可见性，等待布局后定位图片。正文定位矩形带入110dp底部预留，避免返回图片被玻璃操作栏遮住。单独的[来源缩放过渡](2026-10-09-image-origin-animation.md)管理进出几何，不改变手势归属。

GalleryTransformState 管理图片 Fit 矩形、缩放和平移；没有触点的结束事件不能写入 NaN。宽高比不超过1/3的长图默认放大到屏幕宽度并顶部对齐，默认比例仍允许横向翻页，纵向优先滚动内容，到顶后再次下拉可关闭。还原回到长图默认宽度；普通图保持完整适配。长图至少可在默认比例上继续放大2倍，普通图片至少5倍；默认比例不受旧20倍上限阻断。迟到尺寸只在尚未手动变换时初始化，不能覆盖用户缩放。查看器加载目标最多按视口2倍、最长4096与8M像素约束取较小值，不请求无限原始位图。动图只在当前页及前台播放，离开停止。

## Existing note audit

- [相册会话](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)：部分重叠，该篇管理分组、加载与返回协议，本篇管理手势和几何。
- [正文图片](../../implemented/bug-fix/2026-10-09-article-image-layout.md)：部分重叠，使用原图比例与元信息，不改变正文过滤。
- [原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)：部分重叠，复用 Coil 与动图生命周期。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，区分功能断言、像素截图与同机动效证据。
- 原相册提案中的手势与动画部分移入本篇，原篇保留已实现的状态边界；没有其他过时提案。

## Alternatives considered

- 直接对查看器整体做 scaleIn/scaleOut：参数少且容易与导航集成；无法从任意来源位置放大，也无法在第 3 张关闭时缩回第 3 张。
- 单独使用 Navigation shared element：能自动处理固定元素的共享动画；相册保留来源、列表换预览和拖动释放已有明确协议，采用真实边界过渡可同时覆盖 Crop / Fit 与手指位移。
- 引入完整图片浏览库：能提供成熟缩放和分块加载；来源定位仍要独立实现，本轮复用 Compose / Coil，超大长图清晰度若成为实际问题再评估分块解码。

## Verification

2026-10-12：长图纵向及放大宽图横向惯性测试先复现松手即停；修复后与两项图片加载测试共 4 项在 RMX2202 / Android 13 通过（3.809 秒），验证松手继续、新触点中断和边界约束。日志 `artifacts/image-polish-repro-device.txt`、`image-polish-fixed-device.txt`。这批功能测试不代表一加 13 已测或稳定帧率。

独立 `galleryPerformance=true` 探针对本地生成的 1600×12000 / 12000×1600 PNG 进行真实墙钟触摸，加载完成后收集 FrameMetrics，不把解码混入拖动样本。RMX2202 / Android 13 / 60Hz 的长图 144 帧 P95 6.055ms、最大 9.382ms；宽图 138 帧 P95 5.406ms、最大 6.571ms，均无 TOTAL_DURATION 超 16.67ms 样本。两项通过（10.413 秒），不把夹具样本当成所有真实图片稳定帧率或输入延迟结论。日志 `artifacts/gallery-frame-probe-device.txt`，无身份 JSON 位于设备私有目录。

加入交互期间高刷新提示后的两项探针再次通过（`artifacts/gallery-frame-probe-final-device.txt`，10.166 秒）：同机仍为 60Hz，长图 144 帧 P95 6.090ms、最大 11.356ms；宽图 138 帧 P95 5.740ms、最大 8.717ms，均无超预算样本。46 项相关 JVM、Release R8、Lint 通过，最新 39 项图片/正文设备回归通过（`artifacts/image-spoiler-final-build.txt`、`image-spoiler-final-device-recheck.txt`）。高刷请求不是强制显示模式，Android 13 的结果不能验收一加 13 的厂商调度。

GalleryTransformTest 中最后一指抬起引入无效 Offset 的测试先失败，日志 gallery-gesture-repro.txt；修复后4项几何和6项相册状态通过，日志 gallery-gesture-fixed.txt。正文返回图被底栏遮住由 gallery-ui-fixture-fixed.txt 单独复现，增加预留后 ImageGalleryUiTest 的8项真机测试全部通过，17.747秒，日志 gallery-gestures-instrumentation.txt。覆盖正文/评论/列表当前图返回、真实宽图比例、中心缩放和拖动、下拉取消/关闭、长按信息及状态恢复后系统返回。

首次设备截图失败来自 Android CLI 的 UiAutomation 会话未释放，停止 com.android.cli.interact.instrumentation 后重跑；测试活动显式启用与产品相同的 edge-to-edge。短拖夹具最初停住600ms再一次移动，实际触发长按；改为持续移动及真实取消，未放宽产品判定。截图为 gallery-wide-inline.png、gallery-third-fullscreen.png、gallery-return-third.png、gallery-list-third.png；同机酷安参考为 gallery-coolapk-inline.png / gallery-coolapk-fullscreen.png。

原图重试不生效由 gallery-transition-instrumentation.txt 复现：结构相等的Coil请求被复用。用重试次数重建AsyncImage后通过；离开页面清理Painter回调，避免会话保留全部已看大图。最终93项JVM、41项完整真机回归及额外1项补齐后直接退场专项通过；关闭动画5项通过，三项系统缩放恢复为1。真实正文和列表第1张进入、第3张下拉返回，以及长图、纯黑全屏和长按信息均已截图或录屏查看；完整证据见[来源过渡](2026-10-09-image-origin-animation.md)。

## Consequences

2026-10-11：两项长图默认宽度/迟到解码 JVM 复现先失败（long-image-repro-20261011.txt），修复后几何、过渡和相册状态回归通过（long-image-fixed-20261011.txt）。ImageGalleryUiTest 的22项及两项总结布局真机用例通过（long-image-floor-device-20261011.txt）；该批唯一失败是尚未修改的楼层粗体复现。长图用例验证实际像素铺满、顶部对齐、向上阅读与默认缩放下横向翻页。

图片占满可用画布，附件元信息不占阅读画面；缩放不再把画布空白当成可拖动图片。长图和放大采样增加解码内存，约束目标尺寸不会提供无限原像素细节。系统栏隐藏与恢复依赖宿主窗口；手势与真正动画需共同回归。
