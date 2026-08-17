package dev.havoc.taxihud.phone.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdapterRepository {
    public static final String BUILTIN_ASSET = "adapters/builtin_notification_adapters.json";
    private static final String PREFS = "notification_adapter_configs";
    private static final String KEY_IMPORTED = "imported_bundle";
    private static final String KEY_ENABLED = "enabled_overrides";
    private static final Type ENABLED_MAP = new TypeToken<Map<String, Boolean>>() { }.getType();

    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final AdapterBundleValidator validator = new AdapterBundleValidator();

    public AdapterRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<NotificationAdapterConfig> adapters() {
        LinkedHashMap<String, NotificationAdapterConfig> effective = new LinkedHashMap<>();
        for (NotificationAdapterConfig adapter : builtins().adapters) {
            effective.put(adapter.id, adapter.withRuntimeState(adapter.enabled, false));
        }
        AdapterBundle imported = imported();
        for (NotificationAdapterConfig adapter : imported.adapters) {
            effective.put(adapter.id, adapter.withRuntimeState(adapter.enabled, true));
        }
        Map<String, Boolean> overrides = enabledOverrides();
        List<NotificationAdapterConfig> result = new ArrayList<>();
        for (NotificationAdapterConfig adapter : effective.values()) {
            boolean enabled = overrides.containsKey(adapter.id)
                    ? Boolean.TRUE.equals(overrides.get(adapter.id))
                    : adapter.enabled;
            result.add(adapter.withRuntimeState(enabled, adapter.imported));
        }
        return result;
    }

    public synchronized List<NotificationAdapterConfig> enabledAdapters() {
        List<NotificationAdapterConfig> enabled = new ArrayList<>();
        for (NotificationAdapterConfig adapter : adapters()) {
            if (adapter.enabled) {
                enabled.add(adapter);
            }
        }
        return enabled;
    }

    public synchronized boolean handlesPackage(String packageName) {
        for (NotificationAdapterConfig adapter : enabledAdapters()) {
            if (adapter.matchesPackage(packageName)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int importJson(String rawJson) {
        if (rawJson == null
                || rawJson.getBytes(StandardCharsets.UTF_8).length > AdapterBundleValidator.MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Adapter JSON is empty or too large");
        }
        final AdapterBundle incoming;
        try {
            incoming = validator.validate(gson.fromJson(rawJson, AdapterBundle.class));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Adapter JSON is malformed", exception);
        }
        LinkedHashMap<String, NotificationAdapterConfig> merged = new LinkedHashMap<>();
        for (NotificationAdapterConfig adapter : imported().adapters) {
            merged.put(adapter.id, adapter);
        }
        for (NotificationAdapterConfig adapter : incoming.adapters) {
            merged.put(adapter.id, adapter);
        }
        AdapterBundle stored = new AdapterBundle(
                AdapterBundleValidator.SCHEMA_VERSION,
                new AdapterBundle.Metadata("local.imported", "Imported adapters", "local", 1),
                new ArrayList<>(merged.values()));
        validator.validate(stored);
        preferences.edit().putString(KEY_IMPORTED, gson.toJson(stored)).commit();
        return incoming.adapters.size();
    }

    public synchronized void setEnabled(String adapterId, boolean enabled) {
        boolean known = false;
        for (NotificationAdapterConfig adapter : adapters()) {
            if (adapter.id.equals(adapterId)) {
                known = true;
                break;
            }
        }
        if (!known) {
            throw new IllegalArgumentException("Unknown adapter: " + adapterId);
        }
        Map<String, Boolean> overrides = enabledOverrides();
        overrides.put(adapterId, enabled);
        preferences.edit().putString(KEY_ENABLED, gson.toJson(overrides, ENABLED_MAP)).commit();
    }

    public synchronized void resetImported() {
        Map<String, Boolean> overrides = enabledOverrides();
        for (NotificationAdapterConfig adapter : imported().adapters) {
            overrides.remove(adapter.id);
        }
        SharedPreferences.Editor edit = preferences.edit().remove(KEY_IMPORTED);
        if (overrides.isEmpty()) {
            edit.remove(KEY_ENABLED);
        } else {
            edit.putString(KEY_ENABLED, gson.toJson(overrides, ENABLED_MAP));
        }
        edit.commit();
    }

    private AdapterBundle builtins() {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(BUILTIN_ASSET), StandardCharsets.UTF_8)) {
            return validator.validate(gson.fromJson(reader, AdapterBundle.class));
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("Built-in adapter bundle is invalid", exception);
        }
    }

    private AdapterBundle imported() {
        String raw = preferences.getString(KEY_IMPORTED, "");
        if (raw == null || raw.trim().isEmpty()) {
            return emptyImported();
        }
        try {
            return validator.validate(gson.fromJson(raw, AdapterBundle.class));
        } catch (IllegalArgumentException | JsonParseException exception) {
            return emptyImported();
        }
    }

    private Map<String, Boolean> enabledOverrides() {
        String raw = preferences.getString(KEY_ENABLED, "");
        if (raw == null || raw.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Boolean> decoded = gson.fromJson(raw, ENABLED_MAP);
            return decoded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(decoded);
        } catch (JsonParseException exception) {
            return new LinkedHashMap<>();
        }
    }

    private static AdapterBundle emptyImported() {
        return new AdapterBundle(
                AdapterBundleValidator.SCHEMA_VERSION,
                new AdapterBundle.Metadata("local.imported", "Imported adapters", "local", 1),
                List.of());
    }
}
