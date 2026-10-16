# Agent Note: 正文脚注在液态弹窗中阅读

Status: implemented

## Problem

文档的就地脚注与编号脚注都输出 footnote-ref 和独立 footnotes-list。现有解析把引用变成站点根路径链接，定义则重复显示为普通有序列表。用户确认用上标编号打开液态弹窗，关闭后保留阅读位置。

## Decision

从每条 cooked 正文提取脚注定义并移除回链，正文渲染跳过定义容器和专用分隔线。引用使用上标 Clickable 注解，在当前正文定义范围内查找，缺失定义保留编号且不导航。根 ArticleFootnoteHost 在全文列表之外承载 LiquidModal，内容复用 RichContent；链接沿用源正文回调。弹窗期间阻止阅读同步，不修改列表滚动状态。正文离开组合时关闭其脚注。

## Related notes audit

检索脚注、footnote、正文链接和弹窗宿主，命中正文网页分流笔记（部分重叠，脚注必须先从普通 URL 路由中分离）和恢复阅读笔记（仅背景相关，不改恢复策略），无既有脚注提案需要迁移。

- [正文链接](2026-10-10-in-app-web-links.md)
- [恢复阅读](2026-10-11-topic-resume-reading.md)

## Alternatives considered

- 页面底部锚点跳转可以复用网页交互且不需要宿主，但长帖跳转会丢失上下文，也不符合已确认的弹窗要求。
- 在每条 RichContent 内直接放 LiquidModal 接入最少，但 LazyColumn 会按帖子高度约束弹层，无法可靠覆盖全屏，因此使用独立根宿主。

## Verification

先通过现有接口复现两项问题（上标丢失并生成网页 URL、定义重复显示），日志为 `artifacts/markdown-footnotes-repro.txt`。MarkdownFootnoteParserTest 覆盖实际独立 ol 结构、富文本与回链移除、重复引用、不同正文同名编号和缺失定义；与颜色、列表、ContentParser 和 SpoilerParser 共40项 JVM 测试通过（`artifacts/markdown-footnotes-unit.txt`）。

RMX2202 / Android 13 的 MarkdownContentUiTest 验证弹层超出短 LazyColumn 项高度、阻止背景阅读同步、富文本、关闭/系统返回/链接点击后滚动索引与像素偏移不变、重复引用、缺失定义、跨正文编号隔离、最新链接回调和来源消失自动关闭。与原有引用、剧透和链接共17项相关真机用例通过（原有12项见 `artifacts/markdown-device-tests.txt`，新增5项见 `artifacts/markdown-device-tests-fixed.txt`）。

应用内液态弹窗有 dialog 语义但没有独立 DialogWindowProvider，截图使用 UiAutomation 全屏采集。夹具的来源层填充真实页面背景，防止透明夹具叠加未模糊的底层文字；两项脚注用例再次通过（`artifacts/markdown-footnote-material-tests.txt`）。浅色、深色130%截图 `artifacts/markdown-footnote-light.png` / `artifacts/markdown-footnote-dark-large.png` 已查看。

Debug 和测试 APK 构建、Lint 通过，Lint为0 errors / 44 warnings / 1 hint（`artifacts/markdown-ui-final-build.txt`）。使用与真机一致的0.2.2-dev / 2005及既有Debug签名，App使用 adb install -r、测试包使用 -r -t 覆盖安装；未卸载、未清数据。真实应用当前为访客，线上搜索要求登录；语法验收使用已从网站 cook 核对的实际结构夹具，不发布社区内容。

## Consequences

离线或非标准 HTML 可能缺失定义；此时只显示编号。脚注继承原帖颜色，作者颜色的主题对比度限制与普通正文一致。全局宿主只显示一个脚注，定义和路由随发起正文一起持有，不建立跨帖编号缓存。
