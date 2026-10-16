# Agent Note: 统一应用内 Toast 提示

Status: implemented

## Problem

首页手动刷新和多个页面的操作反馈使用 Material Snackbar，图片保存等功能又直接创建系统 Toast，导致提示位置、字体、背景和内边距不一致。首页刷新提示需要与酷安参考图一样使用黄色胶囊提示。

## Decision

新增统一的系统 Toast 工厂 `KudoToast`。所有短消息通过同一入口创建自定义 Toast 视图：顶部居中显示，使用 `LogoYellow` 背景、`Ink` 黑色文字、16sp 字体、24dp 圆角、22dp 水平内边距和 12dp 垂直内边距，最长宽度限制为 320dp。最新消息取消正在显示的旧 Toast，避免快速连续事件堆积。

KudoApp 的账户、主题、编辑器、链接错误、首页刷新计数、邀请复制、登录、发布和私信提示，以及用户页事件和图片保存提示都改用该入口；业务 SnackbarHost 全部移除。首页刷新文案仍来自刷新接口计算的真实计数，保持“已为你加载 N 条新内容”。

真机 AndroidTest 不再使用会自动管理安装生命周期的 `connectedDebugAndroidTest`，改为显式构建、`adb install -r -t` 安装测试包和 `adb shell am instrument` 运行，保护开发版登录数据。

## Alternatives considered

- 继续使用 Snackbar 但只调整颜色：无法满足系统 Toast 的行为和位置要求，也会保留多套提示实现。
- 只替换首页刷新提示：其他页面仍会显示旧 Snackbar，用户看到的样式仍不统一。
- 仅使用 `Toast.makeText`：系统默认样式无法控制黄色背景、字体和内边距。
- 使用 Gradle `connectedDebugAndroidTest`：任务会自动管理测试安装，可能卸载开发版并清除登录状态；采用手动 adb 安装和 instrumentation。

## Consequences

应用内所有短反馈使用一致的黄色胶囊 Toast，位置与酷安参考图一致；Toast 的显示时长和系统窗口策略仍由 Android 平台管理。Android 12 及以上系统可能在特殊场景附加应用图标或限制自定义 Toast，但当前 Android 13 真机实际显示为黄色胶囊且无额外图标。

Toast 工厂保留系统 Toast 载体，因此不需要在每个 Compose 页面维护消息状态；测试可直接检查视图属性、Gravity、真实截图和安装包状态。快速连续事件只显示最新消息，丢弃旧提示是有意的反馈策略。

## Verification

- JVM：`:app:testDebugUnitTest --tests com.kudo.android.ContentParserTest --tests com.kudo.android.QuoteParserTest` 通过。
- 构建：`:app:assembleDebug :app:assembleDebugAndroidTest -PversionName=0.2.2-dev -PversionCode=2005` 通过。
- 手动真机安装：RMX2202 / Android 13 使用 `adb install -r` 和 `adb install -r -t`，版本 `0.2.2-dev / 2005`，未卸载主应用。
- 手动 instrumentation：`com.kudo.android.KudoToastUiTest` 2 项通过，覆盖样式属性、Gravity、顶部位置和真实黄色 Toast 截图 `artifacts/yellow-toast.png`。
- 截图人工检查确认 Toast 为顶部居中、黄色圆角胶囊、黑色文字；测试后 `com.kudo.android` 与 `com.kudo.android.test` 仍安装。
- `npm run verify-notes` 应在提交前通过。
