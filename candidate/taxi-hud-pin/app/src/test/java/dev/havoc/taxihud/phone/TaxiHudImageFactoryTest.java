package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.content.Context;

import java.util.List;

import dev.havoc.taxihud.phone.state.RideSnapshot;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class TaxiHudImageFactoryTest {
    @Test
    public void activeRideProducesNexusSizedBitmapWithBottomActionRegion() {
        RideSnapshot snapshot = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "3",
                false, "", 4L, 9L,
                true, false, 0L, false, 0L);

        Context context = RuntimeEnvironment.getApplication();
        Bitmap decoded = TaxiHudImageFactory.renderBitmap(context, snapshot, List.of(
                new RideHistoryEntry(3L, "А 111 АА ⁷⁷⁷", "Geely", "", 3_000L),
                new RideHistoryEntry(2L, "В 222 ВВ ⁷⁷⁷", "Haval", "", 2_000L)), 1_000L);

        assertEquals(TaxiHudImageFactory.WIDTH, decoded.getWidth());
        assertEquals(TaxiHudImageFactory.HEIGHT, decoded.getHeight());
        assertTrue(TaxiHudImageFactory.BUTTON_LEFT > 0);
        assertTrue(TaxiHudImageFactory.BUTTON_TOP > TaxiHudImageFactory.HEIGHT * 3 / 4);
        assertTrue(TaxiHudImageFactory.BUTTON_RIGHT < TaxiHudImageFactory.WIDTH);
        assertTrue(TaxiHudImageFactory.BUTTON_BOTTOM < TaxiHudImageFactory.HEIGHT);
        decoded.recycle();
    }

    @Test
    public void inactiveHistoryUsesTheSameNexusImageCanvas() {
        Context context = RuntimeEnvironment.getApplication();
        Bitmap decoded = TaxiHudImageFactory.renderEmptyBitmap(context, List.of(
                new RideHistoryEntry(1L, "А 111 АА ⁷⁷⁷", "Demo Car", "", 1_000L),
                new RideHistoryEntry(2L, "В 222 ВВ ⁷⁷⁷", "Geely", "", 2_000L),
                new RideHistoryEntry(3L, "Е 333 ЕЕ ⁷⁷⁷", "Haval", "", 3_000L),
                new RideHistoryEntry(4L, "К 444 КК ⁷⁷⁷", "Toyota", "", 4_000L),
                new RideHistoryEntry(5L, "М 555 ММ ⁷⁷⁷", "Chery", "", 5_000L)));

        assertEquals(TaxiHudImageFactory.WIDTH, decoded.getWidth());
        assertEquals(TaxiHudImageFactory.HEIGHT, decoded.getHeight());
        decoded.recycle();
    }
}
