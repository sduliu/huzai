import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val path = providers.gradleProperty("signingPropertiesFile").orElse("signing.properties").get()
    val configuration = rootProject.file(path)
    if (configuration.isFile) {
        configuration.inputStream().use { load(it) }
    } else if (providers.gradleProperty("signingPropertiesFile").isPresent) {
        throw GradleException("The specified signingPropertiesFile does not exist.")
    }
}

fun signingOptions(variant: String): Map<String, String> = mapOf(
    "storeFile" to "KEYSTORE_PATH",
    "storePassword" to "STORE_PASSWORD",
    "keyAlias" to "KEY_ALIAS",
    "keyPassword" to "KEY_PASSWORD",
    "storeType" to "KEYSTORE_TYPE",
).mapNotNull { (property, environment) ->
    val value = providers.environmentVariable("${variant.uppercase()}_$environment").orNull
        ?: signingProperties.getProperty("$variant.$property")
    value?.takeIf { it.isNotBlank() }?.let { property to it }
}.toMap()

val requiredSigningOptions = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val debugSigning = signingOptions("debug")
val releaseSigning = signingOptions("release")

fun validateSigning(variant: String, options: Map<String, String>) {
    val missing = requiredSigningOptions.filter { it !in options }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "$variant signing is incomplete: ${missing.joinToString()}. " +
                "Configure signing.properties or the ${variant.uppercase()}_* environment variables.",
        )
    }
    if (!rootProject.file(options.getValue("storeFile")).isFile) {
        throw GradleException("$variant keystore does not exist. Check the signing storeFile configuration.")
    }
}

android {
    namespace = "com.kudo.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.huzai.kudo"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orElse("1").get()
            .toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
            ?: throw GradleException("versionCode must be an integer between 1 and 2100000000.")
        versionName = providers.gradleProperty("versionName").orElse("0.1.0").get()
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (debugSigning.isNotEmpty()) {
            validateSigning("Debug", debugSigning)
            getByName("debug") {
                storeFile = rootProject.file(debugSigning.getValue("storeFile"))
                storePassword = debugSigning.getValue("storePassword")
                keyAlias = debugSigning.getValue("keyAlias")
                keyPassword = debugSigning.getValue("keyPassword")
                storeType = debugSigning["storeType"] ?: "PKCS12"
            }
        }
        create("release") {
            storeFile = releaseSigning["storeFile"]?.let { rootProject.file(it) }
            storePassword = releaseSigning["storePassword"]
            keyAlias = releaseSigning["keyAlias"]
            keyPassword = releaseSigning["keyPassword"]
            storeType = releaseSigning["storeType"] ?: "PKCS12"
        }
    }
    buildTypes {
        debug {
            // 卡帧优化：nightly 以 debug 签名分发但走 R8 全量优化
            // （debuggable 开销保留，字节码/资源已收缩，接近 release 流畅度）
            isMinifyEnabled = true
            // 批O：R8 集成资源收缩会用「未注入版本号的源清单」重写 manifest——
            // packaged_manifests 带 -PversionCode/-PversionName，但 shrunk .ap_
            // 里的清单掉回 1/0.1.0（全量重打后暴露）。关掉资源收缩让版本生效；
            // 字节码仍走 R8，只有未引用资源不再剥离（APK 体积略增）。
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-debug.pro")
            testProguardFiles("proguard-androidTest.pro")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/dohJni").get().asFile)
    if (providers.gradleProperty("dohProbe").orNull == "true") {
        sourceSets.getByName("androidTest").jniLibs.srcDir(layout.buildDirectory.dir("generated/dohProbeJni").get().asFile)
    }
}

val buildDoh by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the Rust arm64 DoH/ECH transport with 16KB alignment."
    inputs.files(rootProject.fileTree("native/doh") { include("Cargo.toml", "Cargo.lock", "src/**") })
    inputs.file(rootProject.file("scripts/build_doh.py"))
    inputs.file(rootProject.file("scripts/native_notices.py"))
    inputs.file(rootProject.file("app/src/main/assets/licenses/native-doh-NOTICES.txt"))
    inputs.files(rootProject.fileTree("native/doh/licenses"))
    outputs.file(layout.buildDirectory.file("generated/dohJni/arm64-v8a/libkudo_doh.so"))
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        rootProject.file("scripts/build_doh.py"), "--sdk",
        androidComponents.sdkComponents.sdkDirectory.get().asFile.absolutePath)
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(buildDoh) }

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Rejects Release builds without a complete, private signing configuration."
    doLast { validateSigning("Release", releaseSigning) }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("io.github.kyant0:capsule:2.1.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 虎仔 UI 子树（com.kudo.android.hupuui）使用 coil2 与 media3
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-gif:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
    implementation("com.github.penfeizhou.android.animation:apng:3.0.5")
    implementation("com.github.penfeizhou.android.animation:awebp:3.0.5")
    implementation("com.github.penfeizhou.android.animation:avif:3.0.5")
    implementation("org.aomedia.avif.android:avif:1.1.1.14d8e3c4")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.24.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // 批J-1：撤销批I 的 libs/ stdlib 嵌入——双份 stdlib 在 instrument 进程与主包
    // R8 合并类撞访问权限（CollectionsKt__CollectionsKt 包私有版被 androidx 访问
    // → IllegalAccessError）。改由 proguard-debug.pro 让 debug 主包保留 kotlin
    // canonical 类，测试引用从主包解析，单一 stdlib 来源。
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
