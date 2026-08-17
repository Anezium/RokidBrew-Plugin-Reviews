package dev.havoc.taxihud.phone.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import dev.havoc.taxihud.phone.config.AdapterRepository;
import dev.havoc.taxihud.phone.NotificationTiming;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public final class NotificationAdapterEngineTest {
    private AdapterRepository repository;
    private NotificationAdapterEngine engine;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        repository = new AdapterRepository(context);
        repository.resetImported();
        engine = new NotificationAdapterEngine();
    }

    @Test public void builtInDefaultParsesExistingProviderNotification() {
        AdapterParseResult result = engine.parse("ru.yandex.taxi", "Yandex Go",
                "К вам едет Синий Demo Car А111АА777, приедет через 3 мин",
                "", Collections.emptyList(), repository.enabledAdapters());
        assertTrue(result.matched());
        assertEquals("yandex-go", result.adapter.id);
        assertEquals("А111АА777", result.update.plate);
        assertEquals("3", result.update.arrivalMinutes);
        assertEquals("Yandex Go", result.update.sourceDisplayName);
    }

    @Test public void unrelatedPackageIsIgnored() {
        assertFalse(engine.parse("com.example.other", "Taxi", "А111АА777",
                "", Collections.emptyList(), repository.enabledAdapters()).matched());
    }

    @Test public void importedLocalAdapterParsesWithoutCodeChanges() {
        repository.importJson(customBundle("local-cab", "com.example.localcab"));
        AdapterParseResult result = engine.parse("com.example.localcab", "Driver found",
                "CAR=Blue Bolt PLATE=A123BC77 ETA=6", "", Collections.emptyList(),
                repository.enabledAdapters());
        assertTrue(result.matched());
        assertEquals("local-cab", result.adapter.id);
        assertEquals("А123ВС77", result.update.plate);
        assertEquals("Blue Bolt", result.update.makeModel);
        assertEquals("6", result.update.arrivalMinutes);
        assertEquals(600000L, result.update.sourcePinTtlMs);
    }

    @Test public void yandexCountdownMetadataBecomesTripProgress() {
        AdapterParseResult result = engine.parse(
                "ru.yandex.taxi", "", "", "", Collections.emptyList(),
                repository.enabledAdapters(), 1_000L,
                new NotificationTiming(31L * 60_000L, true, true, 0, 0, false));

        assertTrue(result.matched());
        assertEquals(TaxiUpdate.Kind.TRIP_PROGRESS, result.update.kind);
        assertEquals(31L * 60_000L, result.update.tripEndsAtEpochMs);
    }

    @Test public void localizedTripDurationTextBecomesDeadline() {
        AdapterParseResult result = engine.parse(
                "ru.yandex.taxi", "До прибытия 1 ч. 5 мин.", "", "",
                Collections.emptyList(), repository.enabledAdapters(),
                10_000L, NotificationTiming.NONE);

        assertTrue(result.matched());
        assertEquals(TaxiUpdate.Kind.TRIP_PROGRESS, result.update.kind);
        assertEquals(10_000L + 65L * 60_000L, result.update.tripEndsAtEpochMs);
    }

    @Test(timeout = 2_000L)
    public void hostileImportedRegexCannotBlockNotificationParsing() {
        String hostile = customBundle("hostile-cab", "com.example.hostile")
                .replace("PLATE=([A-Z0-9]+)", "(a+)+$");
        repository.importJson(hostile);

        AdapterParseResult result = engine.parse(
                "com.example.hostile",
                "a".repeat(NotificationAdapterEngine.MAX_MATCH_BODY_CHARS - 1) + "!"
                        + "a".repeat(200_000),
                "",
                "",
                Collections.emptyList(),
                repository.enabledAdapters());

        assertFalse(result.matched());
    }

    @Test public void importedRegexRejectsUnsupportedBacktrackingFeatures() {
        String lookbehind = customBundle("unsafe-cab", "com.example.unsafe")
                .replace("PLATE=([A-Z0-9]+)", "(?<=PLATE=)([A-Z0-9]+)");
        try {
            repository.importJson(lookbehind);
            throw new AssertionError("Expected imported lookbehind to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unsafe or unsupported"));
        }

        assertTrue(repository.handlesPackage("ru.yandex.go"));
    }

    public static String customBundle(String id, String packageName) {
        return "{\"schemaVersion\":1,\"metadata\":{\"id\":\"custom.bundle\","
                + "\"displayName\":\"Custom\",\"author\":\"test\",\"version\":1},"
                + "\"adapters\":[{\"id\":\"" + id + "\",\"displayName\":\"Local Cab\","
                + "\"enabled\":true,\"packages\":[\"" + packageName + "\"],"
                + "\"eventRules\":[],\"fieldRules\":["
                + "{\"field\":\"PLATE\",\"pattern\":\"PLATE=([A-Z0-9]+)\",\"group\":1,"
                + "\"ignoreCase\":true,\"transforms\":[\"UPPERCASE\",\"NORMALIZE_PLATE_LETTERS\"]},"
                + "{\"field\":\"VEHICLE\",\"pattern\":\"CAR=([A-Za-z ]+)\\\\s+PLATE\","
                + "\"group\":1,\"ignoreCase\":true,\"transforms\":[\"TRIM\"]},"
                + "{\"field\":\"ARRIVAL_MINUTES\",\"pattern\":\"ETA=(\\\\d+)\","
                + "\"group\":1,\"ignoreCase\":true,\"transforms\":[\"TRIM\"]}],"
                + "\"truncateBeforePatterns\":[],\"activeWhenAny\":[\"PLATE\"],"
                + "\"requiredWithPlate\":[\"VEHICLE\"],\"pinTtlMs\":600000}]}";
    }
}
