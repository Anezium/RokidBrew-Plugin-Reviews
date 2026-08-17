package dev.havoc.taxihud.phone.log;

import dev.havoc.taxihud.phone.parse.TaxiUpdate;

public final class NotificationParserResult {
    public final String status;
    public final String plate;
    public final String color;
    public final String makeModel;
    public final String arrivalMinutes;
    public final String waitingMinutes;
    public final long tripEndsAtEpochMs;

    public NotificationParserResult(String status, String plate, String color,
            String makeModel, String arrivalMinutes, String waitingMinutes) {
        this(status, plate, color, makeModel, arrivalMinutes, waitingMinutes, 0L);
    }

    public NotificationParserResult(String status, String plate, String color,
            String makeModel, String arrivalMinutes, String waitingMinutes,
            long tripEndsAtEpochMs) {
        this.status = value(status);
        this.plate = value(plate);
        this.color = value(color);
        this.makeModel = value(makeModel);
        this.arrivalMinutes = value(arrivalMinutes);
        this.waitingMinutes = value(waitingMinutes);
        this.tripEndsAtEpochMs = Math.max(0L, tripEndsAtEpochMs);
    }

    public static NotificationParserResult from(TaxiUpdate update) {
        return new NotificationParserResult(update.kind.name(), update.plate, update.color,
                update.makeModel, update.arrivalMinutes, update.waitingMinutes,
                update.tripEndsAtEpochMs);
    }

    public static NotificationParserResult empty() {
        return new NotificationParserResult("", "", "", "", "", "");
    }

    private static String value(String value) { return value == null ? "" : value; }
}
