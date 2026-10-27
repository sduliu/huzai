# Agent Note: 批X12 专区顶栏 tab 竖排换行修复（IntrinsicSize 语义误用）

Status: implemented

## Problem

批X10 通过溢出截断（关注露半字）与长下划线修复后，Muse 真机实测发现新缺陷：「广场」「关注」两 tab 各自竖排两行（广/场、关/注），官方为单行并排。

## Root cause

X10-1 为把下划线收到文字宽，给 `ZoneTopTab` 的 Column 加了 `width(IntrinsicSize.Min)`。语义误用：Column 的 **最小**固有宽 = 子项最小固有宽最大值，而 Text 的最小固有宽 = 最大不可断单元 = **单个 CJK 字宽（~17dp）**。于是列宽=一字宽，文字逐字换行；下划线 `fillMaxWidth` 跟随列宽只剩一字宽——视觉上误以为是「短线已修」，实为换行的同根症状。

## Decision

`IntrinsicSize.Min` → `IntrinsicSize.Max`：列宽=文字完整固有宽（「广场」2字@16sp≈32dp）。文字单行、下划线 fillMaxWidth 仍盖字宽短线、两个 tab 互不侵占。顶栏构图（logo 左、tab 对夹中、搜索钮右）与 X10-1 的溢出修复语义均保留。

## Alternatives considered

- 去掉 intrinsic 宽度让 Column wrap-content：行为等价（wrap-content 即按最大固有宽定宽），但显式 `IntrinsicSize.Max` 对「列宽必须等于文字宽、下划线随列宽」的意图更可读，且防御未来改父级约束。
- `fillMaxWidth` 下划线改 `matchTextWidth`/onGloballyPositioned 量文本宽：过度工程。
- 缩小字号/手动定宽：硬编码 dp 不随字体缩放，弃。

## Consequences

- 单点改动：`ZonePage.kt` ZoneTopTab 一处 modifier。专区左栏/网格/默认推荐/扁平项/关注切换零触碰。
- 教训沉淀：Compose 中用 IntrinsicSize 约束「跟随文字宽」须用 Max 非 Min；Min 对含 Text 容器语义是「最窄不可断宽」，几乎从不是想要的。
- 回退：git revert `af843aa`。
