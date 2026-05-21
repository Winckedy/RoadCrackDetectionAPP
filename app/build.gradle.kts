plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.example.roaddamagedetector"
    compileSdk {
        version = release(36)
    buildFeatures {
        viewBinding = true
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
        }
    }
}

    defaultConfig {
        applicationId = "com.example.roaddamagedetector"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    // 替换为新的 compilerOptions
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
dependencies {

    // 1. AndroidX & UI 核心库
    // androidx-base bundle 包含了 core-ktx, appcompat, material, constraintlayout
    implementation(libs.bundles.androidx.base)
    // activity-ktx 提供了 'by viewModels()' 等便捷的扩展功能
    implementation(libs.androidx.activity)

    // 2. 架构组件 (Lifecycle, Room)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler) // Room 的注解处理器

    // 3. 功能库 (CameraX, PyTorch, Glide 等)
    implementation(libs.bundles.androidx.camera) // camera bundle 已包含所有 camera 相关库
    implementation(libs.bundles.pytorch.mobile)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler) // Glide 的注解处理器
    implementation(libs.permissionx) // 权限请求库
    implementation(libs.androidx.exifinterface) // 图片 EXIF 信息库

    // 4. 其他工具库 (POI, Desugaring)
    implementation(libs.apache.poi.ooxml) // Excel 文件处理
    coreLibraryDesugaring(libs.android.desugar.jdk.libs) // Java 8+ API 兼容

    // 5. 测试库
    testImplementation(libs.junit) // 本地单元测试
    androidTestImplementation(libs.androidx.junit) // UI/设备测试
    androidTestImplementation(libs.androidx.espresso.core) // UI/设备测试
}

