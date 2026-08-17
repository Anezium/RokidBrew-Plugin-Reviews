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
        versionCode = 9
        versionName = "1.0.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
