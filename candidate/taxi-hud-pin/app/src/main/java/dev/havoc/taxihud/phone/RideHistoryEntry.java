package dev.havoc.taxihud.phone;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import dev.havoc.taxihud.phone.state.RideSnapshot;

public final class RideHistoryEntry {
    public final long sessionGeneration;
    public final String plateLine;
    public final String vehicleLine;
    public final String statusLine;
    public final String sourceDisplayName;
    public final long startedAtEpochMs;
    public final long updatedAtEpochMs;

    public RideHistoryEntry(
            long sessionGeneration,
            String plateLine,
            String vehicleLine,
            String statusLine,
            long updatedAtEpochMs) {
        this(sessionGeneration, plateLine, vehicleLine, statusLine, "",
                updatedAtEpochMs, updatedAtEpochMs);
    }

    public RideHistoryEntry(
            long sessionGeneration,
            String plateLine,
            String vehicleLine,
            String statusLine,
            String sourceDisplayName,
            long updatedAtEpochMs) {
        this(sessionGeneration, plateLine, vehicleLine, statusLine, sourceDisplayName,
                updatedAtEpochMs, updatedAtEpochMs);
    }

    public RideHistoryEntry(
            long sessionGeneration,
            String plateLine,
            String vehicleLine,
            String statusLine,
            String sourceDisplayName,
            long startedAtEpochMs,
            long updatedAtEpochMs) {
        this.sessionGeneration = Math.max(0L, sessionGeneration);
        this.plateLine = value(plateLine);
        this.vehicleLine = value(vehicleLine);
        this.statusLine = value(statusLine);
        this.sourceDisplayName = value(sourceDisplayName);
        this.startedAtEpochMs = Math.max(0L, startedAtEpochMs);
        this.updatedAtEpochMs = Math.max(0L, updatedAtEpochMs);
    }

    static RideHistoryEntry from(
            RideSnapshot snapshot, long startedAtEpochMs, long updatedAtEpochMs) {
        String status = snapshot.ended ? "Поездка завершена" : snapshot.statusLine();
        return new RideHistoryEntry(
                snapshot.sessionGeneration,
                snapshot.plateLine(),
                snapshot.vehicleLine(),
                status,
                snapshot.sourceDisplayName,
                startedAtEpochMs,
                updatedAtEpochMs);
    }

    public String displayLine() {
        return displayLine(TimeZone.getDefault());
    }

    String displayLine(TimeZone timeZone) {
        String time = formatStartTime(timeZone);
        String ride;
        if (vehicleLine.isEmpty()) {
            ride = plateLine;
        } else {
            ride = plateLine + " · " + vehicleLine;
        }
        if (!value(sourceDisplayName).isEmpty()) {
            ride += " · " + sourceDisplayName;
        }
        return time.isEmpty() ? ride : time + " · " + ride;
    }

    long effectiveStartedAtEpochMs() {
        return startedAtEpochMs > 0L ? startedAtEpochMs : updatedAtEpochMs;
    }

    private String formatStartTime(TimeZone timeZone) {
        long timestamp = effectiveStartedAtEpochMs();
        if (timestamp <= 0L) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);
        format.setTimeZone(timeZone == null ? TimeZone.getDefault() : timeZone);
        return format.format(new Date(timestamp));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RideHistoryEntry)) {
            return false;
        }
        RideHistoryEntry entry = (RideHistoryEntry) other;
        return sessionGeneration == entry.sessionGeneration
                && startedAtEpochMs == entry.startedAtEpochMs
                && updatedAtEpochMs == entry.updatedAtEpochMs
                && Objects.equals(plateLine, entry.plateLine)
                && Objects.equals(vehicleLine, entry.vehicleLine)
                && Objects.equals(statusLine, entry.statusLine)
                && Objects.equals(sourceDisplayName, entry.sourceDisplayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sessionGeneration, plateLine, vehicleLine, statusLine,
                sourceDisplayName, startedAtEpochMs, updatedAtEpochMs);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
