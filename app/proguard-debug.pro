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
