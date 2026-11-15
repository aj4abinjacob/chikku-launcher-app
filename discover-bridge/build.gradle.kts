plugins {
    alias(libs.plugins.android.application)
}

// Optional helper for Google Discover cards in Chikku release builds (ADR-0004).
// Google's overlay service only accepts system or debuggable clients, so this app is
// debuggable in every build type. It stores no data and must be signed with Chikku's key.
android {
    namespace = "io.github.aj4abinjacob.chikku.discoverbridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.aj4abinjacob.chikku.discoverbridge"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildTypes {
        release {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}
