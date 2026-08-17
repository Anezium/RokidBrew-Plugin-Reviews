package dev.havoc.taxihud.phone;

import android.content.Context;

import com.havoc.rokid.plugin.taxihudpin.R;

import dev.havoc.taxihud.phone.state.RideSnapshot;

final class RideText {
    private static final String UNKNOWN_PLATE = "###";
    private static final String LEGACY_UNKNOWN_VEHICLE = "Новая машина";

    private RideText() {
    }

    static String plate(Context context, RideSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (UNKNOWN_PLATE.equals(snapshot.plate)) {
            return string(context, R.string.hud_plate_unknown);
        }
        return snapshot.plateLine();
    }

    static String vehicle(Context context, RideSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (LEGACY_UNKNOWN_VEHICLE.equals(snapshot.makeModel)) {
            return string(context, R.string.hud_vehicle_unknown);
        }
        return snapshot.vehicleLine();
    }

    static String status(Context context, RideSnapshot snapshot, long nowEpochMs) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.tripInProgress) {
            if (snapshot.tripEndsAtEpochMs > 0L) {
                return string(context, R.string.hud_trip_remaining_status,
                        tripDuration(context, snapshot.tripEndsAtEpochMs, nowEpochMs));
            }
            return string(context, R.string.hud_trip_in_progress);
        }
        if (snapshot.countdownEndsAtEpochMs > nowEpochMs) {
            long seconds = Math.max(
                    1L, (snapshot.countdownEndsAtEpochMs - nowEpochMs + 999L) / 1_000L);
            return string(context, R.string.hud_ride_started_seconds, seconds);
        }
        if (!snapshot.waitingMinutes.isEmpty()) {
            return string(context, R.string.hud_waiting_minutes, snapshot.waitingMinutes);
        }
        if (snapshot.waiting) {
            return string(context, R.string.hud_waiting);
        }
        if (!snapshot.arrivalMinutes.isEmpty()) {
            return string(context, R.string.hud_arrival_minutes, snapshot.arrivalMinutes);
        }
        return string(context, R.string.hud_active_order);
    }

    static String tripDuration(Context context, long endsAtEpochMs, long nowEpochMs) {
        long minutes = Math.max(1L, (endsAtEpochMs - nowEpochMs + 59_999L) / 60_000L);
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        return hours > 0L
                ? string(context, R.string.hud_trip_hours_minutes, hours, remainder)
                : string(context, R.string.hud_trip_minutes, minutes);
    }

    static String string(Context context, int resource, Object... arguments) {
        Context localized = TaxiLocale.localized(context);
        return arguments.length == 0
                ? localized.getString(resource)
                : localized.getString(resource, arguments);
    }
}
