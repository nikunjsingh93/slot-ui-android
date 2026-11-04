plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "fyi.slot.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "fyi.slot.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.1.16"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    // One fixed signing key shared by every build, on any computer, so a new APK can always update the installed
    // app in place (a different key forces an uninstall, which wipes the app's data). It is the debug key, so it is public.
    signingConfigs {
        create("slot") {
            storeFile = file("slot-signing.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("slot") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("slot")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    // The libretro cores are loaded straight from the APK's native lib dir, so they must be extracted.
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.github.Swordfish90:LibretroDroid:0.14.0")
}
