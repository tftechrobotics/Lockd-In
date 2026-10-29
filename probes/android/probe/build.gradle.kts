// EXPERIMENT ONLY. No third-party dependencies on purpose.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.focus.probe"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.focus.probe"
        minSdk = 29 // UsageEvents.Event.ACTIVITY_RESUMED
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-probe"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Probe-only permissions and APIs are intentional; see AndroidManifest comments.
        abortOnError = false
        disable += setOf("ProtectedPermissions", "QueryPermissionsNeeded")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}
