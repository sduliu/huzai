# Agent Note: 底栏中间入口支持发帖或搜索

Status: implemented

## Problem

底栏中间入口固定发帖。用户确认在界面设置中支持发帖与搜索两个选项，默认沿用发帖。

## Decision

DisplaySettings增加NavigationAction枚举，存储枚举名，缺失或未知值回退COMPOSE。设置页使用现有单选ActionSheet，选择立即保存、取消不变。根底栏按设置显示加号或放大镜，始终保留黄色液态按钮与actionIndex=2。搜索直接进入原生search路由，无需登录；发帖沿用认证与草稿流程。切换不改变当前标签，重启和退出账号均保留界面偏好。

## Related decisions

检索底栏、界面设置、AppSettings与搜索。[设置面板](../../implemented/feature/2026-10-08-glass-settings-modals.md)部分重叠，复用单选与取消契约。[底栏材质](../../implemented/architecture/2026-10-07-backdrop-demo-navigation.md)部分重叠，保留采样层、拖动跳过动作格和前景折射。无其他相同提案或需归档记录。

## Alternatives considered

- 增加第六个搜索标签能同时保留发帖，但改变五入口布局且不符合用户要求的二选一。
- 仅切换图标而保留点击发布改动最小，但搜索图标必须对应实际搜索行为；图标与点击共同由同一个枚举驱动。

## Testing

NavigationActionUiTest两项真机通过，覆盖缺省/未知枚举回退、单选保存与取消、偏好重建及两个模式唯一可访问动作。Debug和测试APK构建通过，日志artifacts/member-navigation-fixed-ui.txt。实际账号设置切换搜索后，黄色胶囊显示黑色放大镜并进入原生“搜索社区”；验收后恢复发帖，截图artifacts/runtime-search-action.png和页面结构runtime-native-search-layout.json。

## Consequences

新增设置延长小屏列表，其余选项继续通过滚动访问。导航材质只换前景图标，可访问入口唯一、采样层图标一致已通过真机回归；宽屏和其他Android版本尚无本次视觉样本。
