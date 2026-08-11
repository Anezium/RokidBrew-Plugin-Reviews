plugins {
    id("com.android.application")
}

apply(from = rootProject.file("gradle/plugin-release-signing.gradle"))

android {
    namespace = "com.beyondlevi.nexus.lume"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beyondlevi.nexus.lume"
        // minSdk 30: the Nexus platform dropped to API 30 for Android 11 support.
        // A minSdk-31 APK cannot even be parsed on an API 30 phone (Store install
        // fails). Per plugins/AGENTS.md / docs/PLUGIN_SDK.md.
        minSdk = 30
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.11"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Rokid Nexus bus-client SDK (published via JitPack). `shared` resolves transitively.
    // Rokid Nexus bus-client SDK — latest published line.
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.14.0")
    // Library index persistence.
    implementation("com.google.code.gson:gson:2.11.0")
    // On-device PDF text extraction (no network; nothing leaves the phone).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation("junit:junit:4.13.2")
}

