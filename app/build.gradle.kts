import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mycards.wallet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mycards.wallet"
        minSdk = 29
        targetSdk = 36          // Android 16
        versionCode = 9
        versionName = "1.8"

        // vivo X200 Pro mini — 64-битный ARM. Оставляем только эту архитектуру,
        // чтобы APK был компактнее (модели ML Kit встроены в приложение).
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Постоянный ключ подписи. Пока он один и тот же, обновления ставятся поверх старой версии,
    // и данные приложения (карты, документы) сохраняются. Ключ лежит в репозитории — не меняйте его.
    signingConfigs {
        create("release") {
            storeFile = file("mycards-release.jks")
            storePassword = "a87c98d1749b7b43e4e5b7c31e0123aa"
            keyAlias = "mycards"
            keyPassword = "a87c98d1749b7b43e4e5b7c31e0123aa"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Камера
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Распознавание кодов и текста — «bundled»-версии ML Kit: модели внутри APK,
    // работают без интернета и без сервисов Google Play.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Генерация штрихкодов для цифровой копии карты
    implementation("com.google.zxing:core:3.5.3")

    // Фирменные цвета по логотипу магазина
    implementation("androidx.palette:palette-ktx:1.0.0")
}
