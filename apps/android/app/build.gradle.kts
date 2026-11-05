plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Private Play upload signing is supplied at build time, never committed.
val uploadStoreFile = System.getenv("LOCKDIN_UPLOAD_STORE_FILE")?.takeIf { it.isNotBlank() }
val playBuild = System.getenv("LOCKDIN_PLAY_BUILD") == "1"
fun uploadSetting(name: String): String = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) {
    "Missing $name for private Play upload signing"
}
require(!playBuild || uploadStoreFile != null) { "Play builds require a private upload keystore" }
require(!playBuild || System.getenv("LOCKDIN_VERSION_CODE")?.toIntOrNull()?.let { it > 0 } == true) {
    "Play builds require an explicit positive LOCKDIN_VERSION_CODE"
}

android {
    namespace = "app.lockdin"
    compileSdk = 36

    defaultConfig {
        // Partner service (R49). Only partner setup, partner status and messages to admin use it.
        buildConfigField("String", "PARTNER_URL", "\"https://lockdin-partner.lockdin-tf.workers.dev\"")
        applicationId = "app.lockdin"
        minSdk = 29
        targetSdk = 36
        versionCode = (System.getenv("LOCKDIN_VERSION_CODE") ?: "1").toInt()
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = uploadSetting("LOCKDIN_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadSetting("LOCKDIN_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("LOCKDIN_UPLOAD_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: uploadSetting("LOCKDIN_UPLOAD_STORE_PASSWORD")
            }
        }
        // Public, committed TEST key so every CI debug build can update the previous one when
        // sideloaded for testing. Never used for Play: Play builds are signed with the upload key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = false
            if (uploadStoreFile != null) signingConfig = signingConfigs.getByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets {
        // One source of truth for cross-implementation timing tests: the vectors
        // generated from the Rust core (core/examples/parity_vectors.rs).
        getByName("test").resources.srcDir("../../web/src/engine")
    }
    testOptions { unitTests.isReturnDefaultValues = false }
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false) // revisit once the Compose code has settled through CI
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.play.services.location)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
