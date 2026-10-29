// EXPERIMENT ONLY. No third-party dependencies on purpose: the probe should
// measure the platform, not a library.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.focus.probe"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.focus.probe"
        minSdk = 29 // UsageEvents.Event.ACTIVITY_RESUMED
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-probe"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
