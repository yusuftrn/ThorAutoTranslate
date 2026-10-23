import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val distributionProperties = Properties().apply {
    val file = rootProject.file("signing/release.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

android {
    namespace = "com.thorautotranslate"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.thorautotranslate"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        ndk { abiFilters += "arm64-v8a" }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    signingConfigs {
        if (distributionProperties.isNotEmpty()) create("distribution") {
            storeFile = rootProject.file(distributionProperties.getProperty("storeFile"))
            storePassword = distributionProperties.getProperty("storePassword")
            keyAlias = distributionProperties.getProperty("keyAlias")
            keyPassword = distributionProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("distribution")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ML Kit
    implementation(libs.mlkit.text.recognition)
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation(libs.mlkit.translate)

    implementation(libs.kotlinx.coroutines.play.services)

    // Networking
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
