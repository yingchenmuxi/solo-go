import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sologo.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sologo.app"
        // 随包的 Android KataGo 构建按 API 29 工具链生成，保持一致以避免旧系统符号不兼容。
        minSdk = 29
        // KataGo 只有 arm64-v8a 二进制，限制 ABI 可避免在 x86 设备上安装后才报引擎不可用。
        ndk {
            abiFilters += "arm64-v8a"
        }
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    androidResources {
        // KataGo 权重和二进制不能压缩；模型通常很大，保持原样便于流式复制与校验。
        // txt 也保持不压缩，避免 AAPT 对模型资产做二次压缩导致 openFd/流式复制路径失效。
        noCompress += listOf("katago", "bin", "gz", "cfg", "so", "txt")
    }

    packaging {
        jniLibs {
            // 让 AGP 把 libkatago.so 解压到 nativeLibraryDir。
            // Android 10+ 禁止从 filesDir 直接 exec 二进制，这是 KataGo 能启动的关键。
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))
    implementation(project(":sgf"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.billing.ktx)
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// 构建完成后额外生成带时间戳的 APK，便于区分不同真机测试包。
// 保留 app-debug.apk / app-release.apk，Android Studio 的 Run/Install 流程不受影响。
val debugApkProvider = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
val debugApkDirProvider = layout.buildDirectory.dir("outputs/apk/debug")
val releaseApkProvider = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
val releaseApkDirProvider = layout.buildDirectory.dir("outputs/apk/release")

tasks.register("copyDebugApkWithTimestamp") {
    // 文件名包含构建时刻，因此该任务不参与 Gradle configuration cache。
    notCompatibleWithConfigurationCache("Build timestamp is generated when the APK is copied")
    doLast {
        val source = debugApkProvider.get().asFile
        if (!source.exists()) {
            logger.warn("未找到 debug APK，跳过时间戳副本: ${source.absolutePath}")
            return@doLast
        }
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val target = File(
            debugApkDirProvider.get().asFile,
            "sologo_${formatter.format(Date())}.apk"
        )
        source.copyTo(target, overwrite = true)
        logger.lifecycle("时间戳 APK: ${target.absolutePath}")
    }
}

tasks.register("copyReleaseApkWithTimestamp") {
    notCompatibleWithConfigurationCache("Build timestamp is generated when the APK is copied")
    doLast {
        val source = releaseApkProvider.get().asFile
        if (!source.exists()) {
            logger.warn("未找到 release APK，跳过时间戳副本: ${source.absolutePath}")
            return@doLast
        }
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val target = File(
            releaseApkDirProvider.get().asFile,
            "sologo_${formatter.format(Date())}.apk"
        )
        source.copyTo(target, overwrite = true)
        logger.lifecycle("时间戳 APK: ${target.absolutePath}")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyDebugApkWithTimestamp")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyReleaseApkWithTimestamp")
}
