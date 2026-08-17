package com.havoc.rokidbus.plugin.otps

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusPluginContractTest {
    @Test
    fun `manifest declares one headless api 3 plugin entry point`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse("android.intent.action.MAIN" in manifest)
        assertFalse("android.intent.category.LAUNCHER" in manifest)
        assertFalse("android.permission.INTERNET" in manifest)
        assertFalse("android.permission.POST_NOTIFICATIONS" in manifest)
        assertEquals(1, Regex("com\\.anezium\\.rokidbus\\.action\\.PLUGIN").findAll(manifest).count())
        assertTrue("android:value=\"otps\"" in manifest)
        assertTrue("android:value=\"3\"" in manifest)
        assertTrue("android:value=\"surfaces\"" in manifest)
        assertTrue("android:foregroundServiceType=\"specialUse\"" in manifest)
    }

    @Test
    fun `build uses the current public Nexus sdk`() {
        val build = File("build.gradle.kts").readText()
        assertTrue("bus-client:sdk-v0.15.0" in build)
    }
}

