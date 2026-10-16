# Agent Note: 酷安式举报与编辑表单

Status: implemented

## Problem

举报仍在玻璃浮动面板内使用 Material 单选和描边输入，发帖与回复共用全屏描边表单。用户提供酷安整页举报、开放正文发帖和键盘上方回复面板截图并批准迁移。

## Decision

设置页普通行最小52dp，分组间隔10dp，白色组内细分隔线；长文字和大字自动撑高。退出登录按钮最小48dp，垂直内边距8dp，正常字号从64dp缩至48dp，大字体按内容撑高，保留红字及退出确认。CommunitySwitch的视觉轨道为42×26dp，48dp触控区保留，关闭滑块14dp、开启22dp，以同一200ms进度同步位置、大小与轨道颜色，遵循系统动画时长。开启用LogoYellow，关闭中性灰，禁用降低整体不透明度；不引入Material默认开关外观。

Foundation 输入和自绘单选、开关、扁平按钮形成统一控件。举报使用独立整页，分组原因行、无描边说明与底部提交，继续消费网站动态原因、字数、违法确认和写入锁。发帖用分类选择行、较大标题、开放正文与底部工具条；分类通过新底部选择面板。回复在话题上方以 CommunitySheet 展示，进入聚焦并显示键盘，保留实际楼层作为发送目标，关闭保存草稿。

回复编辑器在话题导航条目内承载，发布成功刷新该条目并触发首页缓存失效；新的话题仍走独立 compose 页面。上传、Markdown 工具、按用户/话题/楼层隔离的草稿及网站写入逻辑继续复用。发送中禁止关闭以保留当前提交归属，失败保留内容。

同一话题会复用编辑器 ViewModel，成功后同时清理落盘和内存草稿、恢复发送状态；否则再次展开会显示已发送正文且仍被锁定。发布请求允许注入夹具以验证真实 ViewModel 生命周期，不对社区写入。楼中楼点击回复先关闭讨论面板，再打开回复编辑器，避免旧焦点锁阻挡输入。

## Related decisions

[账号与编辑器](../../implemented/feature/2026-10-07-account-and-composer.md)部分重叠，保留草稿、上传、登录和写入契约；[主题操作](../../implemented/feature/2026-10-09-topic-actions.md)部分重叠，举报承载从底部改整页而权限、校验与不重发继续成立；[楼中楼](../../implemented/feature/2026-10-10-nested-replies.md)保留真实回复目标；[底部面板](../../implemented/feature/2026-10-10-coolapk-sheets.md)负责手势与安全区。无完全吸收或过时提案。

## Alternatives considered

- 只去掉 OutlinedTextField 的边框改动最少，但回复仍然全屏、举报仍浮动，无法满足已确认的布局。
- 将回复做成独立导航 Dialog 能复用导航生命周期，但独立窗口会增加话题背景、键盘和已有同窗口模态协调；采用话题条目内的面板，ViewModel 仍按账号和楼层隔离。

## Testing

2026-10-11：退出登录按钮由64dp缩至最小48dp，仅缩减内边距。SettingsModalUiTest的退出确认与防重复调用用例真机通过（artifacts/logout-height-device.txt）。实际设置页正常字号按钮144px高（48dp），130%字号自动增至170px，文字完整；artifacts/logout-height-live-default.png与logout-height-live-large.png均已查看，字体已恢复“跟随系统”，底栏搜索偏好及账号保留，未执行真实退出。

2026-10-11：SettingsDensityUiTest两项通过（artifacts/switch-final-device.txt），验证52dp单行、48dp触控区、开关增长/缩小中间帧和禁用态。冻结Compose动画时按压indication会压暗白色滑块，像素检测按明亮中性色识别并允许6%抗锯齿偏差；禁用状态改变后推进一帧再断言，避免测试时钟暂停导致未重组。实际设置截图artifacts/interface-settings-live.png已查看，组内细分隔线和紧凑行高清晰。

EditorLifecycleUiTest 先复现成功后内存草稿仍保留，日志 artifacts/editor-reuse-repro-device.txt，再修复；不同回复目标隔离、失败保留与成功清理三项全部通过。CommunityFormsUiTest 三项覆盖发帖分类和工具、回复聚焦/键盘避让/拖关重开、深色130%字号整页举报及违法确认校验，全部通过。

键盘验证要求测试 Activity 与 MainActivity 一样使用 edge-to-edge 和 adjustResize。空白测试 Activity 原先使用 adjustPan，系统键盘可见但 Compose 不接收预期的 IME Insets，导致超时；修正测试窗口后通过，不保留无证据的产品延时调整。测试机存在实体键盘，临时通过系统设置打开“使用屏幕键盘”完成测试，结束恢复 show_ime_with_hard_keyboard=0。单项通过日志 artifacts/reply-production-window-device.txt；整批36项全部通过日志 artifacts/coolapk-forms-final-device.txt。129项 JVM 测试、Debug/测试 APK 构建通过，Lint 为0错误、30条现有警告和1条提示。

截图 artifacts/coolapk-new-topic.png、artifacts/coolapk-reply-keyboard.png、artifacts/coolapk-report-dark-large.png 由 Compose 根节点捕获，键盘自身属于独立系统窗口，不包含在回复截图中；避让由真实 IME Insets 与面板/按钮边界断言验证。举报和发布使用夹具验证，不向社区提交测试内容。应用正常入口和话题列表进入详情另行检查；本轮通过 adb 直接打开话题链接的补充检查被自动审批拦截，未提供具体理由，因此不将其计入本轮验证。

## Consequences

回复不再通过 compose 导航返回，需要显式连接成功刷新；键盘首次返回应先收起键盘。大字和短窗口由自动化覆盖，横屏、其他输入法与设备仍需实际验收。当前真机未登录，未进行真实举报、上传或发布请求。
