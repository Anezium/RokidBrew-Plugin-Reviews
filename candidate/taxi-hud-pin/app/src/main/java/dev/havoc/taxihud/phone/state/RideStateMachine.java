package dev.havoc.taxihud.phone.state;

import dev.havoc.taxihud.phone.parse.TaxiUpdate;

public final class RideStateMachine {
    public static final long DISMISS_SUPPRESSION_MS = 2L * 60L * 60L * 1000L;
    public static final long COUNTDOWN_MS = 7_000L;
    static final String UNKNOWN_PLATE = PlateFormatter.UNKNOWN;
    static final String UNKNOWN_VEHICLE = "Новая машина";

    public RideTransition onTaxiUpdate(
            RideSnapshot current, TaxiUpdate update, long nowEpochMs) {
        return onTaxiUpdate(current, update, nowEpochMs, false);
    }

    public RideTransition onTaxiUpdate(
            RideSnapshot current,
            TaxiUpdate update,
            long nowEpochMs,
            boolean autoTripPin) {
        RideSnapshot state = current == null ? RideSnapshot.empty() : current;
        if (update == null) {
            return none(state);
        }

        state = expireSuppression(state, nowEpochMs);

        if (update.kind == TaxiUpdate.Kind.NO_MATCH) {
            return none(state);
        }

        if (update.kind == TaxiUpdate.Kind.SEARCHING) {
            if (state.plate.isEmpty()) {
                return none(state);
            }
            RideSnapshot cleared = snapshotFrom(state,
                    "", "", "", "", false, "",
                    state.sessionGeneration, state.revision + 1L,
                    false, false, 0L, false, 0L);
            return new RideTransition(cleared, RideTransition.Command.HIDE);
        }

        String incomingPlate = PlateFormatter.normalize(update.plate);
        boolean completeVehicleUpdate = (update.kind == TaxiUpdate.Kind.ACTIVE
                || update.kind == TaxiUpdate.Kind.WAITING)
                && !incomingPlate.isEmpty();
        boolean differentPlate = completeVehicleUpdate
                && !state.plate.isEmpty()
                && !incomingPlate.equals(state.plate);
        boolean vehicleAfterEnd = completeVehicleUpdate
                && state.ended;
        boolean firstVehicle = completeVehicleUpdate
                && state.plate.isEmpty();
        boolean forcedNewSession = update.forceNewSession && completeVehicleUpdate;
        if (forcedNewSession || differentPlate || vehicleAfterEnd || firstVehicle) {
            return new RideTransition(newSession(state, update), RideTransition.Command.SHOW_OR_UPDATE);
        }

        if (state.ended) {
            return none(state);
        }

        if (update.kind == TaxiUpdate.Kind.ENDED
                || update.kind == TaxiUpdate.Kind.CANCELLED) {
            if (state.plate.isEmpty()) {
                return none(state);
            }
            RideSnapshot finalized = snapshotWithTrip(state,
                    state.plate, state.color, state.makeModel, state.arrivalMinutes,
                    state.waiting, state.waitingMinutes,
                    state.sessionGeneration, state.revision + 1L,
                    false, false, 0L, true, 0L, false, 0L);
            return new RideTransition(finalized, RideTransition.Command.HIDE);
        }

        if (update.kind == TaxiUpdate.Kind.STARTED) {
            if (state.plate.isEmpty() || state.ended) {
                return none(state);
            }
            long deadline = update.tripEndsAtEpochMs > 0L
                    ? update.tripEndsAtEpochMs
                    : state.tripEndsAtEpochMs;
            if (state.tripInProgress) {
                if (deadline == state.tripEndsAtEpochMs) {
                    return none(state);
                }
                RideSnapshot refreshed = snapshotWithTrip(state,
                        state.plate, state.color, state.makeModel, "", false, "",
                        state.sessionGeneration, state.revision + 1L,
                        state.visible, false, 0L, false, 0L, true, deadline);
                return new RideTransition(refreshed, state.visible
                        ? RideTransition.Command.SHOW_OR_UPDATE
                        : RideTransition.Command.NONE);
            }
            RideSnapshot started = snapshotWithTrip(state,
                    state.plate, state.color, state.makeModel, "", false, "",
                    state.sessionGeneration, state.revision + 1L,
                    autoTripPin, false, 0L, false, 0L, true, deadline);
            return new RideTransition(started, autoTripPin
                    ? RideTransition.Command.SHOW_OR_UPDATE
                    : RideTransition.Command.HIDE);
        }

        if (update.kind == TaxiUpdate.Kind.TRIP_PROGRESS) {
            if (state.plate.isEmpty() || update.tripEndsAtEpochMs <= 0L) {
                return none(state);
            }
            if (!state.tripInProgress) {
                if (!state.waiting) {
                    return none(state);
                }
                RideSnapshot inferredStart = snapshotWithTrip(state,
                        state.plate, state.color, state.makeModel, "", false, "",
                        state.sessionGeneration, state.revision + 1L,
                        autoTripPin, false, 0L, false, 0L,
                        true, update.tripEndsAtEpochMs);
                return new RideTransition(inferredStart, autoTripPin
                        ? RideTransition.Command.SHOW_OR_UPDATE
                        : RideTransition.Command.HIDE);
            }
            if (state.tripEndsAtEpochMs == update.tripEndsAtEpochMs) {
                return none(state);
            }
            RideSnapshot progressed = snapshotWithTrip(state,
                    state.plate, state.color, state.makeModel, "", false, "",
                    state.sessionGeneration, state.revision + 1L,
                    state.visible, false, 0L, false, 0L,
                    true, update.tripEndsAtEpochMs);
            return new RideTransition(progressed, state.visible
                    ? RideTransition.Command.SHOW_OR_UPDATE
                    : RideTransition.Command.NONE);
        }

        // Late pre-pickup notification updates can arrive after the trip-start event.
        // They must not turn the plate pin back on or replace the trip countdown.
        if (state.tripInProgress
                && (update.kind == TaxiUpdate.Kind.ACTIVE
                        || update.kind == TaxiUpdate.Kind.WAITING)) {
            return none(state);
        }

        if (update.kind == TaxiUpdate.Kind.WAITING) {
            if (state.plate.isEmpty()) {
                return none(state);
            }
            state = snapshotWithUpdateSource(state, update,
                    state.plate, state.color, state.makeModel, state.arrivalMinutes,
                    true, prefer(update.waitingMinutes, state.waitingMinutes),
                    state.sessionGeneration, state.revision,
                    state.visible, state.dismissed, state.dismissedAtEpochMs,
                    state.ended, state.countdownEndsAtEpochMs);
        } else if (update.kind == TaxiUpdate.Kind.ACTIVE) {
            if (state.plate.isEmpty()) {
                if (update.arrivalMinutes.isEmpty()) {
                    return none(state);
                }
                TaxiUpdate placeholder = TaxiUpdate.active(
                        UNKNOWN_PLATE, "", UNKNOWN_VEHICLE, update.arrivalMinutes)
                        .withSource(update.sourceAdapterId, update.sourceDisplayName,
                                update.sourcePackage, update.sourcePinTtlMs);
                return new RideTransition(
                        newSession(state, placeholder),
                        RideTransition.Command.SHOW_OR_UPDATE);
            }
            state = snapshotWithUpdateSource(state, update,
                    prefer(incomingPlate, state.plate),
                    prefer(update.color, state.color),
                    prefer(update.makeModel, state.makeModel),
                    prefer(update.arrivalMinutes, state.arrivalMinutes),
                    false, "",
                    state.sessionGeneration, state.revision,
                    state.visible, state.dismissed, state.dismissedAtEpochMs,
                    false, state.countdownEndsAtEpochMs);
        } else {
            return none(state);
        }

        if (state.dismissed) {
            return none(state);
        }

        RideSnapshot shown = snapshotFrom(state,
                state.plate, state.color, state.makeModel, state.arrivalMinutes,
                state.waiting, state.waitingMinutes,
                state.sessionGeneration, state.revision + 1L,
                true, false, 0L, state.ended, state.countdownEndsAtEpochMs);
        return new RideTransition(shown, RideTransition.Command.SHOW_OR_UPDATE);
    }

