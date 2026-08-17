package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.List;

import dev.havoc.taxihud.phone.state.RideSnapshot;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TaxiHudImageFactoryInstrumentedTest {
    @Test
    public void encodedFrameFitsAndDecodesOnAndroid() {
        RideSnapshot snapshot = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "3",
                false, "", 4L, 9L,
                true, false, 0L, false, 0L);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TaxiHudImageFrame frame = TaxiHudImageFactory.ride(context, snapshot, List.of(
                new RideHistoryEntry(3L, "А 111 АА ⁷⁷⁷", "Geely", "", 3_000L),
                new RideHistoryEntry(2L, "В 222 ВВ ⁷⁷⁷", "Haval", "", 2_000L)), 1_000L);

        assertTrue(frame.bytes.length > 0);
        assertTrue(frame.bytes.length <= 65_536);
        assertTrue(frame.image.getMimeType().equals("image/png")
                || frame.image.getMimeType().equals("image/jpeg"));

        Bitmap decoded = BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.length);
        assertNotNull(decoded);
        assertEquals(TaxiHudImageFactory.WIDTH, decoded.getWidth());
        assertEquals(TaxiHudImageFactory.HEIGHT, decoded.getHeight());
        decoded.recycle();

        TaxiHudImageFrame empty = TaxiHudImageFactory.empty(context, List.of(
                new RideHistoryEntry(4L, "Е 333 ЕЕ ⁷⁷⁷", "Haval", "", 4_000L)),
                5_000L);
        Bitmap emptyDecoded = BitmapFactory.decodeByteArray(
                empty.bytes, 0, empty.bytes.length);
        assertNotNull(emptyDecoded);
        assertEquals(TaxiHudImageFactory.WIDTH, emptyDecoded.getWidth());
        assertEquals(TaxiHudImageFactory.HEIGHT, emptyDecoded.getHeight());
        emptyDecoded.recycle();
    }
}
