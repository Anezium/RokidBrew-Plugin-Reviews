plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

apply(from = rootProject.file("gradle/plugin-release-signing.gradle"))

android {
    namespace = "com.beyondlevi.nexus.plugin.agenda"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beyondlevi.nexus.plugin.agenda"
        // minSdk 30: the Nexus platform supports Android 11; a minSdk-31 APK
        // cannot be parsed on an API 30 phone and the Store install fails there.
        minSdk = 30
        targetSdk = 36
        versionCode = 14
        versionName = "1.1.1"
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
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.15.0")
    testImplementation("junit:junit:4.13.2")
}
