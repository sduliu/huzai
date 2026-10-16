# Agent Note: 正文图片比例与附件信息

Status: implemented

## Problem

正文将图片宽高比限制在 0.65–2.5。1688×339 的横图因此进入约两倍高的灰色容器，长图也有同类留白。Discourse lightbox 的文件名、分辨率和体积被递归解析成正文，挤占阅读空间；查看器只得到缩略图地址，丢失原图和附件信息。移除比例限制后，铺满宽度的长图又会撑高楼层并过度放大站点缩略图；《秘密花园园丁邀请函》4 楼是用户报告的实际入口。

## Decision

正文图片跟随 Coil 请求显示加载、成功和失败状态；加载时保留原尺寸和淡色底，中央使用既有黑/白/黄三段指示器，失败提供明确提示及重试。重试通过请求代次重建 AsyncImage，不能复用结构相等的失败请求；成功即移除占位底和指示器。延迟响应与首轮失败的独立图片夹具验证此行为，不向社区写入内容。

动图生命周期副作用只清理本次绑定的 Drawable，不能在旧副作用的清理回调里重新读取可变 animation 状态。加载结果替换空状态时，旧回调若停止新动图，会与解码线程异步移除监听竞态，导致启动超时或停留首帧。独立可观察的动图夹具断言前台加载期间零次停止，并继续检查后台、恢复与移除；真实 AVIF 像素用例验证逐帧播放。

图片块分别保留缩略图、原图、有效比例及 lightbox 附件信息。正文与回复用真实比例布局，预览高度最多 300dp；宽度取内容可用宽度与 `300dp × 图片比例` 的较小值，图片靠左，超高时宽高一起缩小。尺寸缺失时由解码结果补齐；不设图片最小高度，不通过 Crop 隐藏留白。相册来源记录缩小后的真实图片矩形，点击仍打开完整原图。只剔除 lightbox 的自动元信息，保留用户图注、正文和行内表情。

图片预览优先选用站点 `srcset` 中最高有效密度的 HTTPS 候选，没有合法候选时使用 `src`。支持 Discourse 的 `x` 描述符与有有效 HTML 宽度时的 `w` 描述符；无描述符按 1x 处理，无效、非正数和不安全地址忽略。原图仍来自 lightbox href，缺失时保留原 src；不会把更清晰的缩略图误作完整附件。Coil 按最终布局尺寸采样，不为正文直接下载超大原图。

非 lightbox 的独立附件保留 HTML 显示宽度，作为正文最大宽度，避免 153×153 表情动图被拉到300dp。lightbox 的 width 是站点缩略图尺寸，不能限制为90dp而损害长图可读性，继续采用预览限高规则。animated 图片保留原动画来源，不从 srcset 选择静态候选；解码结果只补比例，不覆盖作者指定的显示宽度。相册保存/恢复此尺寸字段并兼容旧的七字段记录，查看器仍展示完整原图。

## Existing note audit

- [行内表情](../../implemented/bug-fix/2026-10-09-inline-emoji-images.md)：部分重叠，继续将 emoji 与普通图片分开，不修改表情排版。
- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇细化正文图片规则。
- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，保持 HTTPS 与图片 Cookie 同源边界。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，新增横图、长图和 lightbox 结构的复现。
- proposed / rejected 中没有同主题提案；没有完全吸收或需要归档的决定。

## Alternatives considered

- 保持比例限制并改用 Crop：布局高度稳定，容易移除灰边；会裁掉用户截图，正文按真实比例展示。
- 只限高并保留满宽 Fit 容器：改动少，能压住楼层高度；两侧空白会让图片来源矩形和后续角标偏离真正的图片，采用宽高同步缩小的容器。
- 所有预览直接加载原图：清晰度最高且取源简单；4 楼原图为 1600×8828、1.13MB，而 180×1000 的站点预览足够覆盖真机 300dp 高的显示区域，使用站点预览节省下载和解码成本。
- 删除所有名为 meta 的节点：过滤简单；会误删普通正文中同名结构，只识别 lightbox 自动信息。
- 正文继续显示附件信息：无需新增查看入口；用户明确要求精简阅读区，信息迁到大图菜单。

