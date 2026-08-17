plugins {
    id("com.android.application")
}

apply(from = rootProject.file("gradle/release-signing.gradle"))

android {
    namespace = "com.havoc.rokidbus.plugin.otps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.havoc.rokidbus.plugin.otps"
        minSdk = 30
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.15.0")
    testImplementation("junit:junit:4.13.2")
}
