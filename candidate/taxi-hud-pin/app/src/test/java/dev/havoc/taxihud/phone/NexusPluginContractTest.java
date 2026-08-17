package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NexusPluginContractTest {
    @Test
    public void manifestIsAHeadlessNexusSurfacePlugin() throws Exception {
        String manifest = read(Path.of("src", "main", "AndroidManifest.xml"));

        assertTrue(manifest.contains("com.anezium.rokidbus.action.PLUGIN"));
        assertTrue(manifest.contains("com.anezium.rokidbus.plugin.API_VERSION"));
        assertTrue(manifest.contains("android:value=\"3\""));
        assertTrue(manifest.contains("android:value=\"surfaces\""));
        assertTrue(manifest.contains("taxi-hud-pin"));
        assertTrue(manifest.contains("android:value=\"Taxi Plate\""));
        assertTrue(manifest.contains("android:value=\"taxi-plate\""));
        assertTrue(manifest.contains("com.anezium.rokidbus.plugin.GLYPHS"));
        assertTrue(manifest.contains("dev.havoc.taxihud.phone.TaxiHudPluginService"));
        assertFalse(manifest.contains("android.intent.category.LAUNCHER"));
        assertFalse(manifest.contains("android.permission.BLUETOOTH"));
        assertFalse(manifest.contains("com.rokid.cxr"));
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"));
        assertTrue(manifest.contains("dev.havoc.taxihud.phone.NotificationActionReceiver"));
    }

    @Test
    public void settingsScreenUsesCanonicalNexusUiAndUninstallCard() throws Exception {
        String activity = read(Path.of(
                "src", "main", "java", "dev", "havoc", "taxihud", "phone",
                "MainActivity.java"));

        assertTrue(activity.contains("NexusUi.INSTANCE.fixedRoot"));
        assertTrue(activity.contains("NexusUi.INSTANCE.pluginHeader"));
        assertTrue(activity.contains("NexusUi.INSTANCE.contentColumn"));
        assertTrue(activity.contains("NexusUi.INSTANCE.uninstallCard"));
        assertTrue(activity.contains("Intent.ACTION_DELETE"));
    }

    @Test
    public void taxiPlateGlyphFollowsNexusArtContract() throws Exception {
        String glyph = read(Path.of(
                "src", "main", "res", "drawable", "nexus_glyph_taxi_plate.xml"));
        String glyphs = read(Path.of(
                "src", "main", "res", "values", "nexus_glyphs.xml"));

        assertTrue(glyph.contains("android:width=\"24dp\""));
        assertTrue(glyph.contains("android:height=\"24dp\""));
        assertTrue(glyph.contains("android:viewportWidth=\"24\""));
        assertTrue(glyph.contains("android:viewportHeight=\"24\""));
        assertTrue(glyph.contains("#FF4DFF8C"));
        assertTrue(glyph.contains("android:fillColor"));
        assertTrue(glyph.contains("M2,8H6V12H2Z"));
        assertTrue(glyph.contains("M14,12H18V16H14Z"));
        assertFalse(glyph.contains("android:strokeColor"));
        assertTrue(glyphs.contains(
                "taxi-plate|M2,8H6V12H2Z M10,8H14V12H10Z M18,8H22V12H18Z " +
                        "M6,12H10V16H6Z M14,12H18V16H14Z"));
    }

    @Test
    public void buildContainsOnlyThePhonePluginModule() throws Exception {
        String settings = read(Path.of("..", "settings.gradle.kts"));

        assertTrue(settings.contains("include(\":phone\")"));
        assertFalse(settings.contains("include(\":glasses\")"));
        assertFalse(settings.contains("include(\":shared\")"));
    }

    @Test
    public void notificationUpdatesUseFireAndForgetPinClient() throws Exception {
        String transport = read(Path.of(
                "src", "main", "java", "dev", "havoc", "taxihud", "phone",
                "NexusTaxiHudTransport.java"));

        assertTrue(transport.contains("NexusPluginClient.Companion.create"));
        assertTrue(transport.contains("client.showPin"));
        assertTrue(transport.contains("client.hidePin"));
        assertTrue(transport.contains("client.close"));
        assertTrue(transport.contains("renderCurrentRideIfOpen"));
        assertTrue(transport.contains("TaxiHudCardFactory.ride(context, snapshot"));
        assertTrue(transport.contains("SurfaceModelsKt.surfaceSession"));
        assertTrue(transport.contains("wakeSurface.showCard"));
        assertTrue(transport.contains("wakeSurface.hide"));
    }

    @Test
    public void productionCoordinatorPublishesAndroidNotification() throws Exception {
        String coordinator = read(Path.of(
                "src", "main", "java", "dev", "havoc", "taxihud", "phone",
                "TaxiCoordinator.java"));

        assertTrue(coordinator.contains("new TaxiHudNotificationPublisher(applicationContext)"));
        assertFalse(coordinator.contains("TaxiHudNotificationPublisher.disabled(applicationContext)"));
    }

    @Test
    public void androidIdentityDoesNotReplaceTheExistingTaxiHud() throws Exception {
        String build = read(Path.of("build.gradle.kts"));
        String manifest = read(Path.of("src", "main", "AndroidManifest.xml"));

        assertTrue(build.contains(
                "applicationId = \"com.havoc.rokid.plugin.taxihudpin\""));
        assertFalse(build.contains("applicationId = \"dev.havoc.taxihud.phone\""));
        assertTrue(manifest.contains("android:value=\"taxi-hud-pin\""));
    }

    @Test
    public void applicationPublishesEnglishAndRussianLocales() throws Exception {
        String manifest = read(Path.of("src", "main", "AndroidManifest.xml"));
        String locales = read(Path.of("src", "main", "res", "xml", "locales_config.xml"));
        String english = read(Path.of("src", "main", "res", "values", "strings.xml"));
        String russian = read(Path.of("src", "main", "res", "values-ru", "strings.xml"));

        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""));
        assertTrue(locales.contains("android:name=\"en\""));
        assertTrue(locales.contains("android:name=\"ru\""));
        assertTrue(english.contains("<string name=\"language_title\">Language</string>"));
        assertTrue(russian.contains("<string name=\"language_title\">Язык</string>"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
