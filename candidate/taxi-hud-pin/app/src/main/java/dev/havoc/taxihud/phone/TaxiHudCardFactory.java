package dev.havoc.taxihud.phone;

import android.content.Context;

import com.anezium.rokidbus.client.plugin.NexusCard;
import com.havoc.rokid.plugin.taxihudpin.R;

import java.util.ArrayList;
import java.util.List;

import dev.havoc.taxihud.phone.state.RideSnapshot;

final class TaxiHudCardFactory {
    private TaxiHudCardFactory() {
    }

    static NexusCard ride(Context context, RideSnapshot snapshot, long nowEpochMs) {
        return ride(context, snapshot, List.of(), nowEpochMs);
    }

    static NexusCard ride(
            Context context,
            RideSnapshot snapshot,
            List<RideHistoryEntry> history,
            long nowEpochMs) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, RideText.plate(context, snapshot));
        addIfPresent(lines, RideText.vehicle(context, snapshot));
        addIfPresent(lines, RideText.status(context, snapshot, nowEpochMs));
        addIfPresent(lines, snapshot.sourceDisplayName);
        appendHistory(context, lines, history);
        return new NexusCard(
                RideText.string(context, R.string.app_name),
                lines,
                RideText.string(context, snapshot.tripInProgress
                        ? R.string.hud_show_trip_timer
                        : R.string.hud_tap_clear),
                (snapshot.tripInProgress ? "show-trip-timer-" : "clear-notification-")
                        + snapshot.sessionGeneration + "-" + snapshot.revision
                        + tripMinuteKey(snapshot, nowEpochMs),
                null,
                false,
                null);
    }

    static NexusCard empty(Context context) {
        return empty(context, List.of());
    }

    static NexusCard empty(Context context, List<RideHistoryEntry> history) {
        List<String> lines = new ArrayList<>();
        lines.add(RideText.string(context, R.string.hud_no_active_notification));
        appendHistory(context, lines, history);
        return new NexusCard(
                RideText.string(context, R.string.app_name),
                lines,
                null,
                "taxi-hud-no-notification",
                null,
                false,
                null);
    }

    private static void appendHistory(
            Context context, List<String> lines, List<RideHistoryEntry> history) {
        lines.add(RideText.string(context, R.string.hud_recent_rides));
        if (history == null || history.isEmpty()) {
            lines.add(RideText.string(context, R.string.hud_history_empty));
            return;
        }
        for (RideHistoryEntry entry : history) {
            if (entry != null && !entry.displayLine().isEmpty()) {
                lines.add(entry.displayLine());
            }
        }
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.trim().isEmpty()) {
            lines.add(value.trim());
        }
    }

    private static String tripMinuteKey(RideSnapshot snapshot, long nowEpochMs) {
        if (!snapshot.tripInProgress || snapshot.tripEndsAtEpochMs <= 0L) {
            return "";
        }
        long minutes = Math.max(
                1L, (snapshot.tripEndsAtEpochMs - nowEpochMs + 59_999L) / 60_000L);
        return "-" + minutes;
    }
}
