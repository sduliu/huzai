# Agent Note: 原生工程与提交校验入口

Status: implemented

## Problem

初始仓库只有参考技能，没有 Android 构建入口，也不能在根目录运行约定的笔记校验命令。

## Decision

工程采用 Gradle Wrapper 8.13、AGP 8.13.2 和 Kotlin 2.3.10，compileSdk / targetSdk 36、minSdk 26。Kotlin 使用 compilerOptions 声明 JVM 17。根 package.json 暴露 verify-notes，调用现有技能脚本并锁定 tsx 版本。local.properties、构建输出和签名文件不入库。

私有签名由 scripts/generate_signing.py 一次性生成独立的 RSA 4096 / PKCS12 Debug、Release 密钥库、公开证书和指纹；已有 signing/ 或 signing.properties 时拒绝覆盖。Gradle 读取根目录 signing.properties，DEBUG_* / RELEASE_* 环境变量优先，也支持 signingPropertiesFile 属性指定其他配置。未提供私有配置时源码克隆仍可使用 Android 默认 Debug 签名；自定义 Debug 配置不完整、Release 缺少签名或密钥库不存在时明确失败。Release 使用独立正式证书，不回退到 Debug。签名与发布契约见[Tag 发布](2026-10-08-signing-and-tag-releases.md)。

## Alternatives considered

- 全面升级到最新 AGP 9：可使用新工具链能力，但会额外引入构建迁移和更高 SDK 依赖；当前稳定版本满足 API 36 和 Backdrop 1.0.6。
- 只依赖本机 Gradle：可以快速启动，但其他开发者无法得到一致版本；使用 Wrapper 固定可复现入口。

## Consequences

Android Studio 或 JDK 17+ 的命令行都能打开工程。首次构建仍需联网下载依赖。原生功能开发按 TASKS.md 分单元提交，完整功能测试在开发完成后集中运行，编译失败则当场修复。

开发者需私密备份 signing/ 与 signing.properties；Release 证书丢失后不能保持既有应用更新身份。不同变体使用相同包名但不同签名，不能互相覆盖安装；验证新签名只构建与验签，不卸载手机中的旧签名应用。Release 单元测试或 Lint 的聚合任务也可能经过 preReleaseBuild，所以无签名的 CI 检查使用 testDebugUnitTest 和 lintDebug，恢复正式密钥后才执行 Release 构建。

## Verification

构建入口为 gradlew.bat :app:assembleDebug；笔记入口为 npm run verify-notes。Kotlin 2.3 拒绝旧 kotlinOptions.jvmTarget DSL 的编译错误通过改用 compilerOptions 解决，构建本身作为此配置问题的复现检查。

2026-10-08：Debug 与开启 R8 的 Release 构建成功（1分39秒），apksigner 验证二者均使用 APK Signature Scheme v2，分别匹配生成器导出的独立 RSA 4096 证书。3项生成保护测试通过；真实 Gradle 检查验证缺失 Release 签名、部分配置、密钥文件不存在均失败，空配置仍允许默认 Debug 构建。测试不读取或输出私钥、密码，不安装到手机。构建日志在忽略 Git 的 artifacts/signing-build.txt，异常配置检查日志在 artifacts/signing-validation/。
