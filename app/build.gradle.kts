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
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
