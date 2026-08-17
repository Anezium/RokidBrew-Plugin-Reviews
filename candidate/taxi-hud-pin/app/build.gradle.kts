plugins {
    alias(libs.plugins.android.application)
}

apply(from = rootProject.file("gradle/release-signing.gradle"))

android {
    namespace = "com.havoc.rokid.plugin.taxihudpin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.havoc.rokid.plugin.taxihudpin"
        minSdk = 31
        targetSdk = 36
        versionCode = 14
        versionName = "0.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val packageNamedDebugApks = tasks.register<Copy>("packageNamedDebugApks") {
    dependsOn(":phone:assembleDebug")
    into(rootProject.layout.buildDirectory.dir("outputs/taxi-hud"))
    from(layout.buildDirectory.file("outputs/apk/debug/phone-debug.apk")) {
        rename { "Taxi-Plate-debug.apk" }
    }
}

val packageNamedReleaseApk = tasks.register<Copy>("packageNamedReleaseApk") {
    dependsOn(":phone:assembleRelease")
    into(rootProject.layout.buildDirectory.dir("outputs/taxi-hud"))
    from(layout.buildDirectory.file("outputs/apk/release/phone-release.apk")) {
        rename { "taxi-hud-pin-phone-release.apk" }
    }
}

rootProject.tasks.register("packageTaxiHudDebug") {
    group = "build"
    description = "Builds the single Taxi Plate Nexus plugin APK."
    dependsOn(packageNamedDebugApks)
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.gson)
    implementation(libs.rokid.nexus.bus.client)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

rootProject.tasks.register("packageTaxiHudRelease") {
    group = "build"
    description = "Builds the signed Taxi Plate Nexus Store release APK."
    dependsOn(packageNamedReleaseApk)
}
