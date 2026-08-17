package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;

import com.anezium.rokidbus.client.plugin.NexusPin;
import com.anezium.rokidbus.client.plugin.NexusPinEmphasis;
import com.anezium.rokidbus.client.plugin.NexusPinPosition;
import com.anezium.rokidbus.client.plugin.NexusPinSize;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Locale;

import dev.havoc.taxihud.phone.state.RideSnapshot;

@RunWith(RobolectricTestRunner.class)
public final class TaxiHudPinFactoryTest {
    @Test
    public void activeRideMapsToMediumTopRightPin() {
        RideSnapshot snapshot = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "3",
                false, "", 4L, 9L,
                true, false, 0L, false, 0L,
                "local-cab", "Local Cab", "com.example.localcab", 600_000L);

        NexusPin pin = TaxiHudPinFactory.ride(context("en"), snapshot, 1_000L);

        assertEquals("А 111 АА ⁷⁷⁷", pin.getTitle());
        assertEquals(NexusPinSize.MEDIUM, pin.getSize());
        assertEquals(NexusPinPosition.TOP_RIGHT, pin.getPosition());
        assertEquals(Long.valueOf(snapshot.pinTtlMs), pin.getTtlMs());
        assertEquals("Синий Demo Car", pin.getRichLines().get(0).getText());
        assertEquals("Arrives in: 3 min", pin.getRichLines().get(1).getText());
        assertEquals(NexusPinEmphasis.BRIGHT,
                pin.getRichLines().get(1).getEmphasis());
        assertEquals(Long.valueOf(600_000L), pin.getTtlMs());
        assertEquals("Local Cab", pin.getRichLines().get(2).getText());
        assertEquals(NexusPinEmphasis.DIM, pin.getRichLines().get(2).getEmphasis());
    }

    @Test
    public void countdownAndLongVehicleStayInsidePinCaps() {
        RideSnapshot snapshot = new RideSnapshot(
                "А111АА777", "Очень длинный перламутрово-белый", "Автомобиль модели K5", "",
                false, "", 1L, 2L,
                true, false, 0L, false, 8_000L);

        NexusPin pin = TaxiHudPinFactory.ride(context("ru"), snapshot, 1_000L);

        assertEquals("Поездка началась · 7 с", pin.getRichLines().get(1).getText());
        assertTrue(pin.getRichLines().get(0).getText().length() <= 32);
        assertTrue(pin.getTitle().length() <= 28);
    }

    @Test
    public void tripUsesTinyLocalizedArrivalPin() {
        RideSnapshot snapshot = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "",
                false, "", 4L, 10L,
                true, false, 0L, false, 0L,
                "yandex-go", "Yandex Go", "ru.yandex.taxi", 600_000L,
                true, 3_901_000L);

        NexusPin pin = TaxiHudPinFactory.ride(context("ru"), snapshot, 1_000L);

        assertEquals("До прибытия", pin.getTitle());
        assertEquals(NexusPinSize.SMALL, pin.getSize());
        assertEquals(1, pin.getRichLines().size());
        assertEquals("1 ч. 5 мин.", pin.getRichLines().get(0).getText());
        assertEquals(NexusPinEmphasis.BRIGHT,
                pin.getRichLines().get(0).getEmphasis());
    }

    private static Context context(String language) {
        Context base = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language));
        return base.createConfigurationContext(configuration);
    }
}
