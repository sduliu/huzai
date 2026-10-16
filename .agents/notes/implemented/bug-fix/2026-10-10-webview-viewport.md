# Agent Note: WebView 视口单位与登录验证布局

Status: implemented

## Problem

RMX2202 / Android 13 / WebView 155 的登录弹层空白。hCaptcha 脚本、iframe 和配置均返回 200，第三方 localStorage 可访问；iframe 内联高度为 76px，实际高度为 0，网站规则 `max-height:min(1000px,200vh)` 的计算结果为 0px。页面 innerHeight 为 670，vh/svh/lvh/dvh 却全部为 0px。临时仅解除 hCaptcha iframe 的 max-height 后出现复选框，恢复后再次消失，证据为 artifacts/login-captcha-before.png、login-captcha-diagnostic.png 和 login-device-network.txt。未完成验证码或登录。

## Decision

LoginScreen 和 WebScreen 明确原生 WebView 的 MATCH_PARENT 布局参数，使 Chromium 使用有界视口计算 CSS 单位，不注入站点样式、不改变 Cookie 策略。Compose AndroidView 的默认 WRAP_CONTENT 导致当前 WebView 的视口单位为零，即使 innerHeight 和原生可见区域正常也不能证明 CSS 视口有效。LoginScreen 的可选 WebView 工厂供离线测试使用。

## Existing note audit

[官网会话](../../implemented/feature/2026-10-07-account-and-composer.md)部分重叠，继续保留官网输入与 current_user 校验。[应用内网页](../../implemented/feature/2026-10-10-in-app-web-links.md)部分重叠，继续保留网页连续浏览、域隔离与历史。本篇负责原生视口测量，无完全吸收或过时提案。

## Alternatives considered

- 放开第三方 Cookie 可能解决某些验证服务的存储依赖，但本次脚本和存储已工作，直接原因是 CSS 高度上限为零，不扩大存储策略。
- 覆盖 hCaptcha iframe 的 max-height 可立刻恢复显示，且已作可逆诊断；它耦合站点 DOM，无法修复其他 vh 布局，优先修正原生视口。

## Verification

LoginWebViewUiTest 先失败，100vh 为 0，日志 artifacts/login-viewport-repro-device.txt；阅读器同样复现 682px 的 innerHeight 与 0px 的 100vh，日志 webview-viewport-intermediate-device.txt。修复后6项登录/网页测试通过（7.86秒，webview-viewport-final-device.txt），验证宿主可用高度减少时 CSS 视口同步改变且表单不重载，并回归网页历史、Cookie 共享与错误重试。缩小高度夹具用外层全屏 Box 放松子布局约束，不能让根布局的固定最小高度掩盖缩小操作。

官网实页已查看 artifacts/login-captcha-fixed.png：不注入诊断 CSS、不完成验证的情况下 hCaptcha 复选框可见。131项 JVM、Debug/测试包和 Lint 通过；原签名覆盖安装，应用数据保留。

## Consequences

修复所有依赖 vh 的网页布局，不耦合验证码 DOM。当前真机覆盖 Android 13 / WebView 155；复选框可见不能替代真实账号登录端到端验收。
