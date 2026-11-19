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
        versionCode = 14
        versionName = "3.1"
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
    // Media3 kennzeichnet viele Player-Schnittstellen als „unstable“; das ist nur ein Lint-Hinweis
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
    // Echter Player für Live-TV, Filme und Serien (ExoPlayer)
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-extractor:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
}
