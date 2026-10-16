# Agent Note: 正文保留站点表情图片

Status: implemented

## Problem

RichContent 将独立和段落内的 emoji 图片替换成 alt 字符串，因此 `:distorted_face:` 等名称直接出现在正文。2026-10-09 只读核查确认 /emojis.json 提供 distorted_face，图片返回 200、72×72 PNG；问题不需要通过更换系统字体解决。

## Decision

保留 cooked 中 img.emoji 的 HTML 和 HTTPS src，在 Compose 文本中使用随字号缩放、与文字居中的行内图片。普通正文图片仍作为独立图片块。复用现有图片加载与动画生命周期；表情有可访问名称，非法图片地址保留文字替代，不把远端内容作为代码执行。

## Existing note audit

- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，保留块渲染与浏览器回退决策，本篇负责行内表情。
- [头像显示](../../implemented/feature/2026-10-08-avatar-presentation.md)：部分重叠，复用图片解码和动画生命周期，不改变头像与角标规则。
- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，沿用 HTTPS 图片及跨域不携带会话的边界。

## Alternatives considered

- 加入 EmojiCompat 或第三方字体：可以补全旧 Android 的 Unicode 字形；但无法修复被渲染器直接输出的表情名称，也不能覆盖 L 站自定义图片，优先保留站点图片。
- 将表情当作普通正文图片：复用已有图片块最简单；会打断句子并放大到整行，不适合行内表情。

## Verification

独立 emoji 图片被转成名称的单元测试先失败，输出 artifacts/topic-emoji-repro.txt；修复后 ContentParserTest 的 10 项通过，输出 topic-emoji-fixed.txt，覆盖段落、重复表情、链接、引用、非法地址与普通图片混排。真实站点图片请求只读验证通过，字段和图片状态记录在 topic-detail-contract-review.json。

TopicDetailUiTest 在真机以确定性PNG验证行内图片完成加载、没有裸露名称、130%字号时占位矩形完整；完整28项UI回归通过，输出 topic-final-instrumentation.txt。同一SiteEmoji组件在真实回应面板中显示 distorted_face 与自定义图片，截图 topic-responses-real.png 已人工确认；[详情交互](../feature/2026-10-09-topic-author-and-responses.md)复用此组件并记录完整验收材料。

## Consequences

[完整引用](2026-10-10-discourse-quotes.md)采用分组子块，引用正文仍使用本篇行内表情规则，作者头像独立于正文图片。

图片首次显示依赖缓存或网络；失败使用带表情名称的占位。超出原生块解析范围的插件内容仍需浏览器打开。
