# Agent Note: BBCode 彩色文字保留到原生正文

Status: implemented

## Problem

网站将 color BBCode 输出为带 color 样式的 span，现有内联解析只保留文字而丢弃颜色。

## Decision

用户确认补齐文档中的红字和十六进制颜色。只解析内联 CSS color 属性，支持 CSS 命名色、短/长十六进制与 rgb / rgba，保留嵌套粗体、链接、表情和剧透。非法值忽略，仍显示正常正文；不执行其他样式。颜色数据为静态 CSS 标准命名色表，无运行时依赖。

## Alternatives considered

- 调用 Android Color.parseColor 简洁，但命名色范围和 CSS 十六进制透明度语义不完整，纯 JVM 测试也无法验证真实解析，采用平台无关的受限颜色解析。
- 用 WebView 渲染可完全复用浏览器 CSS，但会改变现有正文布局、链接和图库契约，本需求只需要保留文字颜色。

## Verification

MarkdownInlineParserTest 覆盖站点实际红字、橙字输出、粗体及链接、命名色、短十六进制、CSS 尾部透明度、rgb / rgba 和非法值回退；与 ContentParserTest、SpoilerParserTest 一起通过（`artifacts/markdown-colors-unit.txt`）。静态表包含148种 CSS 命名色。RMX2202 / Android 13 的 MarkdownContentUiTest 在浅色和深色130%字号下验证富文本注解、红色与橙色实际像素及彩色链接回调；与原有引用、剧透和链接共17项相关真机用例全部通过。截图 `artifacts/markdown-colors-dark-large.png` 已查看。

## Consequences

作者指定的颜色可能在某种主题下对比度不足，沿用原帖颜色语义。只接受明确颜色值，不接入 CSS 变量和任意 CSS 表达式。
