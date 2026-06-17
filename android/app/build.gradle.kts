plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

// Статанализ Kotlin (ТЗ §14). Non-gating: detekt формирует отчёт, но не валит сборку
// (ignoreFailures). Перевод в gating — после генерации baseline на машине с Android SDK.
detekt {
    buildUponDefaultConfig = true
    ignoreFailures = true
    parallel = true
}

// google-services.json в .gitignore (секреты не коммитятся, ТЗ §13) — в CI файла нет.
// Плагин применяется только при наличии файла, иначе сборка без конфига Firebase падала бы.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "com.wasat.shop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wasat.shop"
        minSdk = 28          // ТЗ §12
        targetSdk = 36       // обязательно для Google Play с авг. 2026 (ТЗ §12)
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Web Client ID (OAuth) для Sign in with Google. Не секрет (встраивается в APK).
        // Переопределяется gradle-свойством/env; дефолт — Web client проекта wasat-21a20.
        // Вход всё равно гейтится isConfigured (нужен ещё google-services.json в рантайме).
        val webClientId = providers.gradleProperty("wasat.googleWebClientId").orNull
            ?: System.getenv("WASAT_GOOGLE_WEB_CLIENT_ID")
            ?: "925529408936-i5qv589k9okek7gq7mpc40spdmrgm4ue.apps.googleusercontent.com"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildTypes {
        debug {
            // 10.0.2.2 — host-машина из Android-эмулятора (локальный server, PORT=8080)
            val baseUrl = providers.gradleProperty("wasat.apiBaseUrl").orNull
                ?: "http://10.0.2.2:8080/"
            buildConfigField("String", "API_BASE_URL", "\"$baseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Прод-URL подставляется при настройке деплоя (Фаза 5)
            buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
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

    // Исходники размещены в src/main/kotlin.
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM управляет версиями Compose-артефактов.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Offline
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Изображения
    implementation(libs.coil.compose)

    // Пагинация каталога (FR-B02)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Кэш последнего магазина (FR-B01)
    implementation(libs.androidx.datastore.preferences)
    // QR-сканер slug (FR-B01): системный Google code scanner, без camera-permission
    implementation(libs.play.services.code.scanner)

    // Firebase (BOM управляет версиями)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.config)

    // Google Sign-In через Credential Manager (ТЗ §4.1)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Платежи
    implementation(libs.stripe.android)

    // Сеть: Retrofit + OkHttp + kotlinx-serialization (контракт docs/api-contract.md)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // Task<T>.await() для Firebase/Play Services API
    implementation(libs.kotlinx.coroutines.play.services)

    // Тесты
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // createComposeRule() поднимает пустую Activity из этого артефакта
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
