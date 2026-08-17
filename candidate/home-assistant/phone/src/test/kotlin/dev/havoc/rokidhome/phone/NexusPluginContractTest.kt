package dev.havoc.rokidhome.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NexusPluginContractTest {
    @Test
    fun `manifest is one headless api 3 surfaces plugin`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse("android.intent.action.MAIN" in manifest)
        assertFalse("android.intent.category.LAUNCHER" in manifest)
        assertFalse("POST_NOTIFICATIONS" in manifest)
        assertFalse("com.rokid" in manifest)
        assertEquals(1, Regex("<service\\b").findAll(manifest).count())
        assertTrue("com.anezium.rokidbus.action.PLUGIN" in manifest)
        assertTrue("com.anezium.rokidbus.plugin.API_VERSION" in manifest)
        assertTrue("android:value=\"3\"" in manifest)
        assertTrue("com.anezium.rokidbus.plugin.CAPABILITIES" in manifest)
        assertTrue("android:value=\"surfaces\"" in manifest)
        assertTrue("com.anezium.rokidbus.plugin.SETTINGS_ACTIVITY" in manifest)
        assertTrue("android:foregroundServiceType=\"specialUse\"" in manifest)
        assertTrue("android:value=\"home-assistant\"" in manifest)
        assertTrue("com.anezium.rokidbus.plugin.ICON_DRAWABLE" in manifest)
        assertTrue("com.anezium.rokidbus.plugin.GLYPHS" in manifest)
    }

    @Test
    fun `home assistant glyph follows Nexus art and wire contracts`() {
        val drawable = File("src/main/res/drawable/nexus_glyph_home_assistant.xml").readText()
        val glyphs = File("src/main/res/values/nexus_glyphs.xml").readText()

        assertTrue("android:width=\"24dp\"" in drawable)
        assertTrue("android:height=\"24dp\"" in drawable)
        assertTrue("android:viewportWidth=\"24\"" in drawable)
        assertTrue("android:viewportHeight=\"24\"" in drawable)
        assertTrue("android:strokeColor=\"#FF4DFF8C\"" in drawable)
        assertTrue("android:strokeWidth=\"1.7\"" in drawable)
        assertTrue("home-assistant|M2.5,12 L12,2.5 L21.5,12" in glyphs)
    }

    @Test
    fun `build consumes fresh Nexus sdk and has no standalone glasses module`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val settings = File("../settings.gradle.kts").readText()
        val build = File("build.gradle.kts").readText()

        assertTrue("sdk-v0.15.0" in catalog)
        assertTrue("com.github.Anezium.Rokid-Nexus:bus-client" in catalog)
        assertTrue("https://jitpack.io" in settings)
        assertFalse("include(\":glasses\")" in settings)
        assertFalse("client-l" in catalog)
        assertFalse("copyGlassesClient" in build)
    }

    @Test
    fun `settings ui follows canonical Nexus sample components`() {
        val source = File("src/main/kotlin/dev/havoc/rokidhome/phone/MainActivity.kt").readText()

        assertTrue("NexusUi.pluginHeader" in source)
        assertTrue("NexusUi.contentColumn" in source)
        assertTrue("NexusUi.navCard" in source)
        assertTrue("NexusUi.pillButton" in source)
        assertTrue("NexusUi.uninstallCard" in source)
        assertTrue("Unofficial community plugin" in source)
        assertFalse("androidx.compose" in source)
    }
}
