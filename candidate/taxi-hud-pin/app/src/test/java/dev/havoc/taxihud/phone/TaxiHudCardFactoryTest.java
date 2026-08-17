package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;

import com.anezium.rokidbus.client.plugin.NexusCard;
import dev.havoc.taxihud.phone.state.RideSnapshot;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class TaxiHudCardFactoryTest {
    @Test
    public void activeNotificationKeepsRideAndHistoryWithDedicatedClearAction() {
        RideSnapshot snapshot = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "3",
                false, "", 4L, 9L,
                true, false, 0L, false, 0L);

        NexusCard card = TaxiHudCardFactory.ride(context("en"), snapshot, List.of(
                new RideHistoryEntry(3L, "А 111 АА ⁷⁷⁷", "Geely", "", 3_000L)), 1_000L);

        assertEquals("Taxi Plate", card.getTitle());
        assertEquals(List.of(
                "А 111 АА ⁷⁷⁷",
                "Синий Demo Car",
                "Arrives in: 3 min",
                "Recent rides"), card.getLines().subList(0, 4));
        assertTrue(card.getLines().get(4).matches(
                "\\d{2}\\.\\d{2} \\d{2}:\\d{2} · А 111 АА ⁷⁷⁷ · Geely"));
        assertEquals("tap · clear", card.getFooter());
        assertFalse(card.getHandlesBack());
    }

    @Test
    public void noNotificationKeepsHistoryAndHidesClearAction() {
        NexusCard card = TaxiHudCardFactory.empty(context("ru"), List.of(
                new RideHistoryEntry(3L, "А 111 АА ⁷⁷⁷", "Geely", "", 3_000L)));

        assertEquals("Taxi Plate", card.getTitle());
        assertEquals(List.of(
                "Нет активного уведомления",
                "Последние поездки"), card.getLines().subList(0, 2));
        assertTrue(card.getLines().get(2).matches(
                "\\d{2}\\.\\d{2} \\d{2}:\\d{2} · А 111 АА ⁷⁷⁷ · Geely"));
        assertEquals(null, card.getFooter());
        assertFalse(card.getHandlesBack());
    }

    private static Context context(String language) {
        Context base = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language));
        return base.createConfigurationContext(configuration);
    }
}
