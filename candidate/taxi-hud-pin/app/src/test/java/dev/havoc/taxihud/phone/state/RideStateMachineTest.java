package dev.havoc.taxihud.phone.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import dev.havoc.taxihud.phone.parse.TaxiUpdate;
import org.junit.Test;

public final class RideStateMachineTest {
    private final RideStateMachine machine = new RideStateMachine();

    @Test
    public void dismissalSuppressesSameSessionForTwoHours() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideTransition dismissed = machine.onManualDismiss(shown, 2_000L);
        RideTransition early = machine.onTaxiUpdate(dismissed.snapshot,
                active("А111АА777", "Синий", "Demo Car", "2"), 2_000L + 7_199_999L);
        RideTransition expired = machine.onTaxiUpdate(dismissed.snapshot,
                active("А111АА777", "Синий", "Demo Car", "2"), 2_000L + 7_200_000L);

        assertEquals(RideTransition.Command.HIDE, dismissed.command);
        assertEquals(RideTransition.Command.NONE, early.command);
        assertEquals("2", early.snapshot.arrivalMinutes);
        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, expired.command);
        assertFalse(expired.snapshot.dismissed);
        assertEquals("2", expired.snapshot.arrivalMinutes);
    }

    @Test
    public void syntheticTestStartsNewGenerationAfterManualDismiss() {
        RideSnapshot first = machine.onTaxiUpdate(
                RideSnapshot.empty(),
                TaxiUpdate.syntheticTest("А111АА777", "Синий", "Demo Car", "3"),
                1_000L).snapshot;
        RideSnapshot dismissed = machine.onManualDismiss(first, 2_000L).snapshot;

        RideTransition repeated = machine.onTaxiUpdate(
                dismissed,
                TaxiUpdate.syntheticTest("А111АА777", "Синий", "Demo Car", "3"),
                3_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, repeated.command);
        assertTrue(repeated.snapshot.visible);
        assertFalse(repeated.snapshot.dismissed);
        assertEquals(first.sessionGeneration + 1L, repeated.snapshot.sessionGeneration);
        assertEquals(0L, repeated.snapshot.countdownEndsAtEpochMs);
    }

    @Test
    public void waitingKeepsKnownVehicleAndChangesThirdLine() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideTransition waiting = machine.onTaxiUpdate(shown, TaxiUpdate.waiting("5"), 2_000L);

        assertEquals("Синий Demo Car", waiting.snapshot.vehicleLine());
        assertEquals("Ожидает: 5 мин", waiting.snapshot.statusLine());
        assertEquals("А 111 АА ⁷⁷⁷", waiting.snapshot.plateLine());
    }

    @Test
    public void reassignmentSearchHidesOldVehicleAndTitleOnlyArrivalShowsNewPlaceholder() {
        RideSnapshot oldRide = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("В222ВВ777", "Зелёный", "Sample Car", "3"), 1_000L).snapshot;

        RideTransition searching = machine.onTaxiUpdate(
                oldRide, TaxiUpdate.lifecycle(TaxiUpdate.Kind.SEARCHING), 2_000L);
        RideTransition reassigned = machine.onTaxiUpdate(
                searching.snapshot, TaxiUpdate.arrival("9"), 3_000L);

        assertEquals(RideTransition.Command.HIDE, searching.command);
        assertEquals("", searching.snapshot.plate);
        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, reassigned.command);
        assertEquals(RideStateMachine.UNKNOWN_PLATE, reassigned.snapshot.plate);
        assertEquals("НОМЕР УТОЧНЯЕТСЯ", reassigned.snapshot.plateLine());
        assertEquals(RideStateMachine.UNKNOWN_VEHICLE, reassigned.snapshot.vehicleLine());
        assertEquals("Приедет: 9 мин", reassigned.snapshot.statusLine());
        assertNotEquals(oldRide.sessionGeneration, reassigned.snapshot.sessionGeneration);
    }

    @Test
    public void titleOnlyArrivalUpdatesKnownVehicleWithoutReplacingItsDetails() {
        RideSnapshot oldRide = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("В222ВВ777", "Зелёный", "Sample Car", "14"), 1_000L).snapshot;

        RideTransition etaUpdate = machine.onTaxiUpdate(
                oldRide, TaxiUpdate.arrival("13"), 2_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, etaUpdate.command);
        assertEquals("В222ВВ777", etaUpdate.snapshot.plate);
        assertEquals("Зелёный Sample Car", etaUpdate.snapshot.vehicleLine());
        assertEquals("13", etaUpdate.snapshot.arrivalMinutes);
        assertEquals(oldRide.sessionGeneration, etaUpdate.snapshot.sessionGeneration);
    }

    @Test
    public void completeWaitingUpdateCanCreateFreshVisibleRide() {
        RideTransition waiting = machine.onTaxiUpdate(
                RideSnapshot.empty(),
                TaxiUpdate.waiting("А111АА777", "Синий", "Demo Car", "4"),
                1_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, waiting.command);
        assertTrue(waiting.snapshot.visible);
        assertEquals("А 111 АА ⁷⁷⁷", waiting.snapshot.plateLine());
        assertEquals("Синий Demo Car", waiting.snapshot.vehicleLine());
        assertEquals("Ожидает: 4 мин", waiting.snapshot.statusLine());
    }

    @Test
    public void completeWaitingForDifferentPlateStartsNewSessionInsteadOfUpdatingOldCar() {
        RideSnapshot oldRide = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;

        RideTransition waiting = machine.onTaxiUpdate(
                oldRide,
                TaxiUpdate.waiting("А123ВС78", "Черный", "Geely Coolray", "4"),
                2_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, waiting.command);
        assertNotEquals(oldRide.sessionGeneration, waiting.snapshot.sessionGeneration);
        assertEquals("А123ВС78", waiting.snapshot.plate);
        assertEquals("Черный Geely Coolray", waiting.snapshot.vehicleLine());
        assertEquals("Ожидает: 4 мин", waiting.snapshot.statusLine());
    }

    @Test
    public void completeWaitingForDifferentPlateResetsDismissalSuppression() {
        RideSnapshot oldRide = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot dismissed = machine.onManualDismiss(oldRide, 2_000L).snapshot;

        RideTransition waiting = machine.onTaxiUpdate(
                dismissed,
                TaxiUpdate.waiting("А123ВС78", "Черный", "Geely Coolray", "2"),
                3_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, waiting.command);
        assertTrue(waiting.snapshot.visible);
        assertFalse(waiting.snapshot.dismissed);
        assertEquals(0L, waiting.snapshot.dismissedAtEpochMs);
        assertEquals("А123ВС78", waiting.snapshot.plate);
    }

    @Test
    public void completeWaitingAfterEndedRideStartsNewGenerationEvenForSamePlate() {
        RideSnapshot oldRide = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot ended = machine.onTaxiUpdate(
                oldRide, TaxiUpdate.lifecycle(TaxiUpdate.Kind.ENDED), 2_000L).snapshot;

        RideTransition waiting = machine.onTaxiUpdate(
                ended,
                TaxiUpdate.waiting("А111АА777", "Синий", "Demo Car", "1"),
                3_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, waiting.command);
        assertNotEquals(oldRide.sessionGeneration, waiting.snapshot.sessionGeneration);
        assertFalse(waiting.snapshot.ended);
        assertEquals("Ожидает: 1 мин", waiting.snapshot.statusLine());
    }

    @Test
    public void plateFreeWaitingCannotInitializeEmptyRide() {
        RideTransition waiting = machine.onTaxiUpdate(
                RideSnapshot.empty(), TaxiUpdate.waiting("4"), 1_000L);

        assertEquals(RideTransition.Command.NONE, waiting.command);
        assertEquals("", waiting.snapshot.plate);
        assertFalse(waiting.snapshot.visible);
    }

    @Test
    public void rawConfusablePlateIsNormalizedForStorageComparisonAndDisplay() {
        RideSnapshot raw = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("a 111 aa 777", "Синий", "Demo Car", "3"), 1_000L).snapshot;

        RideTransition normalizedUpdate = machine.onTaxiUpdate(
                raw, active("А111АА777", "Синий", "Demo Car", "2"), 2_000L);
        RideTransition rawAgain = machine.onTaxiUpdate(
                normalizedUpdate.snapshot,
                active("a 111 aa 777", "Синий", "Demo Car", "1"),
                3_000L);

        assertEquals("А111АА777", raw.plate);
        assertEquals("А 111 АА ⁷⁷⁷", raw.plateLine());
        assertEquals(raw.sessionGeneration, normalizedUpdate.snapshot.sessionGeneration);
        assertEquals(raw.sessionGeneration, rawAgain.snapshot.sessionGeneration);
        assertEquals("2", normalizedUpdate.snapshot.arrivalMinutes);
    }

    @Test
    public void strongStartKeepsTripInMenuAndLeavesPinHiddenByDefault() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideTransition started = machine.onTaxiUpdate(
                shown, TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED), 10_000L);

        assertEquals(RideTransition.Command.HIDE, started.command);
        assertTrue(started.snapshot.tripInProgress);
        assertFalse(started.snapshot.visible);
        assertEquals(0L, started.snapshot.countdownEndsAtEpochMs);
    }

    @Test
    public void repeatedStartDoesNotResurrectManuallyHiddenTripPin() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideTransition first = machine.onTaxiUpdate(
                shown, TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED), 2_000L);
        RideTransition repeated = machine.onTaxiUpdate(
                first.snapshot, TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED), 5_000L);

        assertEquals(RideTransition.Command.NONE, repeated.command);
        assertEquals(first.snapshot.revision, repeated.snapshot.revision);
        assertFalse(repeated.snapshot.visible);
        assertTrue(repeated.snapshot.tripInProgress);
    }

    @Test
    public void explicitEndHidesTripAndNextSamePlateGetsNewGeneration() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideTransition ended = machine.onTaxiUpdate(
                shown, TaxiUpdate.lifecycle(TaxiUpdate.Kind.ENDED), 2_000L);

        assertEquals(RideTransition.Command.HIDE, ended.command);
        assertFalse(ended.snapshot.visible);
        assertFalse(ended.snapshot.tripInProgress);
        RideTransition next = machine.onTaxiUpdate(
                ended.snapshot, active("А111АА777", "Синий", "Demo Car", "5"), 3_000L);
        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, next.command);
        assertNotEquals(shown.sessionGeneration, next.snapshot.sessionGeneration);
    }

    @Test
    public void lateWaitingAfterDismissedEndedRideDoesNotResurrectFinalizedGeneration() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot dismissed = machine.onManualDismiss(shown, 2_000L).snapshot;
        RideSnapshot ended = machine.onTaxiUpdate(
                dismissed, TaxiUpdate.lifecycle(TaxiUpdate.Kind.ENDED), 3_000L).snapshot;

        RideTransition lateWaiting = machine.onTaxiUpdate(ended, TaxiUpdate.waiting("5"), 4_000L);

        assertEquals(RideTransition.Command.NONE, lateWaiting.command);
        assertFalse(lateWaiting.snapshot.visible);
        assertTrue(lateWaiting.snapshot.ended);
        assertEquals(shown.sessionGeneration, lateWaiting.snapshot.sessionGeneration);
    }

    @Test
    public void replacementAndCancellationClearTripState() {
        RideSnapshot first = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot counting = machine.onTaxiUpdate(
                first, TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED), 2_000L).snapshot;

        RideTransition replacement = machine.onTaxiUpdate(
                counting, active("А123ВС78", "Черный", "Geely", "4"), 3_000L);
        assertFalse(replacement.snapshot.tripInProgress);
        assertNotEquals(counting.sessionGeneration, replacement.snapshot.sessionGeneration);

        RideSnapshot replacementCounting = machine.onTaxiUpdate(
                replacement.snapshot, TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED), 4_000L).snapshot;
        RideTransition cancelled = machine.onTaxiUpdate(
                replacementCounting, TaxiUpdate.lifecycle(TaxiUpdate.Kind.CANCELLED), 5_000L);
        assertEquals(RideTransition.Command.HIDE, cancelled.command);
        assertEquals(0L, cancelled.snapshot.countdownEndsAtEpochMs);
        assertFalse(cancelled.snapshot.visible);
        assertFalse(cancelled.snapshot.tripInProgress);
        assertTrue(cancelled.snapshot.ended);
    }

    @Test
    public void manualDismissHidesTripPinButKeepsTripTracking() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot counting = machine.onTaxiUpdate(
                shown, TaxiUpdate.tripStarted(20L * 60_000L), 2_000L, true).snapshot;

        RideTransition dismissed = machine.onManualDismiss(counting, 3_000L);

        assertEquals(RideTransition.Command.HIDE, dismissed.command);
        assertFalse(dismissed.snapshot.visible);
        assertTrue(dismissed.snapshot.tripInProgress);
        assertEquals(20L * 60_000L, dismissed.snapshot.tripEndsAtEpochMs);
        assertFalse(dismissed.snapshot.dismissed);
    }

    @Test
    public void restoreKeepsHiddenTripTrackedWithoutShowingPin() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot counting = machine.onTaxiUpdate(
                shown, TaxiUpdate.tripStarted(20L * 60_000L), 2_000L).snapshot;

        RideTransition restored = machine.restore(counting, 8_999L);
        assertEquals(RideTransition.Command.NONE, restored.command);
        assertEquals("А111АА777", restored.snapshot.plate);
        assertTrue(restored.snapshot.tripInProgress);
        assertFalse(restored.snapshot.visible);
    }

    @Test
    public void tripProgressUpdatesEtaAndTapShowsTimerPin() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot started = machine.onTaxiUpdate(
                shown, TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED), 2_000L).snapshot;

        RideTransition progress = machine.onTaxiUpdate(
                started, TaxiUpdate.tripProgress(3_602_000L), 3_000L);
        RideTransition tapped = machine.onTripPinRequested(progress.snapshot);

        assertEquals(RideTransition.Command.NONE, progress.command);
        assertEquals(3_602_000L, progress.snapshot.tripEndsAtEpochMs);
        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, tapped.command);
        assertTrue(tapped.snapshot.visible);
        assertTrue(tapped.snapshot.tripInProgress);
    }

    @Test
    public void automaticTripPinIsVisibleAtStart() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;

        RideTransition started = machine.onTaxiUpdate(
                shown, TaxiUpdate.tripStarted(1_802_000L), 2_000L, true);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, started.command);
        assertTrue(started.snapshot.visible);
        assertTrue(started.snapshot.tripInProgress);
        assertEquals(1_802_000L, started.snapshot.tripEndsAtEpochMs);
    }

    @Test
    public void countdownMetadataAfterWaitingCanRecoverMissedStartEvent() {
        RideSnapshot waiting = machine.onTaxiUpdate(
                RideSnapshot.empty(),
                TaxiUpdate.waiting("А111АА777", "Синий", "Demo Car", ""),
                1_000L).snapshot;

        RideTransition inferred = machine.onTaxiUpdate(
                waiting, TaxiUpdate.tripProgress(1_802_000L), 2_000L, false);

        assertEquals(RideTransition.Command.HIDE, inferred.command);
        assertTrue(inferred.snapshot.tripInProgress);
        assertEquals(1_802_000L, inferred.snapshot.tripEndsAtEpochMs);
        assertFalse(inferred.snapshot.visible);
    }

    @Test
    public void restoreShowsDismissedRideWhenTwoHourSuppressionHasExpired() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;
        RideSnapshot dismissed = machine.onManualDismiss(shown, 2_000L).snapshot;

        RideTransition restored = machine.restore(dismissed, 2_000L + 7_200_000L);

        assertEquals(RideTransition.Command.SHOW_OR_UPDATE, restored.command);
        assertTrue(restored.snapshot.visible);
        assertFalse(restored.snapshot.dismissed);
    }

    @Test
    public void noMatchDoesNotChangeCurrentRide() {
        RideSnapshot shown = machine.onTaxiUpdate(
                RideSnapshot.empty(), active("А111АА777", "Синий", "Demo Car", "3"), 1_000L).snapshot;

        RideTransition ignored = machine.onTaxiUpdate(shown, TaxiUpdate.noMatch(), 2_000L);

        assertEquals(RideTransition.Command.NONE, ignored.command);
        assertEquals(shown.revision, ignored.snapshot.revision);
        assertEquals("3", ignored.snapshot.arrivalMinutes);
    }

    private static TaxiUpdate active(
            String plate, String color, String makeModel, String arrivalMinutes) {
        return TaxiUpdate.active(plate, color, makeModel, arrivalMinutes);
    }
}
