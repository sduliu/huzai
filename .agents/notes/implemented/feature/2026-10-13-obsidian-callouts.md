# Agent Note: 支持 Obsidian Callout 正文

Status: implemented

## Problem

正文中的 Obsidian Callout 语法（例如 `> [!note]`）当前被当作普通引用文本显示，用户会看到原始标记和引用前缀，无法获得网页端的彩色标题、内容区域和折叠行为。

## Decision

在现有 `ArticleBlock` 解析和 Compose 渲染链路中增加 Callout 块。识别 cooked HTML 首段第一行的 `[!type]`，同时支持独立段落和实际站点使用的 `<br>` 换行；只移除标题行，保留同段正文的富文本和后续块。支持标准类型、Obsidian 类型别名、自定义标题及 `+`/`-` 折叠标记；只有显式折叠标记才显示折叠按钮。按文档区分蓝色 Note、绿色 Tip、黄色 Warning / Caution、红色 Danger 等颜色。普通引用保持原逻辑。Callout 的正文、图片、代码和链接继续复用已有递归渲染能力，用户签名中的 Callout 只渲染正文内容。

## Alternatives considered

- 在 `parseInlineArticle` 中仅隐藏 `[!note]` 文本：无法提供块级背景、标题图标和折叠内容，也会丢失类型语义。
- 只识别服务端专用 Callout HTML class：当前服务端返回的原始 Markdown 会被转成普通 blockquote，不能覆盖现有帖子。

## Consequences

- Note、Info、Warning、Todo、Tip 等类型能从 `[!type]` 标记解析为 Callout。
- 标记不再作为正文显示，正文、代码块和图片仍正常解析。
- 自定义标题及折叠状态可用，普通 blockquote 行为不变。
- 单元测试覆盖解析边界，Compose 测试覆盖显示和折叠。

实际帖子 964458 的主帖使用 `<p>[!note]<br>正文</p>`，旧测试只有独立段落，未覆盖真实输入。回归夹具包含真实换行、自定义标题、正文富文本、代码块和普通引用边界；后续新增语法须核对实际 cooked HTML。若服务端改为专用 HTML 结构，需要补充对应 DOM 适配。新增块参与图片路径和折叠状态计算，折叠状态在正文重组后仍按路径保存。

## Testing

`ContentParserTest.obsidianCalloutsBecomeTypedBlocksAndOrdinaryQuotesStayQuotes` 覆盖类型、正文、自定义标题、折叠标记和普通引用边界。`SpoilerContentUiTest.obsidianCalloutRendersTitleAndCanBeExpanded` 在 RMX2202 / Android 13 真机上通过，验证标题、隐藏正文和展开/收起交互。

真实同段换行夹具 `actualSiteCalloutKeepsSameParagraphBodyAndCode` 在修复前失败（`artifacts/markdown-callout-repro.txt`）；修复后 ContentParserTest、QuoteParserTest、SpoilerParserTest 全部通过（`artifacts/markdown-callout-fixed.txt`），覆盖显式折叠、别名、普通引用以及 `<br>` 后格式化空白不进入正文。RMX2202 / Android 13 上的 MarkdownContentUiTest 验证真实换行正文、代码块和仅显式标记可折叠；与原有引用、剧透和链接共17项相关真机用例全部通过。实际截图 `artifacts/markdown-callout-real-linebreak.png` 已查看。
