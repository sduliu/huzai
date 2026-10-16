# Agent Note: 图片预览的宽图与长图标识

Status: implemented

## Problem

等比限高后的长图需要提示读者点击看全图；列表的固定裁切区域也无法表达原图形状。用户要求参考酷安，在图片右下角显示“宽图”“长图”。

## Decision

列表角标仍使用本篇比例、颜色和位置规则，点击行为随[封面进入详情](../simplification/2026-10-10-feed-image-opens-topic.md)进入主题，不再打开或切换列表相册。

列表、正文和回复预览共用比例分类：有效宽高比大于等于 3 显示“宽图”，小于等于 1/3 显示“长图”，其余与未知比例不显示。优先采用已解码图片的真实比例，加载前可使用图片模型的比例；绝不使用列表裁切容器的 1.9 比例分类。

角标使用品牌主色 LogoYellow（#FFB003、90% 不透明度）和 Ink 深色文字，浅色/深色主题一致，位于图片右下角、距边缘 6dp，随当前预览切换而更新。用户明确要求使用酷DO 品牌黄；Ink 在黄色上的对比度比白字更适合小角标。它属于图片的可点击区域，不新增独立操作。来源图片在相册转场中隐藏时，角标一同隐藏；相册原图不叠加预览角标。

正文/回复容器先按真实比例和 300dp 上限确定大小，图片填满容器，角标使用 matchParentSize 叠加，不能反过来撑大图片尺寸。来源锚点绑定这个图片容器，使窄长图从自身位置放大和缩回。

## Existing note audit

- [正文图片](../../implemented/bug-fix/2026-10-09-article-image-layout.md)：部分重叠，复用真实比例、300dp 限高和高清缩略图，不改变尺寸决定。
- [分组相册](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)：部分重叠，列表返回当前图片后同步角标，不改变楼层分组。
- [来源过渡](../../implemented/feature/2026-10-09-image-origin-animation.md)：部分重叠，来源几何仍为图片矩形，角标与来源隐藏同步。
- [全屏手势](../../implemented/feature/2026-10-09-image-viewer-motion.md)：无关，不改手势与大图采样。
- 头像/行内表情的角标、数据契约及客户端验证笔记无关；proposed 中没有同主题提案，rejected 目录不存在，没有完全吸收或需归档的旧决定。

## Alternatives considered

- 按裁切后的容器比例分类：不需要等待图片加载；列表所有图片都会被当作同一种形状，无法识别真实长图。
- 把角标放在图片外的正文行：文字空间充裕，不覆盖图片；会额外增高楼层且不符合右下角提示的需求，使用图片内叠加。

## Verification

列表缺少“宽图”的用例在 RMX2202 / Android13 先失败，日志 artifacts/image-badge-list-repro.txt。修复后列表宽图→长图→普通图切换与角标点击、3 和 1/3 临界值、未知/非法比例两项通过（3.924 秒），日志 image-badge-list-fixed.txt；已有来源动画像素测试也通过。位置断言读取带 padding 的角标容器，文字语义矩形只含字形排版区，不能当作角标外框。

正文宽图、回复长图和窄长图转场三项先复现缺少角标，日志 image-badge-article-repro.txt。转场用例对未合并语义树的存在断言不适用于保留源组合的查看器，改查公开语义树和真实黑色遮罩像素。最终黄色版本的 ImageGalleryUiTest / TopicDetailUiTest 共 25 项全部通过（46.778 秒），日志 image-preview-final-instrumentation.txt；96 项 JVM、Debug APK / 测试 APK 构建和 Lint 通过（0 errors / 28 existing warnings），日志 image-preview-final-build.txt / image-preview-final-apk-build.txt。深色 130% 字号回复图和正文宽图的黄色角标截图已人工查看，文件为 image-badge-reply-dark-large.png / image-badge-wide-inline.png。

真实话题 847468 的第 4 楼最终截图 image-preview-live-fourth-yellow.png 已检查：长图矩形 [48,1120][210,2020]，即 54×300dp，黄色角标矩形 [96,1948][192,2002]，右/下留白各 6dp，图片保持完整比例、左对齐。点击后打开完整原图且不残留预览角标，下拉关闭后恢复相同图片位置和黄色角标；录屏 image-preview-live-fourth-yellow-motion.mp4 的抽帧图、原图画面 image-preview-live-fourth-original.png 和返回截图 image-preview-live-fourth-returned.png 均已人工检查。线上取源证据见 image-preview-source.json，夹具测试不代替线上内容验收。

## Consequences

图片形状在固定裁切的列表中仍可识别。首次拿不到图片尺寸时角标会在解码后出现；更极端的窄长图空间有限。角标会覆盖少量预览像素，完整原图可在查看器阅读。列表预览改变时必须丢弃上一张的解码比例，避免错误标签。
