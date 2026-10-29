// EXPERIMENT ONLY. No third-party dependencies on purpose.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Play upload signing is configured only when CI supplies the upload key via
// environment variables (from GitHub Actions secrets). No key is ever committed.
val uploadStoreFile: String? = System.getenv("LOCKDIN_UPLOAD_STORE_FILE")

android {
    namespace = "app.lockdin.probe"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.lockdin.probe"
        minSdk = 29 // UsageEvents.Event.ACTIVITY_RESUMED
        targetSdk = 36
        // CI passes the run number so every Play upload has a higher version code.
        versionCode = (System.getenv("LOCKDIN_VERSION_CODE") ?: "2").toInt()
        versionName = "0.2-probe"
    }
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(requireNotNull(uploadStoreFile))
                storePassword = System.getenv("LOCKDIN_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("LOCKDIN_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("LOCKDIN_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (uploadStoreFile != null) {
                signingConfig = signingConfigs.getByName("upload")
            } else if (System.getenv("LOCKDIN_DEMO_DEBUG_SIGNED") == "1") {
                // CI emulator demo only: an installable release build signed with
                // the throwaway debug key. Never uploaded to Play.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
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
