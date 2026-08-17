plugins {
    id("com.android.application") version "9.2.1" apply false
}

tasks.register<Copy>("packageDebugApk") {
    dependsOn(":app:assembleDebug")
    into(layout.buildDirectory.dir("outputs"))
    from(project(":app").layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")) {
        rename { "otps-phone-debug.apk" }
    }
}

tasks.register<Copy>("packageReleaseApk") {
    dependsOn(":app:assembleRelease")
    into(layout.buildDirectory.dir("outputs"))
    from(project(":app").layout.buildDirectory.file("outputs/apk/release/app-release.apk")) {
        rename { "otps-phone-release.apk" }
    }
}

