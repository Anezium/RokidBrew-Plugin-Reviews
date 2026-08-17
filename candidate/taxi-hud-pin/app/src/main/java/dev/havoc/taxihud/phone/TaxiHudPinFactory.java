package dev.havoc.taxihud.phone;

import android.content.Context;

import com.anezium.rokidbus.client.plugin.NexusPin;
import com.anezium.rokidbus.client.plugin.NexusPinEmphasis;
import com.anezium.rokidbus.client.plugin.NexusPinLine;
import com.anezium.rokidbus.client.plugin.NexusPinPosition;
import com.anezium.rokidbus.client.plugin.NexusPinSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.havoc.taxihud.phone.state.RideSnapshot;

final class TaxiHudPinFactory {
    private static final int TITLE_LIMIT = 28;
    private static final int LINE_LIMIT = 32;

    private TaxiHudPinFactory() {
    }

    static NexusPin ride(Context context, RideSnapshot snapshot, long nowEpochMs) {
        if (snapshot.tripInProgress) {
            return trip(context, snapshot, nowEpochMs);
        }
        List<NexusPinLine> lines = new ArrayList<>();
        String vehicle = RideText.vehicle(context, snapshot);
        lines.add(new NexusPinLine(
                bounded(vehicle.isEmpty()
                        ? RideText.string(context, com.havoc.rokid.plugin.taxihudpin.R.string.hud_vehicle_unknown)
                        : vehicle, LINE_LIMIT),
                NexusPinEmphasis.DEFAULT));
        lines.add(new NexusPinLine(
                bounded(RideText.status(context, snapshot, nowEpochMs), LINE_LIMIT),
                NexusPinEmphasis.BRIGHT));
        if (!snapshot.sourceDisplayName.isEmpty()) {
            lines.add(new NexusPinLine(
                    bounded(snapshot.sourceDisplayName, LINE_LIMIT),
                    NexusPinEmphasis.DIM));
        }
        String plate = RideText.plate(context, snapshot);
        return new NexusPin(
                bounded(plate.isEmpty()
                                ? RideText.string(context, com.havoc.rokid.plugin.taxihudpin.R.string.app_name)
                                : plate,
                        TITLE_LIMIT),
                Collections.emptyList(),
                NexusPinPosition.TOP_RIGHT,
                snapshot.pinTtlMs,
                NexusPinSize.MEDIUM,
                lines);
    }

    private static NexusPin trip(Context context, RideSnapshot snapshot, long nowEpochMs) {
        String duration = snapshot.tripEndsAtEpochMs > 0L
                ? RideText.tripDuration(context, snapshot.tripEndsAtEpochMs, nowEpochMs)
                : RideText.string(context,
                        com.havoc.rokid.plugin.taxihudpin.R.string.hud_trip_in_progress);
        long ttl = snapshot.tripEndsAtEpochMs > nowEpochMs
                ? Math.min(86_400_000L,
                        Math.max(60_000L, snapshot.tripEndsAtEpochMs - nowEpochMs + 120_000L))
                : 30L * 60L * 1_000L;
        return new NexusPin(
                bounded(RideText.string(context,
                        com.havoc.rokid.plugin.taxihudpin.R.string.hud_trip_remaining_title),
                        TITLE_LIMIT),
                Collections.emptyList(),
                NexusPinPosition.TOP_RIGHT,
                ttl,
                NexusPinSize.SMALL,
                Collections.singletonList(new NexusPinLine(
                        bounded(duration, LINE_LIMIT), NexusPinEmphasis.BRIGHT)));
    }

    private static String bounded(String value, int limit) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit - 1) + "…";
    }
}
