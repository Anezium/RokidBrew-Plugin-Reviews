buildscript {
    dependencies {
        // Required when AGP 9 built-in Kotlin is used with Kotlin compiler plugins.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Copy>("packageDebugApk") {
    dependsOn(":phone:assembleDebug")
    into(layout.buildDirectory.dir("outputs"))
    from(project(":phone").layout.buildDirectory.file("outputs/apk/debug/phone-debug.apk")) {
        rename { "Home-Assistant-for-Rokid-Nexus-0.2.1-debug.apk" }
    }
}

tasks.register<Copy>("packageReleaseApk") {
    dependsOn(":phone:assembleRelease")
    into(layout.buildDirectory.dir("outputs"))
    from(project(":phone").layout.buildDirectory.file("outputs/apk/release/phone-release.apk")) {
        rename { "home-assistant-phone-release.apk" }
    }
}
