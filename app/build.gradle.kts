import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.xarena.nativeapp"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.xarena.esports.native"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "0.9.0-native"
    }
    val keyInfo = rootProject.file("keystore/KEY_INFO.txt")
    if (keyInfo.exists()) {
        val props = Properties().apply { keyInfo.inputStream().use { load(it) } }
        signingConfigs {
            create("native") {
                storeFile = rootProject.file("keystore/" + props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            // Test builds are signed with the vaulted native key so in-place updates keep working
            // even when this build sandbox is rebuilt (a fresh debug key would break updates).
            if (signingConfigs.findByName("native") != null) signingConfig = signingConfigs.getByName("native")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.findByName("native") != null) signingConfig = signingConfigs.getByName("native")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/*.version") }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
