package dev.havoc.taxihud.phone;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.havoc.taxihud.phone.state.RideSnapshot;

public final class RideHistoryStore {
    private static final String PREFS = "taxi_hud_ride_history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_STORED = 5;
    private static final Type ENTRY_LIST = new TypeToken<List<RideHistoryEntry>>() { }.getType();

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public RideHistoryStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void record(RideSnapshot snapshot, long nowEpochMs) {
        if (snapshot == null
                || snapshot.plate.isEmpty()
                || "###".equals(snapshot.plate)
                || snapshot.sessionGeneration <= 0L) {
            return;
        }
        List<RideHistoryEntry> entries = mutableEntries();
        RideHistoryEntry existing = null;
        for (RideHistoryEntry entry : entries) {
            if (entry.sessionGeneration == snapshot.sessionGeneration) {
                existing = entry;
                break;
            }
        }
        entries.removeIf(entry -> entry.sessionGeneration == snapshot.sessionGeneration);
        long startedAtEpochMs = existing == null
                ? nowEpochMs
                : existing.effectiveStartedAtEpochMs();
        entries.add(0, RideHistoryEntry.from(snapshot, startedAtEpochMs, nowEpochMs));
        while (entries.size() > MAX_STORED) {
            entries.remove(entries.size() - 1);
        }
        preferences.edit().putString(KEY_ENTRIES, gson.toJson(entries, ENTRY_LIST)).commit();
    }

    public synchronized List<RideHistoryEntry> recent(int limit) {
        return limited(entries(), 0L, limit);
    }

    public synchronized List<RideHistoryEntry> previous(
            long currentSessionGeneration, int limit) {
        return limited(entries(), currentSessionGeneration, limit);
    }

    public synchronized void clear() {
        preferences.edit().remove(KEY_ENTRIES).commit();
    }

    private List<RideHistoryEntry> entries() {
        String json = preferences.getString(KEY_ENTRIES, "");
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<RideHistoryEntry> decoded = gson.fromJson(json, ENTRY_LIST);
            if (decoded == null) {
                return Collections.emptyList();
            }
            List<RideHistoryEntry> valid = new ArrayList<>();
            for (RideHistoryEntry entry : decoded) {
                if (entry != null
                        && entry.sessionGeneration > 0L
                        && !entry.plateLine.isEmpty()) {
                    valid.add(entry);
                }
            }
            return valid;
        } catch (JsonParseException | IllegalStateException exception) {
            return Collections.emptyList();
        }
    }

    private List<RideHistoryEntry> mutableEntries() {
        return new ArrayList<>(entries());
    }

    private static List<RideHistoryEntry> limited(
            List<RideHistoryEntry> entries, long excludedGeneration, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<RideHistoryEntry> result = new ArrayList<>();
        for (RideHistoryEntry entry : entries) {
            if (entry.sessionGeneration == excludedGeneration) {
                continue;
            }
            result.add(entry);
            if (result.size() == limit) {
                break;
            }
        }
        return Collections.unmodifiableList(result);
    }
}
