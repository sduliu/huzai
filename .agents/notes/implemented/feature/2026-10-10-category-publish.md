# Agent Note: 分类内发帖入口

Status: implemented

## Problem

分类详情缺少快捷发帖入口，进入通用编辑器还需重复选择当前分类。

## Decision

分类详情右下角放置黄色液态圆形加号，采样滚动列表并沿用全局材质设置及系统兼容回退；按钮避让导航栏。点击走现有认证入口，已登录打开带 category 参数的新帖编辑器并预选当前分类。只打开编辑器，仍由用户明确发送。

分类新帖使用账号和分类隔离的草稿键，避免切换分类覆盖通用新帖草稿。首次分类草稿默认当前分类，恢复已有草稿保留用户手动选择；回复仍按话题及楼层隔离。草稿列表继续可恢复分类草稿。

新帖草稿键为 `{userId}_new_{categoryContext ?: 0}`，0代表通用入口，正数代表分类入口；旧通用键仍可读取。SavedDraft.categoryContext 表示进入的分类上下文，Draft.categoryId 表示用户当前选中的发布分类，两者可以不同；草稿恢复导航传前者以找到原草稿。新帖键的最后一段不再被解析为回复楼层，实际话题回复保持 `{userId}_{topicId}_{floor}`。

## Related decisions

检索发布、草稿、categoryId 和 LiquidButton。[账号草稿](../../implemented/feature/2026-10-07-account-and-composer.md)部分重叠，扩展新帖分类上下文隔离，保留明确发送和确认后清理；[酷安表单](../../implemented/feature/2026-10-10-coolapk-forms.md)继续提供编辑页；[液态按钮](../../implemented/architecture/2026-10-07-backdrop-demo-navigation.md)复用形变和镜片，增加 tint 的低版本黄色回退；[分类顶部](../../implemented/feature/2026-10-10-category-detail-header.md)只负责布局顶部。无完全吸收或过时提案。

## Alternatives considered

- 共用全站新帖草稿且进入时强改 categoryId：实现少且可续写同一篇，但切换分类会静默改变旧草稿发布目标，使用分类独立草稿。
- 普通黄色 FloatingActionButton：颜色和点击可靠，但没有用户要求的液态材质，复用现有 LiquidButton 和真实列表采样。

## Testing

AccountDataTest 验证分类新帖的上下文、手动选中的发布分类和空回复目标；相关账号数据与分类信息流共27项JVM通过。EditorLifecycleUiTest 验证预选、通用/多分类草稿隔离、恢复手动选择、只在明确发送时提交当前分类、确认后仅清除所属草稿；发送使用本地夹具，没有发布真实社区内容。

RMX2202 / Android13 的 CategoryFeedUiTest、EditorLifecycleUiTest、SurfaceLiquidUiTest 和 CommunityFormsUiTest 共22项通过（37.959秒，artifacts/category-publish-device.txt）。覆盖三材质黄色背景、280dp宽130%字号、按压图标放大与取消复原、访客登录回调、成员当前分类回调，以及分类顶部/分页、原有液态按钮和回复表单回归。已查看浅色吸顶、深色窄屏展开与黄色加号截图。Debug及测试APK构建、Lint通过（0错误、30条既有警告、1提示），日志 category-publish-build.txt。

## Consequences

分类入口得到预选分类及独立草稿，增加的持久状态仍受原账号草稿清理策略管理。低版本或关闭完整材质时使用兼容表面；分类权限最终由网站发帖响应确认。真实登录发帖未实发，协议发送与草稿清理由夹具验证。
