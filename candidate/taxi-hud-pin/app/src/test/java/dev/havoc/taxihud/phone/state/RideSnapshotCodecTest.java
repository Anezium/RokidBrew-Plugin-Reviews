package dev.havoc.taxihud.phone.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RideSnapshotCodecTest {
    private final RideSnapshotCodec codec = new RideSnapshotCodec();

    @Test
    public void roundTripPreservesDismissalAndCountdownState() {
        RideSnapshot input = new RideSnapshot(
                "А111АА777",
                "Синий",
                "Demo Car",
                "3",
                true,
                "5",
                7L,
                11L,
                false,
                true,
                1_234_567L,
                false,
                1_800_000L,
                "local-cab", "Local Cab", "com.example.localcab", 600_000L);

        RideSnapshot output = codec.decode(codec.encode(input));

        assertEquals(input, output);
        assertEquals(1_234_567L, output.dismissedAtEpochMs);
        assertEquals(1_800_000L, output.countdownEndsAtEpochMs);
        assertEquals("local-cab", output.sourceAdapterId);
        assertEquals("Local Cab", output.sourceDisplayName);
        assertEquals(600_000L, output.pinTtlMs);
    }

    @Test
    public void blankAndMalformedJsonReturnEmptySnapshot() {
        assertEquals(RideSnapshot.empty(), codec.decode(""));
        assertEquals(RideSnapshot.empty(), codec.decode("  \n "));
        assertEquals(RideSnapshot.empty(), codec.decode("{not-json"));
    }

    @Test
    public void tripProgressSurvivesProcessRestart() {
        RideSnapshot input = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "", false, "",
                7L, 12L, true, false, 0L, false, 0L,
                "yandex-go", "Yandex Go", "ru.yandex.taxi", 1_800_000L,
                true, 9_000_000L);

        RideSnapshot output = codec.decode(codec.encode(input));

        assertTrue(output.tripInProgress);
        assertEquals(9_000_000L, output.tripEndsAtEpochMs);
        assertEquals(input, output);
    }
}
