import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing. Sources, highest precedence first:
//   1. RELEASE_KEYSTORE / RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_ALIAS /
//      RELEASE_KEY_PASSWORD environment variables (GitHub Actions).
//   2. signing.properties in the repo root, gitignored (local builds):
//      keystore.file / keystore.password / key.alias / key.password.
// With none of these set, assembleRelease produces an unsigned APK so CI on
// fork PRs can still exercise the release build.
val releaseSigning = run {
    val props = Properties()
    val propsFile = rootProject.file("signing.properties")
    if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }

    fun value(envVar: String, key: String): String? =
        providers.environmentVariable(envVar).orNull?.takeIf { it.isNotBlank() }
            ?: props.getProperty(key)?.takeIf { it.isNotBlank() }

    listOf(
        value("RELEASE_KEYSTORE", "keystore.file"),
        value("RELEASE_KEYSTORE_PASSWORD", "keystore.password"),
        value("RELEASE_KEY_ALIAS", "key.alias"),
        value("RELEASE_KEY_PASSWORD", "key.password")
    )
}
when (releaseSigning.count { it != null }) {
    0 -> {} // no credentials configured: unsigned release build
    4 -> require(file(releaseSigning[0]!!).isFile) {
        "Release signing: keystore file not found: ${releaseSigning[0]}"
    }
    else -> throw GradleException(
        "Release signing is partially configured. Set all of RELEASE_KEYSTORE, " +
            "RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD " +
            "(or all four signing.properties keys), or none to build unsigned."
    )
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

    if (releaseSigning[0] != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigning[0]!!)
                storePassword = releaseSigning[1]
                keyAlias = releaseSigning[2]
                keyPassword = releaseSigning[3]
                // keytool -printcert -jarfile only reads v1 (JAR) signatures;
                // keep v1 enabled so the certificate stays inspectable that way.
                enableV1Signing = true
            }
        }
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
            if (releaseSigning[0] != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

// KGP 2.4 removed the kotlinOptions DSL; compiler options go through compilerOptions.
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    // org.json ships with the Android runtime; JVM unit tests need it explicitly.
    testImplementation("org.json:json:20240303")
    // XmlPullParser is provided by the Android framework; JVM unit tests need kxml2 + xmlpull.
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
