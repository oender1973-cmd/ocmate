plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.onder.ocmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.onder.ocmate"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Fester Schlüssel, damit spätere Versionen sich über die alte App installieren lassen.
    signingConfigs {
        create("release") {
            storeFile = file("ocmate.keystore")
            storePassword = "ocmate-tv"
            keyAlias = "ocmate"
            keyPassword = "ocmate-tv"
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
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