    public RideTransition onManualDismiss(RideSnapshot current, long nowEpochMs) {
        RideSnapshot state = current == null ? RideSnapshot.empty() : current;
        if (state.plate.isEmpty() || state.dismissed) {
            return none(state);
        }
        if (state.tripInProgress) {
            if (!state.visible) {
                return none(state);
            }
            RideSnapshot hidden = snapshotWithTrip(state,
                    state.plate, state.color, state.makeModel, "", false, "",
                    state.sessionGeneration, state.revision + 1L,
                    false, false, 0L, false, 0L,
                    true, state.tripEndsAtEpochMs);
            return new RideTransition(hidden, RideTransition.Command.HIDE);
        }
        RideSnapshot dismissed = snapshotFrom(state,
                state.plate, state.color, state.makeModel, state.arrivalMinutes,
                state.waiting, state.waitingMinutes,
                state.sessionGeneration, state.revision + 1L,
                false, true, nowEpochMs, state.ended, 0L);
        return new RideTransition(dismissed, RideTransition.Command.HIDE);
    }

    public RideTransition onTripPinRequested(RideSnapshot current) {
        RideSnapshot state = current == null ? RideSnapshot.empty() : current;
        if (state.plate.isEmpty() || state.ended || !state.tripInProgress) {
            return none(state);
        }
        RideSnapshot shown = snapshotWithTrip(state,
                state.plate, state.color, state.makeModel, "", false, "",
                state.sessionGeneration, state.revision + 1L,
                true, false, 0L, false, 0L,
                true, state.tripEndsAtEpochMs);
        return new RideTransition(shown, RideTransition.Command.SHOW_OR_UPDATE);
    }

