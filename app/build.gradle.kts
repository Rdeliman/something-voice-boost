plugins {
    id("com.android.application")
}

android {
    namespace = "app.something.voiceboost"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.something.voiceboost"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
