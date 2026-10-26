# 批H-1：androidTest R8 缺失类告警抑制。
# androidx.test / okhttp3.tls 引用 errorprone 与 animal-sniffer 的**编译期注解**，
# 这些注解只存在于源码类路径、不打进运行时，R8 全量 shrink 时按缺失类报错；
# 注解本身不影响测试代码行为，按 AGP 生成的 missing_rules.txt 建议逐条 dontwarn。
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