    public RideTransition onCountdownExpired(RideSnapshot current, long nowEpochMs) {
        RideSnapshot state = current == null ? RideSnapshot.empty() : current;
        if (state.countdownEndsAtEpochMs == 0L || nowEpochMs < state.countdownEndsAtEpochMs) {
            return none(state);
        }
        RideSnapshot cleared = new RideSnapshot(
                "", "", "", "", false, "",
                state.sessionGeneration, state.revision + 1L,
                false, false, 0L, false, 0L,
                state.sourceAdapterId, state.sourceDisplayName, state.sourcePackage,
                state.pinTtlMs, false, 0L);
        return new RideTransition(cleared, RideTransition.Command.HIDE);
    }

    public RideTransition restore(RideSnapshot current, long nowEpochMs) {
        RideSnapshot state = current == null ? RideSnapshot.empty() : current;
        if (state.tripInProgress && !state.ended) {
            return new RideTransition(state, state.visible
                    ? RideTransition.Command.SHOW_OR_UPDATE
                    : RideTransition.Command.NONE);
        }
        if (state.countdownEndsAtEpochMs > 0L) {
            if (nowEpochMs >= state.countdownEndsAtEpochMs) {
                return onCountdownExpired(state, nowEpochMs);
            }
            return new RideTransition(state, RideTransition.Command.START_COUNTDOWN);
        }
        boolean wasDismissed = state.dismissed;
        state = expireSuppression(state, nowEpochMs);
        if (wasDismissed && !state.dismissed && !state.ended && !state.plate.isEmpty()) {
            RideSnapshot shown = snapshotFrom(state,
                    state.plate, state.color, state.makeModel, state.arrivalMinutes,
                    state.waiting, state.waitingMinutes,
                    state.sessionGeneration, state.revision + 1L,
                    true, false, 0L, false, state.countdownEndsAtEpochMs);
            return new RideTransition(shown, RideTransition.Command.SHOW_OR_UPDATE);
        }
        if (state.plate.isEmpty() || state.dismissed || !state.visible) {
            return none(state);
        }
        return new RideTransition(state, RideTransition.Command.SHOW_OR_UPDATE);
    }

