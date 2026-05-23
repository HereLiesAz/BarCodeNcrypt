import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt)
}

// Release signing config reads keystore credentials from environment variables
// (in CI) or from a git-ignored keystore.properties file (locally). See
// docs/release.md for the keystore generation + storage process.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().also { props ->
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { props.load(it) }
    }
}

fun signingValue(envKey: String, propsKey: String): String? =
    System.getenv(envKey) ?: keystoreProps.getProperty(propsKey)

android {
    namespace = "com.hereliesaz.barcodencrypt"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hereliesaz.barcodencrypt"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("KEYSTORE_PATH", "storeFile")
            val storePass = signingValue("KEYSTORE_PASSWORD", "storePassword")
            val alias = signingValue("KEY_ALIAS", "keyAlias")
            val keyPass = signingValue("KEY_PASSWORD", "keyPassword")
            if (storePath != null && storePass != null && alias != null && keyPass != null) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
            // If any value is missing the signing config silently stays empty; release
            // assembly will fall through to debug signing or fail at the sign step.
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Keep debug builds fast and avoid masking issues behind R8.
            isMinifyEnabled = false
            isShrinkResources = false
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // Core Android & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.google.firebase.appcheck.debug)
    implementation(libs.google.firebase.appcheck.playintegrity)


    // Lifecycle, ViewModel, LiveData
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.compose.runtime.livedata)


    // Room for Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.googleid)
    implementation(libs.androidx.monitor)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)


    // CameraX for QR Code Scanning
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit for Barcode Scanning
    implementation(libs.barcode.scanning)

    // ZXing for QR Code generation (Consider replacing with a more modern library if issues arise)
    implementation(libs.core)

    // Gson for JSON processing
    implementation(libs.gson)

    // Tink for Crypto
    implementation(libs.tink.android)

    // SQLCipher for Database Encryption
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // AzNavRail
    implementation(libs.aznavrail)

    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Security
    implementation(libs.security.crypto)

    // Argon2id for the v5 ratchet bootstrap KDF (Plan 2)
    implementation(libs.argon2kt)


    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}