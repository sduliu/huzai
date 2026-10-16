# Agent Note: 酷DO 原生社区客户端与玻璃渲染边界

Status: implemented

## Problem

空仓库需要形成可安装的 Linux.do 原生客户端。参考酷安的视觉与交互，同时社区数据和账号能力遵循 Linux.do 的 Discourse 行为；玻璃效果不能让旧设备无法使用。

## Decision

采用 Kotlin、Jetpack Compose、单 Activity 和按功能分包的单应用模块。ViewModel 持有界面状态，Repository 负责 Discourse 契约，OkHttp 负责请求，Coil 负责图片。应用名酷DO，包名 com.kudo.android，最低 API 26。品牌黄为参考项目 Logo 中的 #FFB003，文字采用深色或可读的深琥珀色。

Coil 3.3.0 的全局 ImageLoader 注册 GIF/动态 WebP 解码；APNG 及 API 26/27 的 WebP 由 APNG4Android 3.0.5 补齐，两模块使用 Java 帧解码，不引入本地二进制。按内容识别 PNG acTL / WebP VP8X，绝不只判断后缀。APNG/WebP 补充解码限制 16 MiB、4096 边长、4M 像素和 1000 帧，按目标尺寸在分配画布前采样。返回不可共享 Drawable，避免多个头像抢同一动画回调；磁盘图片缓存与既有 Cookie 同源边界保持一致。APNG4Android 许可随 APK 收录，头像语义由[头像方案](../feature/2026-10-08-avatar-presentation.md)管理。

网络抓取器、SvgDecoder.Factory 和 communityAnimations 必须在同一个 components 配置块中注册。[正文静态 SVG](../bug-fix/2026-10-13-article-svg-decoder.md)复用 Coil 3.3.0 的 coil-svg 模块，SVG 动画签名仍保留独立浏览器图片容器。动画扩展定义在 ComponentRegistry.Builder 上；重复调用 ImageLoader.Builder.components 会替换先前配置，导致测试抓取器及生产的受限 Cookie 客户端被默认抓取器取代。AvatarUiTest 的真实图片夹具先复现加载失败，再验证同一注册块保留两类组件。

KudoApplication 通过 SingletonImageLoader.Factory 提供全局加载器，AppContainer 在 Application.onCreate 的主线程初始化，避免 WebSettings 首次初始化发生在图片工作线程。Activity 复用容器，不再调用 setSafe：图片先于首页访问、Activity 重建和测试先后顺序都必须得到同一受限网络与动画配置。AvatarUiTest 后接真实加载器测试先复现 setSafe 晚于 get 的崩溃，页面重建用例检查同一加载器继续解码 APNG。

玻璃用 Apache-2.0 的 Kyant Backdrop 1.0.6，封装背景采样、模糊、折射、高光和胶囊选中动画。此版本使用 Android Compose，避免为客户端引入跨平台工程。API 33+ 支持完整玻璃，API 31–32 模糊，较旧系统半透明；用户也能手动选择材质。

WebView 用于登录、网页验证及[应用内网页阅读](../feature/2026-10-10-in-app-web-links.md)，原生请求与其共享受限于 linux.do 的 Cookie。以 /session/current.json 验证会话；写请求附带 /session/csrf 获取的令牌，不使用站点管理员 API Key。只允许可信 HTTPS 来源进入带会话的原生请求层。网页阅读允许 HTTP，由平台维护 Cookie 的域、路径与 Secure 属性，不手工注入社区会话。写请求不自动重试，避免重复发帖；401、403 验证、429 与一般网络失败有不同反馈。

正文按服务端 cooked HTML 解析为原生文字、引用、代码块、图片和链接；复杂嵌入提供浏览器入口。长帖按 post_stream 的 id 补齐，不能用首批返回数量冒充完整话题。

Android 12+ 的 data_extraction_rules 明确排除账号数据的云备份和设备迁移。Activity 使用 singleTop 接收新话题链接；第三方客户端无法在 linux.do 发布域名关联文件，因此只声明普通链接入口，不声明 autoVerify。

## Research

已检索仓库：无 .agents/notes 历史笔记。用户于 2026-10-07 批准完整基础范围和 Android 8.0 支持。

- FluxDO e1bd839：参考 lib/services/discourse 下的 API 契约以及会话处理；其代码 GPL-3.0，本项目独立实现，不复制 Flutter 源码。
- 酷安 16.6.4 的布局含 ComposeView，DEX 字符串含 com.kyant.backdrop 与 LiquidGlassSurface、LiquidGlassTabPanel；仅用于研究，不打包其资源或反编译实现。
- Backdrop 公开源码包含 lens / blur / layerBackdrop；正式依赖锁定 1.0.6，不跟随主分支升级。

## Alternatives considered

- Flutter 复用 FluxDO：可直接利用成熟的论坛功能，但用户明确要求安卓原生，并要求贴近 Compose 玻璃交互，因此独立实现 Kotlin 客户端。
- 自写整套 AGSL 渲染器：能控制全部折射参数，但背景采样、坐标和性能适配会扩大风险；复用开放许可的 Backdrop，再独立实现酷DO组件。
- 全站 WebView：能完整支持所有 Discourse 插件，但无法实现要求的原生信息流与玻璃控件；仅保留验证和外部内容入口。
- 在 Activity 中用 setSafe 注册图片加载器：配置紧邻界面、入口直观，但第一次图片读取后再次创建页面会抛异常；由 Application 提供 Factory，页面只消费已经统一的配置。

## Verification

TASKS.md 记录基础闭环；Gradle 生成 Debug APK；单元测试覆盖模型、分页、请求来源与写操作错误；按用户要求使用连接真机检查玻璃和核心页面。真实账号写操作留给用户主动操作验收。

## Consequences

动图解码的 3 项头部边界测试及初轮 3 项真机测试通过（artifacts/avatar-animation-build.txt、avatar-animation-instrumentation.txt）。真实 ImageLoader 的 APNG 静态首帧复现先失败；注册解码器后 APNG、GIF、动态 WebP 都在 32px 画布上实际渲染红绿蓝三帧。真机 API 33 另强制执行旧版 WebP 分支；这不代替 API 26/27 真机验收。停止测试等待后台线程移除渲染监听，首轮同步断言失败属于测试时序，产品解码未为该误报改动。后续9项真机测试通过，包含组件可见性、后台暂停和加载器重建，结果见头像笔记。

原生页面使用统一的主题、网络契约和错误入口。Cloudflare 验证可能对 HTTP 客户端指纹敏感；即使网页验证成功也不能承诺所有网络环境均可原生访问。复杂帖子插件可能需要外部网页。实时折射的帧率必须在真机上确认；模拟器只能确认渲染和交互正确。
