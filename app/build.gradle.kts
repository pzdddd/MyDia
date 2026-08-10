plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // AGP 9.0+ 内置 Kotlin 支持，不再需要单独 apply 'org.jetbrains.kotlin.android'
}

android {
    namespace = "com.pzdd.mydia"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pzdd.mydia"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true
        // 只构建 arm64 + armeabi-v7a（x86 模拟器通常无 root，无 hook 意义）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // 用 AGP 默认的 debug 签名（自动生成），让 release 也能直接覆盖安装
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    // ===== 原生层（Phase 5）：libmydia_hook.so =====
    // 对齐 Dia 的 libdialbox_hook.so：时间伪造 / exit 拦截 / 敏感文件打点。
    // NDK 版本需与本机 sdkmanager 安装的一致（27.1.12297006）。
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "27.1.12297006"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            // 【关键】API 102 入口文件必须合并进 APK 根（LSPosed 扫描此路径识别模块）
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    // ===== libxposed 新 API 102 =====
    // api：入口类 XposedModule 实现（compileOnly，运行时框架提供）
    compileOnly(libs.libxposed.api)
    // 【关键】service 必须 implementation（不是 compileOnly）：
    // 它的 XposedProvider 要合并进 App Manifest，类要打包进 APK。
    // App 自身进程通过该 Provider 向 LSPosed 管理进程拿 binder，判断模块是否启用（激活检测），
    // 无需把自身加入 Xposed 作用域。
    implementation(libs.libxposed.service)

    // ===== 旧 Xposed 兼容 API（compileOnly，仅编译期用）=====
    // hook 实现层用 XposedBridge.findAndHookMethod 等静态方法。
    // 运行时这些类由 LSPosed 按 minApiVersion 决定注入的 legacy bridge 提供。
    compileOnly("de.robv.android.xposed:api:82")

    // ===== Android 基础 =====
    implementation(libs.androidx.core.ktx)
    // 注：不用 appcompat / preference / constraintlayout —— UI 全部 Compose，主题用系统 DeviceDefault

    // ===== 日志 =====
    implementation(libs.timber)

    // ===== 高阶功能依赖 =====
    // 方法改写引擎：按特征模糊查找方法/类（Dia 原版用 dexlib2+自写 MethodFinder，dexkit 更易用）
    implementation(libs.dexkit)
    // 规则 JSON 序列化（Dia 原版用 fastjson，这里用 Gson 更通用稳定）
    implementation(libs.gson)

    // ===== Jetpack Compose / Material3（全新 UI 框架）=====
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ===== 液态玻璃效果（blur + lens 折射 + vibrancy）=====
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    // ===== Haze 毛玻璃（验证长列表稳定性，作为 backdrop 的替代）=====
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)
}
