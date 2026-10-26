# 批J-1：debug 变体保留 kotlin/kotlinx 全部类定义与规范名。
# 原因：debug.isMinifyEnabled 让 R8 把 kotlin 多文件门面水平合并
# （LazyKt→LazyKt__LazyKt、CollectionsKt→CollectionsKt__CollectionsKt），
# 合并后的 __ 类在主包被改成 public，而 stdlib jar 原版是 package-private——
# instrument 进程里主包调用方按改名后引用撞上测试 dex 的包私有副本即
# IllegalAccessError；androidTest 侧也无法按规范名解析 → NoClassDefFoundError。
# debug 包是 nightly/真机测试分发形态，保留 canonical 名换取 instrument 可运行；
# release 变体规则完全不动（其 proguard 链独立），正式包不受任何影响。
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# 批K-1：一次性审计扩展 keep 边界——androidx 共享库 + app 自身类全保留。
# instrument 审计（test dex 引用 vs 主包 class_def）：kotlin 修复后仍缺
# androidx.compose.*（InfiniteAnimationPolicy$DefaultImpls 等，compose-ui-test
# 框架传递需要、App 不直接引用被裁）、androidx.concurrent.futures.*、
# com.kudo.android.*（主包自身类被改名，直调 app 类的测试无对齐目标）。
# 边界理由：androidx/kotlin/kotlinx 是测试类路径与主包的全部共享库空间，
# com.kudo 成员级 keep 使测试可按源码名调用 app 方法；四类合起来覆盖审计到的
# 全部缺失类别而非单个类名，后续同类漂移自动被边界吸收。
# 未覆盖：okhttp3 WebSocket/okio（mockwebserver WS 专用路径，现无用例需要）、
# junit 可选依赖（easymock/jmock，junit 自带容错）、org.json/org.w3c（Android
# 框架自带）、纯编译期注解。代价：debug 包放弃 androidx+app 代码 shrink，
# 体积与内联优化退步；release 不受影响。
-keep class androidx.** { *; }
-keep class com.kudo.android.** { *; }
