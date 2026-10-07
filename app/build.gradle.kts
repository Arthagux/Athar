plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.athar.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.athar.app.android11"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.2"
    }
    signingConfigs {
        create("atharDev") {
            storeFile = file("${rootProject.projectDir}/keystore/athar-dev.jks")
            storePassword = "athardev2026"
            keyAlias = "athar"
            keyPassword = "athardev2026"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("atharDev")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation(project(":llama-lib"))
}
