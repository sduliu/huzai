# Agent Note: 当前图片与来源位置之间的缩放过渡

Status: implemented

## Problem

覆盖层瞬间切换缺少用户要求的空间关系：第1张打开、翻到第3张后，需要向第3张的位置缩回。列表 Crop 和正文 Fit 的显示范围不同，简单缩放整个页面会拉伸图片或在结束时突然跳变。

## Decision

列表入口和列表单预览位定位已由[封面进入详情](../simplification/2026-10-10-feed-image-opens-topic.md)部分取代；本篇正文／回复来源定位、过渡和旧会话恢复继续有效。

来源记录真实窗口矩形、可见裁切区域、圆角与已经显示的 Painter。进入前保存来源几何，避免隐藏系统栏改变坐标。图片内容矩形与容器裁切矩形分别插值，使列表裁切平滑展开为完整大图；背景与圆角随同一进度过渡。

返回先锁定当前页、恢复系统栏和定位来源，再从当前缩放/拖动状态过渡到当前图片的目标矩形。单预览位列表先同步当前图片；正文按出现索引查找，重复 URL 仍缩回正确位置。转场期间隐藏重复来源并隔离输入；完成后立即还原来源。源项失效或图片尚未可绘制时淡出，系统关闭动画直接完成定位。

进场280ms、退场240ms，使用先快后慢的缓动；系统动画缩放为0时立即完成。Painter只在来源和已组合的相册页之间复用，页面销毁即清理引用。异步相册补齐必须保持图片、变换与当前页的一致快照，边界见[相册会话](2026-10-09-image-gallery-and-transitions.md)。

## Existing note audit

- [相册会话](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)：部分重叠，沿用分组、当前页与返回目标。
- [全屏手势](../../implemented/feature/2026-10-09-image-viewer-motion.md)：部分重叠，读取触点变换并沿用系统栏还原。
- [正文图片](../../implemented/bug-fix/2026-10-09-article-image-layout.md)：部分重叠，保留真实比例。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，几何/手势断言与同机录屏证据分别记录。
- 上一篇手势提案中的来源几何部分移至本篇；无其他过时提案。

## Alternatives considered

- 对覆盖层整体 scaleIn/scaleOut：代码少；无法表示不同来源位置以及 Crop 到 Fit 的裁切展开。
- 仅用首次点击项的共享元素：可复用导航动画；第3张返回时仍需来源定位，不能把初始索引当作退出目标。
- 将所有转场交给 SharedTransitionLayout：适合同时存在、布局固定的匹配元素；这里来源位置先由相册协议改变，返回起点还包含手指缩放和平移，直接对内容与裁切矩形插值更明确。

## Verification

GalleryTransitionTest的3项验证Crop展开不变形、部分可见来源与窗口偏移，以及放大后从真实显示矩形退出。ImageGalleryUiTest读取实际彩色图片像素，在第1张进入和第3张返回的首帧、中间帧、末帧比较边界；同组覆盖正文、评论、列表、来源失效、状态恢复和原图重试。完整93项JVM和41项真机回归通过，真机85.027秒，日志为 artifacts/gallery-final-build.txt / gallery-final-instrumentation.txt。额外的补齐相册后不翻页直接关闭专项通过，2.619秒，日志 gallery-retained-painter-check.txt。

系统关闭动画后5项通过，9.794秒，日志 gallery-reduced-motion.txt；三项系统缩放恢复为1，记录 gallery-animation-settings.json。恢复时Android CLI一度只返回状态栏节点，改用已查看的系统截图定位开关，未通过修改测试结果绕过恢复检查。

RMX2202 / Android13同机酷安参考为 gallery-coolapk-motion.mp4。真实帖子《秘密花园园丁邀请函》的 gallery-live-detail-motion.mp4 与 gallery-live-list-motion.mp4 均完成第1张进入、第3张下拉返回；gallery-live-entry-frames.png / gallery-live-exit-frames.png 已逐段查看。正文当前图完整落在底部操作栏上方，列表返回后更新原预览位。gallery-live-inline.png / gallery-live-return-third.png / gallery-live-information.png 分别记录正文比例、返回位置和长按附件信息。编号彩色图片及1688×339横图是测试夹具，未把它们作为线上内容证据。

## Consequences

[引用分组](../bug-fix/2026-10-10-discourse-quotes.md)在定位引用内图片前展开全部折叠祖先，继续按出现索引返回重复图片的正确位置。

图片与来源建立连续的空间关系，来源页保留滚动和交互状态。每次进出需要保存几何并额外绘制一层当前图片；原图未解码或来源消失时安全淡出。Painter复用不替代原图缓存、采样限制和动图生命周期。录屏验证当前真机上的动作和视觉连续性，不推定酷安内部参数、稳定帧率或其他设备表现。
