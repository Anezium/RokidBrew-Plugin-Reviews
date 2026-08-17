package dev.havoc.taxihud.phone.state;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RideWakePolicyTest {
    @Test
    public void wakesForNewVisibleGeneration() {
        assertTrue(RideWakePolicy.shouldWake(RideSnapshot.empty(), snapshot(1L, false, true, false)));
        assertTrue(RideWakePolicy.shouldWake(
                snapshot(1L, false, true, false), snapshot(2L, false, true, false)));
    }

    @Test
    public void wakesForFirstWaitingTransitionInSameGeneration() {
        assertTrue(RideWakePolicy.shouldWake(
                snapshot(1L, false, true, false), snapshot(1L, true, true, false)));
    }

    @Test
    public void doesNotWakeForEtaOrRepeatedWaitingUpdate() {
        assertFalse(RideWakePolicy.shouldWake(
                snapshot(1L, false, true, false), snapshot(1L, false, true, false)));
        assertFalse(RideWakePolicy.shouldWake(
                snapshot(1L, true, true, false), snapshot(1L, true, true, false)));
    }

    @Test
    public void doesNotWakeWhenDismissedGenerationReappears() {
        assertFalse(RideWakePolicy.shouldWake(
                snapshot(1L, false, false, true), snapshot(1L, false, true, false)));
    }

    private static RideSnapshot snapshot(
            long generation, boolean waiting, boolean visible, boolean dismissed) {
        return new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", waiting ? "" : "3",
                waiting, waiting ? "2" : "", generation, generation,
                visible, dismissed, dismissed ? 1_000L : 0L, false, 0L);
    }
}
