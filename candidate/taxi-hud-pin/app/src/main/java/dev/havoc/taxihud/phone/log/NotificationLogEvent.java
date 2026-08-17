package dev.havoc.taxihud.phone.log;

import java.util.List;

import dev.havoc.taxihud.phone.NotificationTiming;

public final class NotificationLogEvent {
    public final long timestampMs;
    public final String packageName;
    public final String adapterId;
    public final String adapterDisplayName;
    public final String eventType;
    public final String key;
    public final int removalReason;
    public final String title;
    public final String text;
    public final String bigText;
    public final List<String> textLines;
    public final long whenEpochMs;
    public final boolean showChronometer;
    public final boolean chronometerCountDown;
    public final int progress;
    public final int progressMax;
    public final boolean progressIndeterminate;
    public final NotificationParserResult parserResult;
    public final String decision;

    public NotificationLogEvent(long timestampMs, String packageName, String adapterId,
            String adapterDisplayName, String eventType, String key, int removalReason,
            String title, String text, String bigText, List<String> textLines,
            NotificationParserResult parserResult, String decision) {
        this(timestampMs, packageName, adapterId, adapterDisplayName, eventType, key,
                removalReason, title, text, bigText, textLines, NotificationTiming.NONE,
                parserResult, decision);
    }

    public NotificationLogEvent(long timestampMs, String packageName, String adapterId,
            String adapterDisplayName, String eventType, String key, int removalReason,
            String title, String text, String bigText, List<String> textLines,
            NotificationTiming timing,
            NotificationParserResult parserResult, String decision) {
        this.timestampMs = timestampMs;
        this.packageName = value(packageName);
        this.adapterId = value(adapterId);
        this.adapterDisplayName = value(adapterDisplayName);
        this.eventType = value(eventType);
        this.key = value(key);
        this.removalReason = removalReason;
        this.title = value(title);
        this.text = value(text);
        this.bigText = value(bigText);
        this.textLines = textLines;
        NotificationTiming stableTiming = timing == null ? NotificationTiming.NONE : timing;
        this.whenEpochMs = stableTiming.whenEpochMs;
        this.showChronometer = stableTiming.showChronometer;
        this.chronometerCountDown = stableTiming.chronometerCountDown;
        this.progress = stableTiming.progress;
        this.progressMax = stableTiming.progressMax;
        this.progressIndeterminate = stableTiming.progressIndeterminate;
        this.parserResult = parserResult == null ? NotificationParserResult.empty() : parserResult;
        this.decision = value(decision);
    }

    private static String value(String value) { return value == null ? "" : value; }
}
