# Agent Note: 批X10 专区顶栏 tab 溢出 + 网格名省略修复

Status: implemented

## Problem

批 X9 有条件通过后 Muse 真机实测（2049 包，证据 `search_verify/X9_zone2.png` 对照 `SW_off_zone.png`）两处残留：

1. 顶栏「广场」居中、「关注」在右边缘被截断只露半字；选中红下划线拉满整宽而非选中 tab 文字下短线。
2. 网格版块名全量省略（步行街主…/数码综合…/湿乎乎的…），官方同机同版名完整显示。

## Root cause

- **顶栏**：`ZoneTopTab` 的下划线 `Box(fillMaxWidth)` 使所在 `Column` 被撑到 `Row` 剩余全部宽度——「广场」列吃掉整行剩余空间，「关注」与搜索钮被挤出右缘截断；下划线随列宽拉满（官方为选中文字下短线）。
- **网格名**：右栏格区 = (屏宽 − 左栏 92dp − 网格内容边距 20dp − 列间距 20dp) / 3，再减格内水平边距 12dp ≈ 64dp 文字区（~360dp 屏）；6 字名 @13sp 需 ~78dp → `Ellipsis`。

## Decision

- **ZoneTopTab**：`Column` 加 `width(IntrinsicSize.Min)`——列宽收敛为文字固有宽，内部 `fillMaxWidth` 下划线即只盖文字宽（官方形态）；下划线逻辑不变。
- **顶栏布局**：logo 左、广场/关注 tab 对两侧 `weight(1f)` 夹中居中、搜索钮右（搜索入口位置语义不变；批X9-4 的左排布局是溢出根因的一半）。
- **TopicCell/网格**：名 13→12sp、格内水平边距 6→2dp、列间距 10→8dp、内容边距 10→8dp → 文字区 ~74dp 容下 6 字名（@12sp ~72dp）。阈值口径：**~360dp 屏 ≤6 字全显，7+ 字仍 Ellipsis**（不硬缩字保可读性）。两处网格（广场带左栏/关注全宽）同步生效；logo 56dp 与 👍N万 指标行不动。

## Alternatives considered

- 下划线固定 dp 宽度（如 22dp）：对不同字长 tab 不自适应，IntrinsicSize.Min 一次到位。
- 网格名允许两行：官方单行为准，两行破坏网格等高——弃。
- 缩左栏 92dp：左栏分类名虽短可容，但收窄会引入新的截断风险且属批X9 已验版式——不动。

## Consequences

- 改动面仅 `ZonePage.kt`（ZoneTopTab + 顶栏 Row + 两处 LazyVerticalGrid 间距/边距 + TopicCell 字号边距）；赛程/详情/数据 tab/底栏零触碰。
- `ZoneTopTab`/`TopicCell` 签名未变——批X9-5 的 `ZonePageChromeUiTest`（文字 tab 点切 + 步行街主干道显示/点击）语义不受影响。
- 回退：逐项 revert（71d7511 / b59d820）。
