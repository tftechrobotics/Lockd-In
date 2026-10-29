// Harmless test app for the Focus probe. EXPERIMENT ONLY.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.focus.probetarget"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.focus.probetarget"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-target"
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
