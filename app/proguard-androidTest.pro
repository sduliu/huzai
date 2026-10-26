# 批H-1：androidTest R8 缺失类告警抑制。
# androidx.test / okhttp3.tls 引用 errorprone 与 animal-sniffer 的**编译期注解**，
# 这些注解只存在于源码类路径、不打进运行时，R8 全量 shrink 时按缺失类报错；
# 注解本身不影响测试代码行为，按 AGP 生成的 missing_rules.txt 建议逐条 dontwarn。
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# 批I-1：测试变体整体禁用 R8 收缩/混淆/优化（debug.isMinifyEnabled 会传染 test APK）。
# 原因：am instrument 在 runner 启动即 NoClassDefFoundError kotlin/LazyKt——
# androidx.test runner、junit、compose-ui-test、kotlin stdlib 的测试类路径大量走反射
# 与 ServiceLoader，R8 无法静态判定入口，逐条 keep 是对「全保留」的脆弱近似；
# 测试 APK 无包体积/性能诉求，shrink 只有害处。AGP 9.1 DeviceTest API 无
# enableMinification 开关，testProguardFiles 是唯一作用域正确的关闭手段。
# 只作用于 androidTest 变体；主 debug/release APK 的 proguard 规则不受影响。
-dontshrink
-dontobfuscate
-dontoptimize
# 反射驱动框架（kotlin reflect-metadata、junit runner、compose semantics）依赖
# 注解/签名/内部类元数据，显式保留防 R8 属性裁剪。
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
