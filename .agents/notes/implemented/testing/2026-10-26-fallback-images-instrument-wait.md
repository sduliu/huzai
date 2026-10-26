# Agent Note: FallbackImagesUiTest 真机断言失败定性与兜底节点显式等待

Status: implemented

## Problem

批K 基线（0.2.8-batchK-test/vc2022）真机实测：instrument 架子已能启动执行，但 `FallbackImagesUiTest` 三个用例全部失败，报错一致为 `Assert failed: The component with TestTag = 'hupu-fallback-*' is not displayed!`。测试给 `HupuAvatarImage`/`HupuIconImage`/`HupuThumbImage` 喂 `http://127.0.0.1:1/unreachable.png`（连接即时拒绝），`waitForIdle` 后断言兜底 testTag 节点 displayed。

需要定性：是 Coil error 态根本没到、到了但兜底没显示，还是 testTag/语义树问题。

## Evidence（定性证据链）

1. 设备 logcat（2026-10-26 02:44 运行批次）抓到真实失败：`java.lang.AssertionError: Assert failed: The component with TestTag = 'hupu-fallback-avatar' is not displayed!`（thumb/icon 同）。每例启动到失败约 1.1 秒，期间 Activity 走完 RESUMED→PAUSED→DESTROYED 收尾。
2. 反编译 `androidx.compose.ui:ui-test-android:1.12.0` 的 `checkIsDisplayed`：**语义树选集为空时直接返回 false**，与「存在但未放置/零尺寸/越界」共用同一句 "is not displayed" 报错。因此报错本身不能证明节点存在——缺席与不可见无法从消息区分。
3. 反编译 `io.coil-kt:coil-compose-base:2.7.0`：`SubcomposeAsyncImage` 走 `AsyncImageState` + `ConstraintsSizeResolver` + `BoxWithConstraints`；`AsyncImagePainter.onRemembered` 在 `SupervisorJob + Dispatchers.Main.immediate` 作用域 `launch` 请求，`imageLoader.execute` 内部切到后台线程取图，结果回 Main 后写 `painter.state` 触发重组。
4. `waitForIdle` 的空闲判定只覆盖 compose 重组/布局/draw 待办、Espresso 资源与主队列——**Coil 在后台线程的请求往返不被追踪**。断言抢在 error 态投递前执行时，`hupu-fallback-*` 节点尚未进语义树，命中上条的空选集分支。
5. 结构性排除「存在但不可见」：error 槽一旦组合，`ImageFallbackBox` 无条件发射 `Icon` + testTag，`Modifier.size(48.dp)` → `fillMaxSize` → `fillMaxSize(0.55f)` 链上不存在零尺寸/未放置机制；若 error 槽已渲染，节点必有非零屏幕内边界。
6. 环境排除请求挂死变体：设备全局 `http_proxy` 为 null，`127.0.0.1:1` 为 IP 字面量免 DNS、TCP 层即连即拒；剩余时延主要是 SwiftShader 软渲 + 未优化 dex 冷启动下 Coil/OkHttp 首次类加载的秒级开销——这恰好解释三例确定性（而非偶发）失败：等待窗口稳定短于 error 投递路径。

结论：**测试时序问题，不是 App 渲染问题**。`SubcomposeAsyncImage` error 槽映射链路（FallbackImages.kt）静态与运行证据均正确，JVM 映射单测全绿佐证契约未变。

## Decision

测试侧改为「对兜底 tag 节点做显式语义等待，再断言 displayed」：每个用例 `setContent` 后调用 `compose.waitUntil("...", timeoutMillis = 10_000)` 轮询 `onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()` 非空，随后 `compose.waitForIdle()` 等布局落位，最后 `assertIsDisplayed()`。

tag 节点由 error 槽唯一发射（FallbackImages.kt 中 `ImageFallbackBox` 仅在 `State.Error` 分支组合），因此节点出现即证明 Coil error 态确实到达——这就是 error 态可达性的显式证据，不再需要猜测 `waitForIdle` 是否覆盖了异步失败。10 秒上限只为 SwiftShader 冷路径留余量；若 error 态真的不到，失败形态变为 `ComposeTimeoutException`（条件不满足），与「节点存在但不可见」可明确区分，诊断价值更好。

继续喂 `http://127.0.0.1:1`：连接拒绝即时、离线可复现、免 DNS、不经代理（设备代理为 null），且走真实 `HttpUriFetcher` 失败路径，与生产失败同源。

App 侧零改动：兜底渲染链路不是根因，不在本测试修复中顺带调整。

## Alternatives considered

- 测试内注入 `LocalImageLoader` 自定义失败 Fetcher（fetch() 直接抛错）：最强理由是彻底消除网络与代理变量、失败确定性最高；被否是因为 `127.0.0.1:1` 在确认无代理后同样即时失败，自定义 Fetcher 会偏离生产 HttpUriFetcher 失败路径且多写一层组件注册代码。
- 给 `Hupu*Image` 公开 `onState`/`transform` 回调让测试直接观察 `painter.state`：能拿到最精确的状态证据；被否是因为这会为纯测试需求扩大 App API 面，而 tag 节点出现本身已是 error 态到达的等价证明（error 槽是节点的唯一来源）。
- `Thread.sleep` 或拉长隐式等待：最省事但把时序猜测换成固定时长，慢环境仍可能不够、快环境白白拖慢，且无法区分「没到」与「没显示」。
- 只断言 `assertExists` 放弃 displayed：能绕过不可见判定，但放弃了「兜底真的渲到屏幕上」这一核心验收，语义变弱。

## Consequences

- 收益：等待目标从「不可见的异步完成」换成「可观测的语义树事实」，三例在慢设备上具备确定性通过条件；失败形态从含糊的 "is not displayed" 细化为超时报错（error 未到）或 displayed 断言（已到但未显示），后续定性成本降低。
- 代价：慢环境下单例最长多等约 10 秒（仅失败路径才耗尽超时）；`waitUntil` 依赖 `fetchSemanticsNodes` 轮询而非推送，属库既定用法。
- 残余未定：若真机上 error 态确实可达但 `waitUntil` 仍超时，说明存在环境级阻断（例如 instrument 进程网络栈异常），届时 `ComposeTimeoutException` 会把问题面收敛到「error 未到达」——需要 Muse 真机执行后回报哪种失败形态出现。
