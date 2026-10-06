import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The TrueTime key lives in the untracked local.properties, or in the PRT_API_KEY environment
// variable that the release workflow fills from a GitHub secret. A missing key still builds,
// and the app asks for one on first launch instead.
val prtApiKey: String =
    rootProject
        .file("local.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
        ?.getProperty("PRT_API_KEY")
        ?: providers.environmentVariable("PRT_API_KEY").orNull.orEmpty()

// Release signing comes only from environment variables (set by the release workflow from
// GitHub secrets). Without them assembleRelease still builds, but the APK is unsigned.
val releaseKeystore: String? = providers.environmentVariable("OPENPRT_KEYSTORE_FILE").orNull

// Splitting by ABI keeps each release APK to one copy of MapLibre's native library. Only
// release builds split, so assembleDebug and installDebug keep producing a single APK.
val isReleaseBuild: Boolean = gradle.startParameter.taskNames.any { it.contains("Release") }

android {
    namespace = "org.openprt.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.openprt.app"
        minSdk = 26
        targetSdk = 37
        versionName = providers.gradleProperty("VERSION_NAME").get()
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        buildConfigField("String", "PRT_API_KEY", "\"$prtApiKey\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("OPENPRT_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("OPENPRT_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("OPENPRT_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    splits {
        abi {
            isEnable = isReleaseBuild
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's SDK 36+ shadows reach into JDK internals for FileDescriptor.
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    // Committed so future schema changes can be checked against shipped versions.
    schemaDirectory("$projectDir/schemas")
}

ktlint {
    version.set(libs.versions.ktlint.cli)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.maplibre.android)
    implementation(libs.work.runtime.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