## Verification

2026-10-12 发布回归：AVIF 用例分别出现启动超时和只采到首帧（`artifacts/image-spoiler-final-device.txt`、`article-avif-final-recheck.txt`）。新增可观察 Drawable 的专项测试稳定复现前台加载时被停止 1 次，期望为 0（`artifacts/article-animation-lifecycle-repro.txt`）。副作用捕获自身 Drawable 后，新专项、真实 AVIF 三帧与后台/恢复/移除，以及剧透、加载重试和相册等共 40 项设备回归全部通过（`artifacts/release-0.2.0-final-device.txt`，64.083 秒）。没有放宽原 AVIF 像素断言或延长超时。

2026-10-12：ArticleImageLoadingUiTest 的延迟响应和首轮失败重试两项先失败；修复后与两项平移惯性用例共 4 项在 RMX2202 / Android 13 通过（3.809 秒）。日志 `artifacts/image-polish-repro-device.txt`、`image-polish-fixed-device.txt`，Debug 和 AndroidTest 构建通过；同签名 `install -r`，未清数据。

153×153 小附件在真机先复现被拉到300dp（artifacts/article-repro-device.txt），修复后 ArticleImageSizingUiTest 验证153dp宽高与点击原图地址，日志 avif-fixed-device.txt。ContentParserTest 覆盖动画源优先、HTML宽度、lightbox缩略图宽度不误限。21项相册回归通过（avif-device.txt），原有长图、缩放、翻页与来源矩形机制保持。

最终两项 ArticleImageSizingUiTest 通过（artifacts/render-final-device.txt），包括实际正文AVIF逐帧播放和生命周期。article-avif-inline.png 已人工检查小图保持153dp方形且靠左；静态截图不作为动画播放证据。

限高复现的两项真机测试先失败：有尺寸和无尺寸的长图分别被撑高到 1640dp、1800dp，日志 artifacts/image-preview-height-repro.txt。修复后 ImageGalleryUiTest 的 16 项全部通过（31.303 秒），长图变为 60×300dp、靠左、下方内容可见，点击仍交付原图；图片翻页、缩放、返回目标及动画像素回归通过，日志 image-preview-height-fixed.txt。93 项 JVM 测试通过，日志 image-preview-height-build.txt。只读核查确认话题 847468 的第 4 楼原图为 1600×8828、src 为 90×500，证据保存在 image-preview-source.json。

预览分辨率的三项复现先失败，日志 image-preview-resolution-repro.txt；接入 srcset 后，17 项解析、6 项相册状态、3 项来源几何测试通过，日志 image-preview-resolution-fixed.txt。覆盖 90×500 到 180×1000 的实际站点结构、HTTPS 筛选、非法描述符、w 候选与低密度不降级。实际 CDN 两张缩略图均成功读取，分别为 20536 / 70243 字节；原图地址保持独立。

1688×339比例及lightbox信息泄露两项先失败，日志 artifacts/image-layout-repro.txt；修复后ContentParserTest的14项通过，日志 image-layout-fixed.txt。覆盖横图、长图、缺失/非法尺寸、原图HTTPS边界、嵌套附件信息与图注/emoji混排。

真机夹具 gallery-wide-inline.png 使用1688×339原比例，实际边界断言与截图均确认没有上下灰边，文件名/尺寸/体积不进入正文。真实帖子 gallery-live-inline.png 保留作者图注，gallery-live-information.png 将2770×1400、232KB等附件信息放在长按菜单；gallery-live-tall-motion.mp4 检查长图。图片比例、元信息和相册交互进入93项JVM与42项真机测试；完整分批结果见[来源过渡](../feature/2026-10-09-image-origin-animation.md)。

## Consequences

正文完整呈现横图与长图，附件信息不再占用阅读空间。预览高度不超过 300dp，超长图片会变窄，细节阅读依赖点击原图放大。更清晰的站点预览会增加下载字节数；最高密度仍受站点提供的资源约束，Coil 不会凭空恢复原始像素。尺寸缺失时首次解码可能触发布局调整。图片初次读取仍受网络与站点权限影响。