    private static RideSnapshot expireSuppression(RideSnapshot state, long nowEpochMs) {
        if (!state.dismissed
                || nowEpochMs - state.dismissedAtEpochMs < DISMISS_SUPPRESSION_MS) {
            return state;
        }
        return snapshotFrom(state,
                state.plate, state.color, state.makeModel, state.arrivalMinutes,
                state.waiting, state.waitingMinutes,
                state.sessionGeneration, state.revision,
                state.visible, false, 0L, state.ended, state.countdownEndsAtEpochMs);
    }

    private static RideSnapshot newSession(RideSnapshot state, TaxiUpdate update) {
        boolean waiting = update.kind == TaxiUpdate.Kind.WAITING;
        return new RideSnapshot(
                update.plate,
                update.color,
                update.makeModel,
                waiting ? "" : update.arrivalMinutes,
                waiting,
                waiting ? update.waitingMinutes : "",
                state.sessionGeneration + 1L,
                state.revision + 1L,
                true,
                false,
                0L,
                false,
                0L,
                update.sourceAdapterId,
                update.sourceDisplayName,
                update.sourcePackage,
                update.sourcePinTtlMs,
                false,
                0L);
    }

    private static String prefer(String incoming, String existing) {
        return incoming.isEmpty() ? existing : incoming;
    }

    private static RideTransition none(RideSnapshot snapshot) {
        return new RideTransition(snapshot, RideTransition.Command.NONE);
    }

    private static RideSnapshot snapshotFrom(
            RideSnapshot source,
            String plate,
            String color,
            String makeModel,
            String arrivalMinutes,
            boolean waiting,
            String waitingMinutes,
            long sessionGeneration,
            long revision,
            boolean visible,
            boolean dismissed,
            long dismissedAtEpochMs,
            boolean ended,
            long countdownEndsAtEpochMs) {
        return new RideSnapshot(
                plate, color, makeModel, arrivalMinutes, waiting, waitingMinutes,
                sessionGeneration, revision, visible, dismissed, dismissedAtEpochMs,
                ended, countdownEndsAtEpochMs, source.sourceAdapterId,
                source.sourceDisplayName, source.sourcePackage, source.pinTtlMs,
                source.tripInProgress, source.tripEndsAtEpochMs);
    }

    private static RideSnapshot snapshotWithTrip(
            RideSnapshot source,
            String plate,
            String color,
            String makeModel,
            String arrivalMinutes,
            boolean waiting,
            String waitingMinutes,
            long sessionGeneration,
            long revision,
            boolean visible,
            boolean dismissed,
            long dismissedAtEpochMs,
            boolean ended,
            long countdownEndsAtEpochMs,
            boolean tripInProgress,
            long tripEndsAtEpochMs) {
        return new RideSnapshot(
                plate, color, makeModel, arrivalMinutes, waiting, waitingMinutes,
                sessionGeneration, revision, visible, dismissed, dismissedAtEpochMs,
                ended, countdownEndsAtEpochMs, source.sourceAdapterId,
                source.sourceDisplayName, source.sourcePackage, source.pinTtlMs,
                tripInProgress, tripEndsAtEpochMs);
    }

    private static RideSnapshot snapshotWithUpdateSource(
            RideSnapshot source,
            TaxiUpdate update,
            String plate,
            String color,
            String makeModel,
            String arrivalMinutes,
            boolean waiting,
            String waitingMinutes,
            long sessionGeneration,
            long revision,
            boolean visible,
            boolean dismissed,
            long dismissedAtEpochMs,
            boolean ended,
            long countdownEndsAtEpochMs) {
        return new RideSnapshot(
                plate, color, makeModel, arrivalMinutes, waiting, waitingMinutes,
                sessionGeneration, revision, visible, dismissed, dismissedAtEpochMs,
                ended, countdownEndsAtEpochMs,
                prefer(update.sourceAdapterId, source.sourceAdapterId),
                prefer(update.sourceDisplayName, source.sourceDisplayName),
                prefer(update.sourcePackage, source.sourcePackage),
                update.sourcePinTtlMs > 0L ? update.sourcePinTtlMs : source.pinTtlMs,
                source.tripInProgress,
                source.tripEndsAtEpochMs);
    }
}
