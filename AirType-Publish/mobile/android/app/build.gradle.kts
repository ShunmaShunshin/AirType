plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.airtype.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.airtype.phone"
        minSdk = 24
        targetSdk = 34
        versionCode = 16
        versionName = "1.0.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 零第三方依赖：UI 使用自研 Kuikly 风格声明式 DSL（渲染到框架 View），JSON 用内置 org.json。
}
