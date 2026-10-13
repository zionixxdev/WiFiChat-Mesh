plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.wifichat.mesh"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wifichat.mesh"
        minSdk = 24          // Android 7.0. Bluetooth RFCOMM + Room + BouncyCastle all support 24.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // ---------------------------------------------------------------------
    // ABI handling.
    //
    // WiFiChat Mesh ships ZERO native (.so) libraries: every dependency is
    // pure Java/Kotlin (Room, AndroidX, BouncyCastle). An APK with no native
    // code is architecture-neutral, so it installs and runs on both
    // armeabi-v7a and arm64-v8a devices.
    //
    // The splits block below is kept so the CI pipeline emits explicitly
    // ABI-labelled artifacts (matching the project brief) and so the ABI
    // story stays visible and verifiable. Because there is no native code,
    // the per-ABI APKs are functionally identical to the universal one; the
    // workflow verifies this by inspecting each APK's native-code list.
    // ---------------------------------------------------------------------
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // ---- AndroidX / UI (all pure Java/Kotlin, no native code) ----
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // ---- Lifecycle / coroutines ----
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ---- Persistence (Room) ----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ---- Crypto: BouncyCastle is pure Java (no .so), so it is ABI-neutral.
    //      Used for X25519 key agreement, Ed25519 signatures and AES-GCM. ----
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // ---- Unit tests (run on the JVM in CI, no device needed) ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")

    // ---- Instrumented tests (optional, device only) ----
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}

// Export the Room schema so migrations can be diffed and reviewed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
