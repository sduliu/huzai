# Agent Note: 酷安式未登录账号页面

Status: implemented

## Problem

“我的”页将未登录引导放在大面积黄色头像卡中，搭配大标题及带副标题的纵向菜单，与用户给定的酷安个人中心参考在层级和密度上不同。用户要求分阶段：先验收未登录版，用户确认并登录真实账号后才改登录后的资料、统计、信任等级及 Connect 数据展示。

消息未登录页的铃铛欢迎图、大标题与不可用账号操作也需要统一为已验收的登录区。会话失效后 ViewModel 保留的旧通知不能出现在访客页面。

## Decision

ProfileScreen 在访客分支使用独立布局，登录后的卡片、资料补齐和背景图保持现有实现。访客页从状态栏安全区下方开始，不显示顶部工具栏和重复设置按钮；白色小圆角登录区使用黄色居中登录按钮。下方按用户修订保留“设置”“关于酷DO”两条横向菜单行，保留说明文字并收紧字号、行高和间距；关于行箭头前显示 BuildConfig.VERSION_NAME，菜单下方保留“真诚 · 友善 · 团结 · 专业”。移除访客收藏入口，不增加夜间模式或图标网格。登录区说明和入口文字允许随字号、宽度换行，内容可滚动并预留底栏空间。

关于入口进入独立简洁页面，复用设置页同一段应用说明并显示真实构建版本。登录和设置保留原路由，不修改显示偏好。

[关于菜单归拢](2026-10-10-about-settings.md)将设置中的关于、许可、指南和隐私集中在独立关于页正文下方，并显示双品牌红心页头；访客入口、真实版本和 slogan 保留。

未登录消息页复用同一 GuestLoginPanel，说明改为回复、提及和点赞通知；顶部只保留18sp文字和最小48dp标题区，不显示刷新、全部已读或铃铛欢迎图。NotificationsScreen 在无账号时选择完整访客分支，不渲染 ViewModel 中可能残留的通知、加载和错误内容。登录后的通知样式和操作沿用现有实现。共享面板只提供说明与登录回调，不读取账号或主动发网络请求。

## Existing note audit

检索账号、未登录、个人中心、等级、Connect 和 AccountScreens。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)部分重叠，保留会话确认、登录和草稿边界；[用户装饰](../../implemented/feature/2026-10-10-user-decoration.md)部分重叠，登录后的资料卡背景图继续成立；[酷安密度](../../implemented/feature/2026-10-07-coolapk-density-and-motion.md)部分重叠，遵循按内容角色设字号和大字增高；[设置模态](../../implemented/feature/2026-10-08-glass-settings-modals.md)部分重叠，继续复用显示偏好及现有设置页。无完全吸收、过时或被推翻的决策。

## Alternatives considered

- 缩小现有黄色欢迎卡，调整圆角即可保留全部现有结构，修改最少；但大色块和带头像的欢迎结构仍不符合用户给定的白色登录区与居中按钮布局。
- 同时重做登录与未登录分支，能一次统一组件；但用户明确要求先验收访客态并登录真实账号，第二阶段的真实数据组织需要届时验证。
- 四列图标入口能接近参考图并让更多快捷操作保持首屏可达，但用户修订要求只保留现有样式的两条菜单行，使用收紧的文字与间距。
- 消息页继续使用通用 EmptyState 可以复用所有空态入口，但铃铛图和欢迎标题仍是用户要求移除的视觉层级；共享已验收的登录区，使两处登录入口保持同一套尺寸和交互。

## Verification

GuestProfileUiTest 四项覆盖登录、设置和关于各自回调、无收藏和夜间入口、账号与访客切换、280dp宽 / 420dp高 / 深色130%字号下的滚动与完整文字、关于页真实版本和返回。两项既有 AvatarUiTest 保证账号卡片仍使用原背景及本人资质展示；设置模态和拖动面板回归同时通过。Android 13 上合计16项通过（29.381秒），见 artifacts/guest-profile-final-device.txt。

首轮测试以 TextLayoutResult.hasVisualOverflow 判定宽度失败，但截图中文字完整，诊断结果是语义接口保留720px段落可用宽、实际文字布局290px；改为检查每行实际 getLineRight、是否省略和高度。测试外层 Surface 的最小约束也让初版280dp窗口铺满屏幕，增加松约束外层 Box 后截图确认为840×1260px，并重新通过全部回归；这两项是夹具修正，不以放宽字号或扩大产品窗口绕过问题。首轮和诊断记录为 artifacts/guest-profile-device.txt、artifacts/guest-profile-overflow-device.txt。

135项 JVM 测试通过，Debug 与测试 APK 构建成功，Lint 0错误、30条原有警告与1条提示；见 artifacts/guest-profile-final-build.txt、artifacts/guest-profile-final-lint.txt。已查看浅色、深色窄屏130%截图，以及真实应用 artifacts/guest-profile-live.png 和 guest-profile-live-about.png。真机关于入口进入独立页面，返回恢复未登录“我的”；未修改用户显示偏好，未使用真实账号声称登录后的数据验收。测试截图中按压涟漪是瞬间状态，静态外观以真实应用截图为准。

移除右上设置、恢复 slogan 并增加关于行版本号后，GuestProfileUiTest 四项再次通过（6.101秒），额外检查唯一的界面设置入口、版本号完整布局及窄屏 slogan 可滚动到达。135项 JVM 测试和 Lint 再次通过；记录为 artifacts/guest-profile-polish-device.txt、artifacts/guest-profile-polish-build.txt。已查看浅色静态真机截图 guest-profile-polish-live.png 以及深色130%窄屏截图 guest-profile-polish-dark-narrow-large.png，版本号和箭头相邻、长说明可换行。

GuestNotificationsUiTest 先在旧版复现：加载夹具通知后将会话响应改为无用户，旧通知仍被渲染，见 artifacts/guest-notifications-repro-device.txt。独立访客分支修复后，与 GuestProfileUiTest 合计6项通过（6.771秒）；保留 ViewModel 缓存以证明 UI 不依赖清空通知，验证账号操作隐藏、无写请求、登录回调及280dp短窗口 / 深色130%字号文字完整可滚动。夹具 HTTP 拦截所有请求，不访问真实账号。

135项 JVM 测试和 Lint 再次通过（0错误、30条原有警告及1条提示），记录为 artifacts/guest-notifications-build.txt、guest-notifications-final-device.txt。已查看浅色与深色窄屏截图，真实消息页截图为 guest-notifications-live.png；实际点击登录进入官网 WebView，原生返回恢复消息页，没有输入账号或提交登录，布局证据为 guest-notifications-login.json / guest-notifications-return.json。

## Consequences

未登录页面减少大色块和重复入口，登录入口更明确；关于页新增独立导航目标，与设置说明共享文案。窄窗口或大字号下说明允许换行，内容增高时需要滚动。真机当前仅 Android 13；登录后的重排、等级入口和 Connect 数据展示等待用户验收访客态并登录后实施。
