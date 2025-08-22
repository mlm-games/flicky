@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")


}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

android {
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "app.flicky"
        minSdk = 24
        targetSdk = 36
        versionCode = 250
        versionName = "2.3.2"

        androidResources {
            localeFilters += setOf("en", "ar", "de", "es-rES", "es-rUS", "fr", "hr", "hu", "in", "it", "ja", "pl", "pt-rBR", "ru-rRU", "sv", "tr", "uk", "zh")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val enableApkSplits = (providers.gradleProperty("enableApkSplits").orNull ?: "false").toBoolean()
    val targetAbi = providers.gradleProperty("targetAbi").orNull

    splits {
        abi {
            isEnable = enableApkSplits
            reset()
            if (enableApkSplits) {
                if (targetAbi != null) {
                    include(targetAbi)
                } else {
                    include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                }
            }
            isUniversalApk = false
        }
    }

    applicationVariants.all {
        val buildingApk = gradle.startParameter.taskNames.any { it.contains("assemble", ignoreCase = true) }
        if (!buildingApk) return@all

        val variant = this
        outputs.all {
            if (this is ApkVariantOutputImpl) {
                val abiName = filters.find { it.filterType == "ABI" }?.identifier
                if (abiName != null) {
                    val base = variant.versionCode
                    val abiVersionCode = when (abiName) {
                        "x86" -> base - 3
                        "x86_64" -> base - 2
                        "armeabi-v7a" -> base - 1
                        "arm64-v8a" -> base
                        else -> base
                    }
                    versionCodeOverride = abiVersionCode
                    outputFileName = "flicky-${variant.versionName}-${abiName}.apk"
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootProject.projectDir}/release.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
            enableV4Signing = false
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("Long", "BUILD_TIME", "0L")
            isShrinkResources = true
        }
        getByName("debug") {
            isShrinkResources = false
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.0.0"
    }

    namespace = "app.flicky"


    dependenciesInfo {
        includeInApk = false
    }
}

// Configure all tasks that are instances of AbstractArchiveTask
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.kotlin.stdlib)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)

    // Android lifecycle
    implementation(libs.lifecycle.extensions)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Navigation
    implementation(libs.navigation.fragment.ktx)

    // Work Manager
    implementation(libs.work.runtime.ktx)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.runtime)
    implementation(libs.okhttp)


    //Material dependencies
    implementation(libs.material)
    implementation(libs.material3.android)

    // Compose dependencies
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.constraintlayout.compose.android)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidbrowserhelper)
    implementation(libs.androidx.datastore.preferences.core)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Testing
//    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
}
