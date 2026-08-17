package dev.havoc.taxihud.phone.state;

import java.util.Objects;

public final class RideSnapshot {
    public final String plate;
    public final String color;
    public final String makeModel;
    public final String arrivalMinutes;
    public final boolean waiting;
    public final String waitingMinutes;
    public final long sessionGeneration;
    public final long revision;
    public final boolean visible;
    public final boolean dismissed;
    public final long dismissedAtEpochMs;
    public final boolean ended;
    public final long countdownEndsAtEpochMs;
    public final String sourceAdapterId;
    public final String sourceDisplayName;
    public final String sourcePackage;
    public final long pinTtlMs;
    public final boolean tripInProgress;
    public final long tripEndsAtEpochMs;

    public RideSnapshot(
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
        this(plate, color, makeModel, arrivalMinutes, waiting, waitingMinutes,
                sessionGeneration, revision, visible, dismissed, dismissedAtEpochMs,
                ended, countdownEndsAtEpochMs, "", "", "", 30L * 60L * 1_000L,
                false, 0L);
    }

    public RideSnapshot(
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
            String sourceAdapterId,
            String sourceDisplayName,
            String sourcePackage,
            long pinTtlMs) {
        this(plate, color, makeModel, arrivalMinutes, waiting, waitingMinutes,
                sessionGeneration, revision, visible, dismissed, dismissedAtEpochMs,
                ended, countdownEndsAtEpochMs, sourceAdapterId, sourceDisplayName,
                sourcePackage, pinTtlMs, false, 0L);
    }

    public RideSnapshot(
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
            String sourceAdapterId,
            String sourceDisplayName,
            String sourcePackage,
            long pinTtlMs,
            boolean tripInProgress,
            long tripEndsAtEpochMs) {
        this.plate = PlateFormatter.normalize(plate);
        this.color = orEmpty(color);
        this.makeModel = orEmpty(makeModel);
        this.arrivalMinutes = orEmpty(arrivalMinutes);
        this.waiting = waiting;
        this.waitingMinutes = orEmpty(waitingMinutes);
        this.sessionGeneration = sessionGeneration;
        this.revision = revision;
        this.visible = visible;
        this.dismissed = dismissed;
        this.dismissedAtEpochMs = dismissedAtEpochMs;
        this.ended = ended;
        this.countdownEndsAtEpochMs = countdownEndsAtEpochMs;
        this.sourceAdapterId = orEmpty(sourceAdapterId);
        this.sourceDisplayName = orEmpty(sourceDisplayName);
        this.sourcePackage = orEmpty(sourcePackage);
        this.pinTtlMs = pinTtlMs > 0L ? pinTtlMs : 30L * 60L * 1_000L;
        this.tripInProgress = tripInProgress;
        this.tripEndsAtEpochMs = Math.max(0L, tripEndsAtEpochMs);
    }

    public static RideSnapshot empty() {
        return new RideSnapshot("", "", "", "", false, "", 0L, 0L,
                false, false, 0L, false, 0L);
    }

    public String sessionId() {
        return plate + "#" + sessionGeneration;
    }

    public String plateLine() {
        return PlateFormatter.display(plate);
    }

    public String vehicleLine() {
        return (color + " " + makeModel).trim();
    }

    public String statusLine() {
        if (!waitingMinutes.isEmpty()) {
            return "Ожидает: " + waitingMinutes + " мин";
        }
        if (waiting) {
            return "Ожидает";
        }
        if (!arrivalMinutes.isEmpty()) {
            return "Приедет: " + arrivalMinutes + " мин";
        }
        return "";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RideSnapshot)) {
            return false;
        }
        RideSnapshot snapshot = (RideSnapshot) other;
        return waiting == snapshot.waiting
                && sessionGeneration == snapshot.sessionGeneration
                && revision == snapshot.revision
                && visible == snapshot.visible
                && dismissed == snapshot.dismissed
                && dismissedAtEpochMs == snapshot.dismissedAtEpochMs
                && ended == snapshot.ended
                && countdownEndsAtEpochMs == snapshot.countdownEndsAtEpochMs
                && pinTtlMs == snapshot.pinTtlMs
                && tripInProgress == snapshot.tripInProgress
                && tripEndsAtEpochMs == snapshot.tripEndsAtEpochMs
                && Objects.equals(plate, snapshot.plate)
                && Objects.equals(color, snapshot.color)
                && Objects.equals(makeModel, snapshot.makeModel)
                && Objects.equals(arrivalMinutes, snapshot.arrivalMinutes)
                && Objects.equals(waitingMinutes, snapshot.waitingMinutes)
                && Objects.equals(sourceAdapterId, snapshot.sourceAdapterId)
                && Objects.equals(sourceDisplayName, snapshot.sourceDisplayName)
                && Objects.equals(sourcePackage, snapshot.sourcePackage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plate, color, makeModel, arrivalMinutes, waiting, waitingMinutes,
                sessionGeneration, revision, visible, dismissed, dismissedAtEpochMs, ended,
                countdownEndsAtEpochMs, sourceAdapterId, sourceDisplayName, sourcePackage,
                pinTtlMs, tripInProgress, tripEndsAtEpochMs);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
