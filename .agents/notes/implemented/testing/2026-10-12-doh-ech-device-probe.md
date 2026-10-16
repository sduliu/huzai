# Agent Note: 手机非代理网络的 DoH 与 ECH 只读对照验证

Status: implemented

## Problem

[DoH 功能设计](../feature/2026-10-12-doh-network.md)确认文章节点返回 ECH 配置，但桌面 DNS 查询不能证明手机能直连，也不能证明代理登录后的原生会话继续有效。用户批准开发验证，并说明手机 Wi-Fi 已切换为非代理网络。本篇提供生产接入所依据的独立探针对照。

## Decision

实验使用独立实现的 Rust JNI 网络探针，使用 rustls 的 ECH 与正常受信任根证书，依靠 Android 网络实际直连文章节点和主站。它只打包进 androidTest APK，不接管 App 的日常请求，不改变登录、代理、VPN 或用户设置。

实现分为原生解析／TLS 和离线测试、可复现 Android arm64 构建、Android opt-in 对照测试及真机记录四个小单元。每批最多修改 3 个文件，代码与对应笔记同次提交。

对照组为既有 OkHttp + 系统 DNS、既有 OkHttp + 文章 DoH 解析出的地址，以及 Rust + 同节点 DoH + 真正 ECH。固定相同 UA，按 URL 读取现有 Cookie，仅请求会话、话题列表和同站图片。所有组明确不使用 HTTP 代理；若 Android 当前可见 VPN 生效则暂停实验。用户说明的路由器非代理状态是外部条件，App 无法独立证明上游没有透明代理。

探针区分 DNS 配置存在、TLS ECH accepted、HTTP 状态、CF 挑战及 JSON／图片业务成功。DNS 使用标准 wire format，检查响应匹配；TLS 保留原域名证书验证。只允许 HTTPS 主站的白名单 GET；DoH 的 POST 只含 DNS 查询，不携带社区凭据。不跟随重定向、不重试 HTTP 请求、不处理写操作。限制超时和响应大小，不将 Cookie、Set-Cookie、响应正文或账号标识写入报告。

测试只读取既有 Cookie，不清除、伪造或迁移真实登录数据，不把实验响应的 Set-Cookie 写回日常存储。当前会话为空或失效时如实记录，不能宣称完成“代理登录后保持登录”的验证。设备覆盖安装必须核对签名并使用 adb install -r。

## Related decisions

检索 DNS、ECH、JNI、只读探针及网络笔记。与[DoH 功能设计](../feature/2026-10-12-doh-network.md)部分重叠并互链：本篇保留独立技术验证及其结果，正式配置和生产网络由功能篇记录。原有[数据契约](../architecture/2026-10-07-discourse-data-contract.md)和[CF 恢复流程](../bug-fix/2026-10-11-cloudflare-verification-flow.md)是实验必须保留的边界。其他 UI、分页和通知笔记无关；没有完全吸收或过时提案。

## Native verification evidence

首批 5 项 Rust 离线边界测试通过。补充协议回归先复现 HTTP/1.1 直连误用完整 URL 而非 origin-form，7 项中 1 项失败，记录在 artifacts/doh-validation/native-repro.txt；修复将 HTTP/1.1 请求行与 HTTP/2 authority 分开处理后，7 项全部通过，记录在 native-fixed.txt。新增响应体上限测试检查超限拒绝和恰好达到上限的成功边界。DNS 各记录查询状态独立报告，全部失败保留实际底层阶段，不混成普通无地址。

Windows 构建入口为 scripts/build_doh_probe.ps1，使用固定 Cargo.lock、NDK 27 与 CMake 3.22.1，生成 android-26／arm64 的 JNI 库到 app/build/generated/dohProbeJni。脚本只设置当前进程环境，结束后恢复；强制 16KB ELF LOAD 对齐并检查结果。生成文件都在既有忽略的 app/build/ 下，不把二进制提交源码。

首次真实构建复现 Windows PowerShell 5 将 Cargo stderr 进度文本当作终止错误，记录 android-native-build.txt；脚本改为归一化文本并以进程退出码判断后，Android arm64 release 构建成功（73 秒），库为 4,798,696 字节，ELF LOAD 对齐均为 16KB，记录 android-native-build-fixed.txt。

Android 对照位于 DohEchDeviceProbeTest，编译时显式传入 -PdohProbe=true 才把生成库放进 androidTest APK。运行 JNI 边界检查需 dohNativeProbe=true，线上只读对照需 dohLiveProbe=true；不传参数不会联网。额外增加 Rust + DoH、不开 ECH 的对照，区分 TLS 栈变化与 ECH 的作用。

AndroidTest 构建及 Lint 通过（27 秒，0 错误、38 警告，android-test-build-final.txt）。测试 APK 为 7,661,209 字节，签名 SHA256 与手机现有开发版相同（95f95aff266413e4c3fb431bf7ae96552d0e5e601b986e4a53d2a116b6623e4f），16KB zipalign 通过。仅使用 adb install -r 覆盖测试包，没有替换 App 主包、卸载或清数据。真机 JNI 凭据与白名单检查 1 项通过（device-native-boundaries.txt）。

