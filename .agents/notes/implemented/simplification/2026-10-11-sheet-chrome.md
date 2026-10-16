# Agent Note: 普通底部面板移除重复关闭区

Status: implemented

## Problem

普通选择、资料和表单面板已有手柄、遮罩和返回关闭，标题与底部取消仍占用空间。用户要求更多操作类保持原样。

## Decision

CommunitySheet默认隐藏可见标题及动作区，保留paneTitle语义。LiquidModal底部模式默认同样精简，居中确认不受影响。帖子更多菜单显式保留取消，用户主页更多菜单保留现状标题；忽略表单保留确认按钮，发帖、删除、邀请、聊天等业务按钮继续存在，忙碌态保持关闭锁。独立徽章详情以内容展示名称，避免移除标题后丢失身份。

## Existing note audit

[酷安面板](../../implemented/feature/2026-10-10-coolapk-sheets.md)部分重叠，本篇替代统一标题和取消区，保留手势、安全区与焦点契约；[酷安表单](../../implemented/feature/2026-10-10-coolapk-forms.md)继续约束实际提交。无完全吸收或过时提案。

## Alternatives considered

- 每个调用都显式隐藏标题和取消，可避免默认值变化，但容易漏掉新增面板，采用精简默认及更多菜单显式例外。
- 根据“取消/关闭”文字在共享按钮中过滤可少改调用点，但无法判断更多菜单例外且会留下空动作区，采用面板配置和业务动作显式保留。

## Verification

普通设置面板无可见标题和取消，拖动、遮罩、返回关闭仍有效；更多菜单保留取消；确认、删除与提交仍可达。

2026-10-11相关71项真机用例最终通过，分别记录于artifacts/interface-device-tests.txt、update-device-tests.txt、sheet-migration-tests.txt、interface-final-recheck.txt和switch-final-device.txt；前几轮失败的开关像素与楼中楼测试已在最后复跑修正。楼中楼采用现有预览→跳到帖子→更多操作路径，旧用例曾查找被面板隔离的后台按钮，修正测试后验证实际可操作入口。实际设置选项面板截图artifacts/interface-sheet-live.png已查看，无标题和取消区。构建及Lint通过，0错误、37条现有警告及1条提示。

## Consequences

The public profile action sheet is a compact exception: it hides the visual title and presents the four existing actions as icon plus short text rows, while retaining the sheet pane title for accessibility.

普通面板减少重复关闭控件，更多菜单和居中确认保留明确取消路径。旧UI测试迁移为手势和paneTitle验证；新增面板若需业务动作区必须显式开启，不能直接批量删除居中确认的取消按钮。
