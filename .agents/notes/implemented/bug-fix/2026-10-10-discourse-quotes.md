# Agent Note: Discourse 引用分组与头像隔离

Status: implemented

## Problem

真实话题 3002436 的第19楼使用 aside.quote，title 中包含24像素头像与作者，blockquote 包含引用正文。当前解析器逐子节点展开引用，头像进入正文大图和相册，作者与正文分别生成灰色块。

## Decision

引用作为独立 Quote 块保留作者、头像、原帖地址和子块；普通 blockquote 也整体分组。站点引用标题中的头像只在小尺寸标题栏显示，不进入图片相册。正文继续使用原生文字、表情、代码和图片组件。标题支持折叠与跳转原帖，默认展开。

相册递归收集引用正文图片，按出现顺序保留重复地址，整条回复仍使用一个相册和一组来源锚点。折叠不能改变后续图片索引；从相册返回折叠引用中的图片时先展开其祖先引用再定位。签名保持独立渲染，尺寸由[签名真实尺寸](2026-10-10-signature-natural-size.md)接管，不因引用块类型改变规则。

折叠路径以不可变列表状态和显式列表Saver保存，状态变化替换整个列表，不在MutableState内部修改可变集合。

[details 折叠](2026-10-10-discourse-details.md)使用独立容器与 open 属性，复用同一不可变路径和相册祖先展开机制；引用仍默认展开。

## Existing note audit

- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇补齐引用的结构语义，保留其他块的原生渲染。
- [行内表情](../../implemented/bug-fix/2026-10-09-inline-emoji-images.md)：部分重叠，引用正文继续保持表情在行内。
- [分组相册](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)与[来源动画](../../implemented/feature/2026-10-09-image-origin-animation.md)：部分重叠，保留每次出现位置与按楼层分组的契约，补齐嵌套引用遍历。
- [用户装饰](../../implemented/feature/2026-10-10-user-decoration.md)：部分重叠，签名继续使用自己的尺寸与开关。
- 未发现同主题 proposed 或 rejected；无完全吸收或过时提案。

## Alternatives considered

- 只跳过 img.avatar：改动最少，能修复巨型头像；但作者和正文仍拆成不同灰块，不能保留引用折叠及原帖定位，使用完整分组。
- 让引用使用单独 RichContent 和相册：复用容易；但会把同一楼层的图片割裂，并改变图片来源索引，递归渲染共用根相册。

## Verification

真实 cooked 结构的3项复现测试先失败（artifacts/quote-parser-repro.txt）。修复后169项JVM单测通过（artifacts/quote-build.txt），其中QuoteParserTest共7项，覆盖多段、裸文本、嵌套引用、表情、正文图片、重复地址、非法元信息及onebox；ContentParserTest保留普通引用和引用内表情图片的原有覆盖。

3项新增真机测试通过（artifacts/quote-device.txt），验证20dp头像、共同引用区域、原帖跳转、折叠状态恢复，以及折叠两层引用后从相册返回第二次重复图片并展开祖先。28项正文、原有相册和离线SVG签名回归通过（artifacts/quote-regression-device.txt）。quote-fixed.png为已人工查看的离线结构夹具截图；不声称打开真实帖后的阅读进度验证。

折叠状态改用不可变列表和显式Saver后，3项专项真机再次通过，纳入11项收尾验证（artifacts/quote-solved-final-device.txt）；12项引用/通知单测、Debug构建及Lint通过（artifacts/quote-solved-checks.txt），没有新增Lint警告。

## Consequences

引用标题与正文有完整视觉分组，头像不会污染正文相册；普通文字、表情和图片继续使用原生组件。代价是保留引用树及图片出现位置，所有图片提取入口统一递归遍历，包括旧会话的延迟相册读取。异常插件HTML缺少作者或楼层信息时仍显示正文，只隐藏不可用的跳转；从相册恢复引用图片先展开祖先，来源已销毁时仍按既有相册规则安全关闭。
