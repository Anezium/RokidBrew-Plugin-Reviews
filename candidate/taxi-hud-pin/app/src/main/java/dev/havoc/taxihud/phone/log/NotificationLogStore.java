package dev.havoc.taxihud.phone.log;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

public final class NotificationLogStore {
    private static final String PREFS_NAME = "taxi_hud_notification_log";
    private static final String EVENTS_KEY = "events";
    private final SharedPreferences preferences;

    public NotificationLogStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    public void append(NotificationLogEvent event) {
        NotificationLogBuffer buffer = new NotificationLogBuffer();
        List<NotificationLogEvent> stored = entries();
        for (int i = stored.size() - 1; i >= 0; i--) buffer.add(stored.get(i));
        buffer.add(event);
        preferences.edit().putString(EVENTS_KEY,
                NotificationLogBuffer.toJsonl(buffer.entries())).apply();
    }
    public List<NotificationLogEvent> entries() {
        return NotificationLogBuffer.fromJsonl(preferences.getString(EVENTS_KEY, ""));
    }
    public void clear() { preferences.edit().remove(EVENTS_KEY).apply(); }
}
