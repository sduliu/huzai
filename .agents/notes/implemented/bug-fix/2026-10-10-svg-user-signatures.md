# Agent Note: 签名 SVG 的显示与动画

Status: implemented

## Problem

skyrock 的 user_signature 是公开 HTTPS SVG。接口和图片请求均成功，图片加载器仅支持位图与 GIF／WebP／APNG，导致签名只显示占位文字。该 SVG 使用 CSS keyframes、裁切和渐变，静态 SVG 解码不足以保留签名动画。

## Decision

仅 HTTPS 路径以 .svg 结尾的签名使用无脚本的 WebView 图片容器，浏览器以 img 显示外部 SVG，保留 CSS 动画；其他图片继续走已有原生加载器。[正文静态 SVG](2026-10-13-article-svg-decoder.md)增加统一原生解码能力，但不改变此处保留动画的决定。查询参数和扩展名大小写不影响识别。签名默认开启，已保存偏好不变，关闭时不创建图片容器或请求；默认值见[用户装饰](../feature/2026-10-10-user-decoration.md)。

原尺寸上限由[签名真实尺寸](2026-10-10-signature-natural-size.md)接管：图片壳宽度100%、高度auto，通过原生内容高度回调保持满宽真实比例，不启用脚本或重复请求。

图片错误、HTTP失败及SSL错误通知签名宿主隐藏对应图片并释放实例，SSL处理必须取消请求；整个签名无可显示内容时分隔线也移除。规则与[用户装饰](../feature/2026-10-10-user-decoration.md)一致，不把图片壳加载完成当成网络失败后的成功。

图片壳使用 linux.do 基准地址，使同站点图片保持 Cookie；外部图片禁用第三方 Cookie，不发送社区 Cookie。图片 URL 经过属性转义，CSP 禁止脚本，资源拦截只允许该图片的 GET 请求；禁止网页导航、文件访问、存储、新窗口及缩放，不引入网页工具栏。原生容器提供图片描述，WebView 不接收触摸，由原生正文负责滚动。

仅可见且前台的签名创建图片容器，不可见和组合移除时释放 WebView；后台事件立即隐藏并调用 onPause，恢复时显示／重建。只管理当前实例，不调用全局 pauseTimers，不影响登录或普通网页。离开前台再返回可能重新播放签名的入场动画。图片尺寸遵循独立满宽规则。

## Existing note audit

- [用户装饰](../../implemented/feature/2026-10-10-user-decoration.md)：部分取代签名全部原生渲染的约定，签名独立字段、默认值和位图动画保留。
- [网页浏览](../../implemented/feature/2026-10-10-in-app-web-links.md)和[WebView 视口](../../implemented/bug-fix/2026-10-10-webview-viewport.md)：部分重叠，复用受限访问、明确尺寸和生命周期原则，签名不是可导航网页。
- 头像、相册与列表入口记录无关；没有同主题 proposed 或 rejected 记录，无需归档。

## Alternatives considered

- 添加原生 SVG 解码器：成本较低且方便复用 Coil 缓存；不能渲染此签名的 CSS 动画和全部浏览器效果。
- 所有签名都用 WebView：HTML 与动画一致性最好；每条普通位图签名也会引入网页实例，仅对 SVG 使用受限图片容器。
- 将 SVG 转成位图：原生显示简单；会丢失动画且改变用户签名效果。

## Testing

RMX2202 / Android13 先用 skyrock 的真实 SVG URL 复现20秒仍只显示占位文字（artifacts/feed-signature-repro-device.txt、svg-signature-before.png）。修复后实际 SVG 的终端画面和打字效果显示，svg-signature-after.png 已人工查看；该用例直接取签名图片，不打开真实帖子或提交阅读进度。

162项JVM单测通过，包括 SVG URL 分类、查询参数、图片壳转义和 CSP。SVG 的3项真机用例、签名偏好1项及 GIF／APNG／WebP 签名动画1项全部通过（11.803秒，artifacts/svg-signature-final-device.txt），覆盖真实图片、离线红蓝动画帧、默认关闭无请求、禁止导航及其他资源、后台暂停、禁用销毁、滑动正文及离屏销毁。滚动首轮错误地在 SideEffect 中复制 ScrollState.value，未随布局更新；改为断言真实 ScrollState，未放宽产品滚动条件。构建记录为 artifacts/svg-signature-build.txt 和 svg-signature-final-build.txt。

失败隐藏改动后3项离线SVG真机回归通过（artifacts/content-about-device.txt），覆盖错误隐藏与实例释放、CSS动画帧和关闭释放、原生滚动及离屏释放；本轮未重复访问真实 skyrock 图片。关联的签名偏好用例也通过。

## Consequences

SVG 签名可以保留浏览器动画；容器比原生图片占用更多资源，因此仅用于可见签名。具体渲染受设备 WebView 版本影响，网络失败后隐藏并释放图片。仅识别 HTTPS 图片路径的 .svg 扩展，扩展名缺失的 SVG 不在本次已验证范围。外部 SVG 的浏览器图片模式限制外部资源和脚本；本次实际签名及 CSS 动画夹具经过验证，不承诺任意网页嵌入内容。
