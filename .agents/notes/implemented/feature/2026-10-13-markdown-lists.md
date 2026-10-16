# Agent Note: 正文有序列表、嵌套列表与清单

Status: implemented

## Problem

正文将所有 li 转成同一种圆点文本，丢失有序编号，嵌套内容被拼接到父项；站点的清单 span 没有文本，勾选状态也丢失。

## Decision

用户确认按文档 5.1.4 至 5.1.5 补齐阅读渲染。引入保留项目子块的 ListBlock，各项保存编号或圆点、可选勾选状态和正文块。识别站点实际 chcklst-box / checked / permanent 输出以及标准 checkbox；只读取当前项目的状态，不能从子列表误取。正文、图库、折叠、目录收集和签名递归遍历同一结构。清单展示原帖状态，不在阅读界面修改原帖。

## Alternatives considered

- 给旧文本添加编号和空格改动较少，但无法可靠保留嵌套图片、段落、折叠和标题的布局及路径，因此采用结构化项目。
- 在阅读时临时切换清单看起来方便，但与服务端持久状态和编辑权限不一致，用户确认的是勾选状态渲染，不增加原帖写入。

## Verification

MarkdownListParserTest 覆盖有序编号、start / value、三级混合嵌套、站点三种清单输出、父项不能误取子项勾选状态及嵌套图片顺序。与 ContentParserTest、QuoteParserTest、SpoilerParserTest 一起通过（`artifacts/markdown-lists-unit.txt`）。站点客户端实际 cook 结果保存在 `artifacts/markdown-syntax-preview.json`。RMX2202 / Android 13 上的 MarkdownContentUiTest 在360dp窄屏、深色130%字号下验证三级缩进、行序、编号和清单语义；与原有引用、剧透和链接共17项相关真机用例全部通过。截图 `artifacts/markdown-lists-dark-large.png` 已查看，三级内容完整。

## Consequences

新增块必须同步图库、标题和折叠的递归路径，避免列表中图片索引与显示不一致。嵌套使用标记列和正文列自然缩进，极端深度仍可能挤压小屏宽度；真机检查文档中的三级列表。
