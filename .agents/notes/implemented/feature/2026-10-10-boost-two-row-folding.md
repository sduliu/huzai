# Agent Note: Boost 按实际两行折叠

Status: implemented

## Problem

Boost 短条全部展开会占据过多正文空间；固定条数不能适配窗口、字体和内容长短。

## Decision

Boost 默认最多显示两行，仅实际溢出时在末行右侧显示向下箭头；点击展示全部并改为向上箭头，支持再次收起。展开状态通过 rememberSaveable 按帖子ID隔离并保存；窗口和字号变化重新测量。短条保留纯头像、服务端表情与作者主页点击。

局部 SubcomposeLayout 测量实际宽高，按可用宽度换行。折叠的预测量节点清空无障碍语义且从不放置，第三行确定溢出后停止测量，只为可见短条建立交互节点。末行右侧预留48dp箭头，长条必要时缩窄换行，避免只剩一行短条；展开时箭头若放不下则另起一行。排列通过 placeRelative 服从布局方向。

## Existing note audit

已检索 Boost、表单与回复时间线。[帖子Boost](2026-10-10-post-boosts-and-length.md)部分重叠，发送、头像和短条内容继续有效，仅增加展示折叠；[酷安表单](2026-10-10-coolapk-forms.md)和[回复时间线](2026-10-10-reply-timeline-and-preview.md)的面板与真实楼层目标不变。无过时提案。

## Alternatives considered

- 固定显示若干条实现最少，但内容长短、窗口和字号决定真实行数，无法保证两行，采用实际布局行数。
- 裁切全部短条高度最简单，但会留下半条内容和不可见的交互节点。普通 Layout 仅不放置隐藏内容仍留下零尺寸语义节点（boost-fold-layout-device.txt），采用清空预测量语义及按可见项子组合。
- FlowRow 溢出API能直接复用行数限制，但1.10.5的加权箭头在measureAndCache分支未初始化ellipsisSize，真机3项测试触发NullPointerException（boost-fold-device.txt）；API已停止维护，采用局部布局，避免补丁库或改变短条间距。

## Verification

TopicBoostsUiTest 的原实现复现测试因没有隐藏溢出条目而失败（boost-fold-repro-device.txt）。真机覆盖实际两行、全部展开和再次收起、箭头靠右、短列表无箭头、逐帖独立、260dp深色130%字体、16字长条与状态恢复/窗口变化；同类回归继续检查头像、点击作者、输入校验、发送目标和爱心/火箭。

11项真机测试通过（boost-fold-regression-device.txt），构建通过；boost-fold-320.png 与 boost-fold-260.png 确认浅色常规及深色窄屏大字均为两行，箭头靠右。

## Consequences

正文空间更稳定，隐藏条目不占交互与无障碍入口；折叠只预测量前两行和第一个溢出项。可见条目在折叠状态需要两次组合/测量；展开仍按全部数量线性处理。长条允许自身换行，不截断服务端内容；窄窗口的末行可能因箭头腾位减少短条数量。
