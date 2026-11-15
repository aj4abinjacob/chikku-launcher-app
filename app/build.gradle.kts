plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Explicitly isolated, disposable acceptance builds only. Never reuse a real installation ID
// or the normal release signing configuration for version-replacement experiments.
val acceptanceId = providers.gradleProperty("chikkuTestApplicationId").orNull
val acceptanceVersionCode = providers.gradleProperty("chikkuTestVersionCode").orNull
val acceptanceVersionName = providers.gradleProperty("chikkuTestVersionName").orNull
val acceptanceReleaseValue = providers.gradleProperty("chikkuAcceptanceRelease").orNull
require(acceptanceReleaseValue == null || acceptanceReleaseValue in setOf("true", "false")) {
    "chikkuAcceptanceRelease must be true or false"
}
val acceptanceRelease = acceptanceReleaseValue == "true"
require(acceptanceId == null || Regex(
    "io\\.github\\.aj4abinjacob\\.chikku\\.acceptance\\.[a-z][a-z0-9_]{0,47}",
).matches(acceptanceId)) { "chikkuTestApplicationId must be an isolated acceptance scenario ID" }
require(acceptanceId != null || (acceptanceVersionCode == null && acceptanceVersionName == null &&
    acceptanceReleaseValue == null)) {
    "Acceptance version/signing overrides require chikkuTestApplicationId"
}
val parsedAcceptanceVersionCode = acceptanceVersionCode?.let {
    require(Regex("[1-9][0-9]*").matches(it)) { "Acceptance versionCode must be positive" }
    requireNotNull(it.toIntOrNull()?.takeIf { code -> code <= 2_100_000_000 }) {
        "Acceptance versionCode is out of Android's supported range"
    }
}
require(acceptanceVersionName == null || (acceptanceVersionName.isNotBlank() &&
    acceptanceVersionName.length <= 80 && acceptanceVersionName.none { it.isISOControl() })) {
    "Acceptance versionName must be nonblank, bounded and contain no control characters"
}
val acceptanceKeyStore = if (acceptanceRelease) {
    file(requireNotNull(providers.environmentVariable("CHIKKU_ACCEPTANCE_KEYSTORE").orNull) {
        "Acceptance release requires an external disposable CHIKKU_ACCEPTANCE_KEYSTORE"
    }).also {
        require(it.isFile && !it.toPath().toRealPath().startsWith(
            rootDir.toPath().toRealPath(),
        )) { "Acceptance keystore must exist outside the repository" }
    }
} else null
val acceptanceStorePassword = if (acceptanceRelease) {
    requireNotNull(providers.environmentVariable("CHIKKU_ACCEPTANCE_STORE_PASSWORD").orNull) {
        "Acceptance release requires CHIKKU_ACCEPTANCE_STORE_PASSWORD"
    }.also { require(it.isNotEmpty()) { "Acceptance keystore password must not be empty" } }
} else null

// Upload signing is distinct from Play's app signing key. All secrets stay outside Git.
val uploadVariables = listOf(
    "CHIKKU_UPLOAD_KEYSTORE", "CHIKKU_UPLOAD_STORE_PASSWORD",
    "CHIKKU_UPLOAD_KEY_ALIAS", "CHIKKU_UPLOAD_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val uploadSigningRequested = uploadVariables.values.any { it != null }
require(!uploadSigningRequested || uploadVariables.values.all { !it.isNullOrBlank() }) {
    "Upload signing requires all four CHIKKU_UPLOAD_* environment variables"
}
require(!uploadSigningRequested || acceptanceId == null) {
    "Production upload signing cannot be combined with an acceptance application ID"
}
val uploadKeyStore = if (uploadSigningRequested) {
    file(requireNotNull(uploadVariables["CHIKKU_UPLOAD_KEYSTORE"])).also {
        require(it.isFile && !it.toPath().toRealPath().startsWith(rootDir.toPath().toRealPath())) {
            "Upload keystore must exist outside the repository"
        }
    }
} else null
require(gradle.startParameter.taskNames.none {
    it.substringAfterLast(':').lowercase() in setOf("bundlerelease", "bundle")
} || (uploadSigningRequested && acceptanceId == null)) {
    "A Play bundle requires external CHIKKU_UPLOAD_* signing access and the production application ID"
}

android {
    // F-Droid and IzzyOnDroid reject the Google-encrypted dependency block in APK signatures.
    // The AAB keeps it for Play.
    dependenciesInfo {
        includeInApk = false
    }

    namespace = "io.github.aj4abinjacob.chikku"
    compileSdk = 37

    defaultConfig {
        applicationId = acceptanceId ?: "io.github.aj4abinjacob.chikku"
        minSdk = 29
        // Stay on 36 until the Android 17 RemoteViews memory rules are validated (docs/ANDROID_APIS.md).
        targetSdk = 36
        versionCode = parsedAcceptanceVersionCode ?: 21
        versionName = acceptanceVersionName ?: "0.1.0-beta.20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (acceptanceRelease) {
        signingConfigs.create("disposableAcceptance") {
            storeFile = acceptanceKeyStore
            storePassword = acceptanceStorePassword
            keyAlias = providers.environmentVariable("CHIKKU_ACCEPTANCE_KEY_ALIAS")
                .getOrElse("chikku-acceptance")
            keyPassword = providers.environmentVariable("CHIKKU_ACCEPTANCE_KEY_PASSWORD")
                .orNull ?: acceptanceStorePassword
        }
        testBuildType = "release"
    }

    if (uploadSigningRequested) {
        signingConfigs.create("playUpload") {
            storeFile = uploadKeyStore
            storePassword = uploadVariables["CHIKKU_UPLOAD_STORE_PASSWORD"]
            keyAlias = uploadVariables["CHIKKU_UPLOAD_KEY_ALIAS"]
            keyPassword = uploadVariables["CHIKKU_UPLOAD_KEY_PASSWORD"]
        }
    }

    buildTypes {
        debug {
            if (acceptanceId == null) applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            if (uploadSigningRequested) signingConfig = signingConfigs.getByName("playUpload")
            // R8 and the nondebuggable production runtime remain active in acceptance builds.
            if (acceptanceRelease) {
                signingConfig = signingConfigs.getByName("disposableAcceptance")
                proguardFiles("acceptance-app-proguard-rules.pro")
                testProguardFiles("acceptance-test-proguard-rules.pro")
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 4.17 reflects into JDK internals; newer JDKs (e.g. Studio's JBR 25) need these opened.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }

    // Native migration fixtures read the exact checked-in Room schemas, including earlier versions.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
    }
}

// Unsigned APKs remain useful for static inspection; a Play bundle must have upload signing.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(uploadSigningRequested && acceptanceId == null) {
            "A Play bundle requires external CHIKKU_UPLOAD_* signing access and the production application ID"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.recyclerview)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.truth)
}
