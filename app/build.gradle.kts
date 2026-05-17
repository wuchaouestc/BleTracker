plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.chaquo.python")
    kotlin("kapt")
}

android {
    namespace = "com.example.bletracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bletracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Chaquopy Python configuration (use pre-built Python runtime)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ═══════════════════════════════════════════════════════
//  🔒 版本一致性钩子 — 编译前自动校验
//  确保 SettingsScreen 等所有 UI 文件中的版本号
//  与 defaultConfig.versionName 完全一致
// ═══════════════════════════════════════════════════════
tasks.register("validateVersionConsistency") {
    description = "校验所有源文件中的硬编码版本号是否与 build.gradle.kts 的 versionName 一致"
    group = "verification"

    doLast {
        val canonicalVersion = android.defaultConfig.versionName
        if (canonicalVersion == null || canonicalVersion.isBlank()) {
            throw GradleException("❌ defaultConfig.versionName 为空，请检查 build.gradle.kts")
        }

        // 匹配模式: 硬编码的版本号字符串（如 "1.0.1", "1.0.12", "2.0.0-beta"）
        val versionPattern = Regex(""""(\d+\.\d+\.\d+(?:-[a-zA-Z0-9]+)?(?:\s*\(MVP\))?)"""")
        val sourceDirs = listOf(
            file("src/main/java"),
            file("src/main/kotlin")
        )

        val violations = mutableListOf<String>()
        sourceDirs.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.extension in listOf("kt", "java") }.forEach { file ->
                // 跳过 BuildConfig 生成文件
                if (file.path.contains("BuildConfig")) return@forEach
                val content = file.readText()
                versionPattern.findAll(content).forEach { match ->
                    val found = match.groupValues[1].trim()
                    if (found != canonicalVersion) {
                        violations.add("  📄 ${file.relativeTo(rootDir)} → 发现硬编码版本 \"$found\"，应为 \"$canonicalVersion\"")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            val header = "╔══════════════════════════════════════════════════╗"
            val footer = "╚══════════════════════════════════════════════════╝"
            val msg = buildString {
                appendLine()
                appendLine(header)
                appendLine("║  🔒 版本号一致性校验失败！                       ║")
                appendLine("║  canonicalVersion = $canonicalVersion".padEnd(51) + "║")
                appendLine("╠══════════════════════════════════════════════════╣")
                violations.forEach { appendLine(it.padEnd(51) + "║") }
                appendLine("╠══════════════════════════════════════════════════╣")
                appendLine("║  👉 修复方式: 将硬编码替换为 BuildConfig.VERSION_NAME  ║")
                appendLine(footer)
            }
            throw GradleException(msg)
        }
        println("✅ 版本一致性校验通过 (versionName=$canonicalVersion)")
    }
}

// 将钩子挂载到 preBuild — 每次编译前自动执行
project.afterEvaluate {
    tasks.matching { it.name.startsWith("preBuild") }.configureEach {
        dependsOn("validateVersionConsistency")
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Navigation
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Core KTX
    implementation("androidx.core:core-ktx:1.12.0")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
