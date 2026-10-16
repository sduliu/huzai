# Agent Note: 正文代码块复制

Status: implemented

## Problem

正文代码只能逐字选择，用户无法一键取得包含缩进和换行的整段代码。

## Decision

每个代码块右上角增加复制入口，写入解析后的原始整段文本，不走 plainText、不包含正文或按钮文案。保留原有横向滚动和手动选择；成功后短暂显示已复制。

## Related decisions

检索代码块、复制：[主题操作](../../implemented/feature/2026-10-09-topic-actions.md)部分重叠，但其复制正文采用纯文本，不能替代代码的空白保持；无同主题或过时提案。

## Alternatives considered

- 仅保留系统文字选择：没有新增控件，但长代码选择困难且不能直接取得整段。
- 复用复制正文：入口现成，但会混入其他正文并丢失代码空白，使用每块独立复制。

## Testing

ArticleContentUiTest 先因找不到复制按钮失败（artifacts/article-repro-device.txt），实现后 1 项真机测试通过（code-copy-device.txt），两个代码块分别复制并保留缩进、制表符、换行和 HTML 实体；正文不混入剪贴板。

## Consequences

长代码可以完整复制，横向滚动与手动选择保留。系统剪贴板浮层由 Android 控制；App 仅提供按钮内反馈。
