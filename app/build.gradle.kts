plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.deepseek.harness"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deepseek.harness"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/hrs-release.jks")
            storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as? String) ?: ""
            keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as? String) ?: "hrs"
            keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as? String) ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("tar.gz", "gz")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
