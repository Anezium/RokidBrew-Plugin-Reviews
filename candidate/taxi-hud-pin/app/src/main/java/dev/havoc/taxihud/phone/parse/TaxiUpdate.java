package dev.havoc.taxihud.phone.parse;

public final class TaxiUpdate {
    public enum Kind {
        ACTIVE,
        WAITING,
        STARTED,
        TRIP_PROGRESS,
        SEARCHING,
        ENDED,
        CANCELLED,
        NO_MATCH
    }

    public final Kind kind;
    public final String plate;
    public final String color;
    public final String makeModel;
    public final String arrivalMinutes;
    public final String waitingMinutes;
    public final boolean forceNewSession;
    public final String sourceAdapterId;
    public final String sourceDisplayName;
    public final String sourcePackage;
    public final long sourcePinTtlMs;
    public final long tripEndsAtEpochMs;

    private TaxiUpdate(
            Kind kind,
            String plate,
            String color,
            String makeModel,
            String arrivalMinutes,
            String waitingMinutes,
            boolean forceNewSession) {
        this(kind, plate, color, makeModel, arrivalMinutes, waitingMinutes,
                forceNewSession, "", "", "", 30L * 60L * 1_000L, 0L);
    }

    private TaxiUpdate(
            Kind kind,
            String plate,
            String color,
            String makeModel,
            String arrivalMinutes,
            String waitingMinutes,
            boolean forceNewSession,
            String sourceAdapterId,
            String sourceDisplayName,
            String sourcePackage,
            long sourcePinTtlMs,
            long tripEndsAtEpochMs) {
        this.kind = kind;
        this.plate = orEmpty(plate);
        this.color = orEmpty(color);
        this.makeModel = orEmpty(makeModel);
        this.arrivalMinutes = orEmpty(arrivalMinutes);
        this.waitingMinutes = orEmpty(waitingMinutes);
        this.forceNewSession = forceNewSession;
        this.sourceAdapterId = orEmpty(sourceAdapterId);
        this.sourceDisplayName = orEmpty(sourceDisplayName);
        this.sourcePackage = orEmpty(sourcePackage);
        this.sourcePinTtlMs = sourcePinTtlMs;
        this.tripEndsAtEpochMs = Math.max(0L, tripEndsAtEpochMs);
    }

    public static TaxiUpdate active(
            String plate, String color, String makeModel, String arrivalMinutes) {
        return new TaxiUpdate(Kind.ACTIVE, plate, color, makeModel, arrivalMinutes, "", false);
    }

    public static TaxiUpdate syntheticTest(
            String plate, String color, String makeModel, String arrivalMinutes) {
        return new TaxiUpdate(Kind.ACTIVE, plate, color, makeModel, arrivalMinutes, "", true);
    }

    public static TaxiUpdate waiting(String waitingMinutes) {
        return new TaxiUpdate(Kind.WAITING, "", "", "", "", waitingMinutes, false);
    }

    public static TaxiUpdate waiting(
            String plate, String color, String makeModel, String waitingMinutes) {
        return new TaxiUpdate(Kind.WAITING, plate, color, makeModel, "", waitingMinutes, false);
    }

    public static TaxiUpdate lifecycle(Kind kind) {
        return new TaxiUpdate(kind, "", "", "", "", "", false);
    }

    public static TaxiUpdate tripStarted(long tripEndsAtEpochMs) {
        return new TaxiUpdate(Kind.STARTED, "", "", "", "", "", false,
                "", "", "", 30L * 60L * 1_000L, tripEndsAtEpochMs);
    }

    public static TaxiUpdate tripProgress(long tripEndsAtEpochMs) {
        return new TaxiUpdate(Kind.TRIP_PROGRESS, "", "", "", "", "", false,
                "", "", "", 30L * 60L * 1_000L, tripEndsAtEpochMs);
    }

    public static TaxiUpdate arrival(String arrivalMinutes) {
        return new TaxiUpdate(Kind.ACTIVE, "", "", "", arrivalMinutes, "", false);
    }

    public static TaxiUpdate noMatch() {
        return lifecycle(Kind.NO_MATCH);
    }

    public TaxiUpdate withSource(
            String adapterId, String displayName, String packageName, long pinTtlMs) {
        return new TaxiUpdate(
                kind, plate, color, makeModel, arrivalMinutes, waitingMinutes,
                forceNewSession, adapterId, displayName, packageName, pinTtlMs,
                tripEndsAtEpochMs);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
