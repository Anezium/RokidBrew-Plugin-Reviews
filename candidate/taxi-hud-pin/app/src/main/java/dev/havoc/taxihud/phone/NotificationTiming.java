package dev.havoc.taxihud.phone;

/** Standard Android notification timing/progress metadata not exposed as text extras. */
public final class NotificationTiming {
    public static final NotificationTiming NONE =
            new NotificationTiming(0L, false, false, 0, 0, false);

    public final long whenEpochMs;
    public final boolean showChronometer;
    public final boolean chronometerCountDown;
    public final int progress;
    public final int progressMax;
    public final boolean progressIndeterminate;

    public NotificationTiming(
            long whenEpochMs,
            boolean showChronometer,
            boolean chronometerCountDown,
            int progress,
            int progressMax,
            boolean progressIndeterminate) {
        this.whenEpochMs = Math.max(0L, whenEpochMs);
        this.showChronometer = showChronometer;
        this.chronometerCountDown = chronometerCountDown;
        this.progress = Math.max(0, progress);
        this.progressMax = Math.max(0, progressMax);
        this.progressIndeterminate = progressIndeterminate;
    }

    public long countdownDeadline(long eventTimestampMs) {
        // A future chronometer base is necessarily a countdown even when an app omits
        // EXTRA_CHRONOMETER_COUNT_DOWN (seen with some custom notification styles).
        return showChronometer && whenEpochMs > eventTimestampMs
                ? whenEpochMs
                : 0L;
    }
}
