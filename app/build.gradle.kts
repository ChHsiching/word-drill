plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.chsiching.worddrill"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.chsiching.worddrill"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
        // Ticket #9: the About page reads BuildConfig.VERSION_NAME to show the version
        buildConfig = true
    }
}

// Kotlin 2.4.x compiler options go through the compilerOptions DSL (kotlinOptions is deprecated)
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM pins versions for all Compose libraries
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (first-run import idempotence flag, current book and other settings)
    implementation(libs.androidx.datastore.preferences)

    // Ticket #24: AndroidX SplashScreen -- set the system splash icon to a transparent
    // drawable so the custom Compose launch animation takes over seamlessly
    // (API 31+ forces a system splash).
    implementation(libs.androidx.core.splashscreen)

    // Ticket #21: PDF table extraction. tabula-java depends on PDFBox 2.0.x (which uses
    // java.awt, absent on Android) and has been unmaintained since 2021; use PdfBox-Android
    // instead (an Android port of PDFBox 2.0.27).
    implementation(libs.pdfbox.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    // org.json ships with the Android runtime; JVM unit tests need it explicitly to verify JSON parsing
    testImplementation("org.json:json:20240303")
    // XmlPullParser is provided by the Android framework; JVM unit tests need kxml2 + xmlpull for OOXML parsing
    testImplementation(libs.kxml2)
    testImplementation(libs.xmlpull)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
