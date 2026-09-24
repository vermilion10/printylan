// CI passes these with -PappVersionName and -PappVersionCode. Local builds use the defaults.
val appVersionName = providers.gradleProperty("appVersionName").getOrElse("0.1.0")
val appVersionCode = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)

// Release signing comes from environment variables so the keystore never enters the repo.
val releaseKeystore = providers.environmentVariable("PRINTYLAN_KEYSTORE_FILE").orNull

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.printylan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.printylan"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("PRINTYLAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PRINTYLAN_KEY_ALIAS")
                keyPassword = System.getenv("PRINTYLAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without a release keystore, sign with the debug key so the APK still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        compose = true
    }
}

dependencies {
    implementation(project(":core:driver"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.print)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