首轮手机对照 27 秒，系统 DNS 的会话／列表请求超时，仅 DoH 的两项请求连接错误，Rust 不开 ECH 的两项握手失败；ECH 两项在本地配置解析时失败，没有业务响应，不能判断 CF。DNS 三种记录成功，配置解码后 69 字节；Cookie 存储未变（device-result-first.json）。根因是 Hickory 的 EchConfig.0 不含 ECHConfigList 的 2 字节长度，而 rustls 需要完整 TLS 向量。用公开 ECH 配置和完整 DNS 编解码路径新增回归，8 项中此项先失败（ech-length-repro.txt）；修复使用 BinEncodable 恢复完整 wire value 后 8 项通过（ech-length-fixed.txt），不手工猜配置版本或关闭证书校验。

## Alternatives considered

- 直接上线设置和全局网络层：用户能立即体验开关、节点管理和所有页面，但会在 ECH／CF 尚未验证时扩大回归范围；先交付隔离的对照探针。
- 只在电脑跑 Rust：构建和调试更方便，能验证 DNS、TLS API，但电脑线路和 Cookie 不代表手机；桌面仅用于离线测试，结论以手机为准。
- 通过浏览器 MITM 接管全部请求：可让 WebView 与原生统一出口，但带来证书、私钥和网页兼容成本，超出当前最小原生验证范围。

## Testing

最终 8 项 Rust 离线边界测试、1 项真机 JNI 边界检查和 1 项线上只读对照通过。线上对照包含四种传输各三种业务请求，共 12 条记录，耗时 78.223 秒；失败对照如实保留，验收要求 ECH 组的真实业务成功，不把其他组连接失败伪装成 CF 响应。

当前 RMX2202／Android 13 的默认网络为 Wi-Fi，Android 未发现 VPN 或系统 HTTP 代理，四种客户端显式禁用 HTTP 代理。用户确认路由器侧为非代理线路。结果如下：

| 传输 | 会话 | 列表 | 同站头像 |
| --- | --- | --- | --- |
| OkHttp + 系统 DNS | 连接超时 | 连接超时 | 连接超时 |
| OkHttp + 文章 DoH | SocketException | SocketException | SocketException |
| Rust + DoH，不启用 ECH | TLS 握手失败 | TLS 握手失败 | TLS 握手失败 |
| Rust + DoH + ECH | HTTP 200，已认证 | HTTP 200，30 条话题 | HTTP 200，WebP 成功解码 |

ECH 三项均为 TLS Accepted、HTTP/2，未检测到 CF 挑战。会话走 IPv4（1,759ms），列表走 IPv6（1,638ms），头像走 IPv6（1,778ms）；这些是包含 DNS 和握手的单次独立请求观测，不是吞吐量或长期性能基准。DNS 返回 A／AAAA／HTTPS，完整 ECH 参数 71 字节。已有登录 Cookie 存在，ECH 请求得到有效用户，Cookie 存储前后相同。

最终报告为 artifacts/doh-validation/device-result-ech-fixed.json，JUnit 输出为 device-live-ech-fixed.txt；报告不含用户 ID、昵称、Cookie、Set-Cookie、响应正文或目标头像 URL。完整受控测试 APK 留在 app-doh-probe-androidTest.apk，App 主包未替换。

不传参数运行测试类时，两项测试都在入口跳过（device-default-disabled.txt），不会访问网络。另一次不传 -PdohProbe 的常规 AndroidTest 构建通过，确认 APK 不含 libkudo_doh_probe.so（android-default-test-build.txt）。8 项单测、两项 opt-in 真机检查以及默认跳过行为分别记录，不能把跳过项计入通过项。

复现入口（Windows，先安装 Rust aarch64-linux-android target、NDK 27、CMake 3.22.1）：

```powershell
./scripts/build_doh_probe.ps1
./gradlew.bat '-PdohProbe=true' :app:assembleDebugAndroidTest
# 核对现有 App 和测试 APK 的签名，再使用 adb install -r 覆盖测试包。
adb shell am instrument -w -r -e class com.kudo.android.DohEchDeviceProbeTest -e dohNativeProbe true -e dohLiveProbe true com.kudo.android.test/androidx.test.runner.AndroidJUnitRunner
```

## Consequences

最小探针证明在用户当前非代理 Wi-Fi 上，文章节点配合真正 ECH 可以读取现有登录会话、话题列表与头像；仅 DoH 在同次对照中不足。生产 App 的开关、自定义节点 CRUD、VPN 自动暂停／恢复及全局传输尚未接入，功能提案仍保持 proposed。探针仅支持受限 GET、每次独立创建运行时并缓冲响应，不具备生产上传、取消、连接池或路由切换语义，不能直接作为完整 HTTP 替换层上线。

当前 Cookie 并非本次新做代理登录获得，无法复原其未知历史出口；成功证明当前会话在当前非代理线路可复用，不能宣称重新完成了全部代理登录步骤。单条 Wi-Fi 线路结果不覆盖蜂窝、其他运营商或长期 CF 策略。Rust HTTP／TLS 特征与 Chromium／OkHttp 不同，后续仍可能再次遇盾。没有改造 WebView，直连过盾闭环仍是完整功能的验收项。

首次构建增加 Rust、NDK 和加密库依赖；最终原生库为 4,799,320 字节，探针不进入 App APK。技术收益是明确 DNS 与 ECH 的能力边界，并用真实 Cookie 证明当前直连可行；下一阶段需要生产传输的完整契约与设置验收，不能只把探针的 JNI 调用接到所有请求上。
