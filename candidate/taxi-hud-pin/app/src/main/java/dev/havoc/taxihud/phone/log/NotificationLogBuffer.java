package dev.havoc.taxihud.phone.log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NotificationLogBuffer {
    public static final int MAX_ENTRIES = 200;
    private static final Gson GSON = new Gson();
    private final List<NotificationLogEvent> entries = new ArrayList<>();

    public void add(NotificationLogEvent event) { entries.add(0, event); trim(); }
    public List<NotificationLogEvent> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
    public static String toJsonl(List<NotificationLogEvent> entries) {
        StringBuilder jsonl = new StringBuilder();
        int count = Math.min(entries.size(), MAX_ENTRIES);
        for (int i = 0; i < count; i++) {
            if (i > 0) jsonl.append('\n');
            jsonl.append(GSON.toJson(entries.get(i)));
        }
        return jsonl.toString();
    }
    public static List<NotificationLogEvent> fromJsonl(String jsonl) {
        List<NotificationLogEvent> entries = new ArrayList<>();
        if (jsonl == null || jsonl.isEmpty()) return entries;
        for (String line : jsonl.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                NotificationLogEvent entry = GSON.fromJson(line, NotificationLogEvent.class);
                if (entry != null) entries.add(entry);
                if (entries.size() == MAX_ENTRIES) break;
            } catch (JsonParseException | IllegalStateException ignored) { }
        }
        return entries;
    }
    private void trim() {
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }
}
