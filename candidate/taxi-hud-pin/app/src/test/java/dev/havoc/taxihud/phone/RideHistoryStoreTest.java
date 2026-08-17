package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.TimeZone;

import dev.havoc.taxihud.phone.state.RideSnapshot;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class RideHistoryStoreTest {
    private RideHistoryStore store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        store = new RideHistoryStore(context);
        store.clear();
    }

    @Test
    public void keepsFiveMostRecentDistinctSessions() {
        store.record(ride("А111АА777", "Demo Car", 1L, "5"), 1_000L);
        store.record(ride("В222ВВ777", "Geely Coolray", 2L, "4"), 2_000L);
        store.record(ride("Е333ЕЕ777", "Haval Jolion", 3L, "3"), 3_000L);
        store.record(ride("К444КК777", "Toyota Camry", 4L, "3"), 4_000L);
        store.record(ride("М555ММ777", "Chery Tiggo", 5L, "2"), 5_000L);
        store.record(ride("Н666НН777", "Skoda Rapid", 6L, "1"), 6_000L);

        List<RideHistoryEntry> recent = store.recent(10);

        assertEquals(5, recent.size());
        assertEquals("Н 666 НН ⁷⁷⁷", recent.get(0).plateLine);
        assertEquals("В 222 ВВ ⁷⁷⁷", recent.get(4).plateLine);
    }

    @Test
    public void latestUpdateReplacesSameSessionInsteadOfAddingHistoryRow() {
        store.record(ride("А111АА777", "Demo Car", 1L, "5"), 1_000L);
        store.record(ride("А111АА777", "Demo Car", 1L, "2"), 2_000L);

        List<RideHistoryEntry> entries = store.recent(2);

        assertEquals(1, entries.size());
        assertEquals("Приедет: 2 мин", entries.get(0).statusLine);
        assertEquals(1_000L, entries.get(0).startedAtEpochMs);
        assertEquals(2_000L, entries.get(0).updatedAtEpochMs);
    }

    @Test
    public void provisionalUnknownPlateIsNotAddedToHistory() {
        store.record(ride("###", "Новая машина", 1L, "5"), 1_000L);

        assertEquals(0, store.recent(2).size());
    }

    @Test
    public void displayLinePlacesStartDateAndTimeBeforePlate() {
        RideHistoryEntry entry = new RideHistoryEntry(
                1L,
                "А 111 АА ⁷⁷⁷",
                "Demo Car",
                "",
                "",
                3_600_000L,
                7_200_000L);

        assertEquals(
                "01.01 01:00 · А 111 АА ⁷⁷⁷ · Demo Car",
                entry.displayLine(TimeZone.getTimeZone("UTC")));
    }

    @Test
    public void legacyEntryUsesItsOldUpdateTimeAsBestAvailableStart() {
        RideHistoryEntry legacy = new RideHistoryEntry(
                1L, "А 111 АА ⁷⁷⁷", "Demo Car", "", "", 0L, 4_000L);

        assertEquals(4_000L, legacy.effectiveStartedAtEpochMs());
    }

    private static RideSnapshot ride(
            String plate, String model, long generation, String arrivalMinutes) {
        return new RideSnapshot(
                plate, "Синий", model, arrivalMinutes,
                false, "", generation, 1L,
                true, false, 0L, false, 0L);
    }
}
