# Agent Note: 用户签名与个人背景图

Status: implemented

## Problem

帖子忽略网站独立签名，个人中心也没有显示用户已设置的资料标题和卡片背景。签名可以包含动图，需要独立显示开关。

## Decision

签名全部原生渲染的范围由[SVG 签名修复](../bug-fix/2026-10-10-svg-user-signatures.md)部分取代：SVG 图片使用受限、无脚本的网页图片容器保留 CSS 动画；独立签名字段及位图动图契约继续有效。

图片高度上限由[签名真实尺寸](../bug-fix/2026-10-10-signature-natural-size.md)部分取代：签名满正文宽度、按真实比例自适应高度，不裁切。

Post 读取 user_signature（图片 URL 或 cooked HTML），Member 读取 profile_background_upload_url 和 card_background_upload_url。签名使用本机 show_signatures 偏好，默认 true，设置界面立即更新；关闭时不创建图片请求。默认值同时应用于 DisplaySettings 和未保存偏好的本地读取，已有显式开启或关闭保持原值，不迁移或重写。主帖和完整回复正文下显示签名，楼中楼短预览不放签名。原生解析文字、链接和图片，不执行网页脚本；复用 GIF/WebP/APNG 解码及前台生命周期，尺寸遵循独立满宽规则。

已登录“我的”使用 profile_background_upload_url 作为沉浸式资料标题，覆盖状态栏并加遮罩，空值或失败使用 LogoYellow。card_background_upload_url 是用户卡片的独立字段。会话接口不保证背景字段，个人中心按账号补齐公开资料，较早账号的响应不得覆盖新账号。头像进入个人主页，无独立“查看个人主页”行或顶部标题、设置按钮。

公开主页的背景选择由[完整公开主页](2026-10-10-public-user-profile.md)部分取代：使用独立卡片接口的 card_background_upload_url，未设置或失败回退头像加遮罩。本篇“我的”使用资料标题图、失败为黄色的约定继续有效，两处不能再次混用。

个人摘要使用 /u/{username}/summary.json，默认显示访问天数、已读帖子、获赞、发帖数。未知值显示破折号，辅助读取失败可以重试，不能把失败显示成零。主题统一接收状态栏前景覆盖，离开资料页恢复系统主题前景。原生目的页与消息分组见[个人中心功能](2026-10-10-member-account-dashboard.md)。

## Related decisions

检索签名、背景图、个人中心、动图、界面设置。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)、[头像](../../implemented/feature/2026-10-08-avatar-presentation.md)、[设置弹窗](../../implemented/feature/2026-10-08-glass-settings-modals.md)和[原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)部分重叠，分别保留会话、头像、偏好与解码边界，没有被完全吸收或过时的提案。

## Alternatives considered

- 默认关闭可减少图片加载和滚动占用，但用户最新要求默认开启；仅修改缺失偏好的回退值，避免覆盖用户已经保存的关闭选择。
- 用 bio_cooked 当签名：不增加字段，但个人简介与帖子签名是网站不同内容，不能混用。
- WebView 渲染签名：最接近网页 HTML，但为每个回复引入网页实例、脚本与生命周期；使用已有原生文章解析和受限图片渲染。
- 两张背景混用：总能展示一张图，但网站独立配置两种用途，分别绑定个人卡片和资料标题。

## Testing

2026-10-13：默认值改为开启，已有显式false保持关闭。ThreadAndDecorationTest的7项JVM用例通过，SignatureSettingsUiTest验证缺失偏好默认开启、关闭后重建仍关闭及再次开启；与置顶偏好合跑10项真机用例全部通过（artifacts/compact-signature-device.txt）。两项位图和SVG动画用例在floor-signature-device.txt中通过，夹具显式指定showSignatures=false以继续验证关闭零请求、开启动画及关闭释放，避免把旧默认值当作关闭行为前提。该轮合跑的独立滑块断言后续修复与验证见[楼层导航](2026-10-10-topic-floor-navigation.md)。以下默认关闭的检查为旧默认值的历史验证记录。

129项JVM测试通过，覆盖图片 URL、HTML、缺失和错误类型。50项真机回归全部通过（artifacts/community-features-final-device.txt），其中 SignatureSettingsUiTest 验证默认关闭、持久化及立即切换；AvatarUiTest 在签名实际图片像素上采集 GIF/APNG/WebP 的三色帧，并验证后台暂停、恢复和关闭停止，同时验证卡片与资料背景分别使用及失败回退。

首轮动画断言将绿色 #43a047 的亮度阈值误设为0.7，修正为颜色主通道差值后通过，未更改产品解码器。新增签名 HTML 截图放在暂停前，使用 Compose 内容截图避开 Android CLI 持有 UiAutomationService 的冲突；调整后独立重跑通过，日志 artifacts/signature-compose-capture-device.txt。已查看 user-signature-animated.png 和 profile-card-background.png。账号响应隔离由按账号重建的取消作用域与响应身份校验守住，未使用真实账号切换声称网络端验收。

个人中心收尾回归通过151项JVM测试和21项真机用例（artifacts/member-account-final-checks.txt、artifacts/member-account-final-device.txt），其中 AvatarUiTest 验证标题图加载后切换白色图标，MemberHeaderStatusUiTest 验证标题底部离开状态栏时恢复主题。真实账号截图 artifacts/member-account-live-profile.png 确认资料标题图延伸至状态栏且图标清晰。

## Consequences

详情全部装饰迁移到昵称后的旧约定由[头像挂件与昵称图标分工](../bug-fix/2026-10-11-topic-avatar-name-decorations.md)取代：头像与列表一致保留 PUBLIC 挂件，昵称后只显示其余有效图标，不重复头像已经显示的部分。两处作者栏的昵称与元信息仍保持单行并在超长时省略；本篇签名和背景图约定继续有效。

正文解析的[引用分组](../bug-fix/2026-10-10-discourse-quotes.md)也可用于签名；签名递归渲染其内容，遵循独立满宽尺寸、显示偏好和SVG生命周期规则。

沉浸式标题滚动时，以标题底部是否覆盖状态栏高度决定图标前景，不能只检查第一个可见列表项。组合阶段读取派生的覆盖状态和图片载入状态，随后由 SideEffect 写出，避免只在副作用读取状态导致滚动不触发重组。真机用例先复现标题剩余半个状态栏高度仍保留覆盖颜色（artifacts/member-statusbar-repro.txt），随后使用实际标题偏移、尺寸与状态栏 inset 修复。

原生签名支持内容子集，不复刻网页主题 CSS。远程签名图片失败时隐藏，资料背景图失败时回退；资料补齐失败不能影响登录和现有个人信息。

签名图片加载失败后隐藏该图片，不保留“签名图片”及空白占位；当文字与可用图片均不存在时同时移除签名分隔线。失败仅记在当前签名组合，修改签名或重新进入可重试，普通头像的失败回退不变。gonethen 的实际帖子与用户 custom_fields.signature_url 均返回同一 GIF，但来源地址 TLS 失败，不能据网页不显示推断用户未设置签名。包含可用文字的混合签名继续保留文字；SVG 的错误、HTTP失败和SSL失败也遵循隐藏规则并释放实例，不放宽证书校验。

失败隐藏先由 SignatureFailureUiTest 复现残留签名区域（artifacts/signature-failure-repro-device.txt），修复后2项真机用例通过，覆盖整段空签名消失和混合签名保留文字（artifacts/content-about-device.txt）。本轮另重跑 GIF／APNG／WebP 签名动画1项通过，验证默认关闭、逐帧变化和前后台暂停（artifacts/content-about-fixed-device.txt）。
