plugins {
    id("com.android.application")
}

android {
    namespace = "com.yaniv.japanesewalkingtimer" // Windsurf can correct this later if needed
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yaniv.japanesewalkingtimer"
        minSdk = 26 // Android 8.0, minimum needed for good Foreground Services
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Standard Android UI and WebView dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.10.0")
}