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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdapterRepository {
    public static final String BUILTIN_ASSET = "adapters/builtin_notification_adapters.json";
    private static final String PREFS = "notification_adapter_configs";
    private static final String KEY_IMPORTED = "imported_bundle";
    private static final String KEY_ENABLED = "enabled_overrides";
    private static final String KEY_ALLOWED_PACKAGES = "allowed_package_overrides";
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
        if (!isPackageAllowed(packageName)) {
            return false;
        }
        for (NotificationAdapterConfig adapter : enabledAdapters()) {
            if (adapter.matchesPackage(packageName)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int importJson(String rawJson) {
        AdapterImportPreview preview = previewImportJson(rawJson);
        return importJson(rawJson, new LinkedHashSet<>(preview.packages));
    }

    public synchronized AdapterImportPreview previewImportJson(String rawJson) {
        AdapterBundle incoming = decodeImported(rawJson);
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : incoming.adapters) {
            packages.addAll(adapter.packages);
        }
        return new AdapterImportPreview(incoming.adapters.size(), new ArrayList<>(packages));
    }

    public synchronized int importJson(String rawJson, Set<String> allowedPackages) {
        AdapterBundle incoming = decodeImported(rawJson);
        LinkedHashSet<String> requestedPackages = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : incoming.adapters) {
            requestedPackages.addAll(adapter.packages);
        }
        Set<String> selected = allowedPackages == null ? Set.of() : allowedPackages;
        require(requestedPackages.containsAll(selected),
                "Selected packages are not declared by the imported bundle");

        Map<String, Boolean> packageOverrides = packageOverrides();
        for (String packageName : requestedPackages) {
            packageOverrides.put(packageName, selected.contains(packageName));
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
        validator.validateImported(stored);
        boolean saved = preferences.edit()
                .putString(KEY_IMPORTED, gson.toJson(stored))
                .putString(KEY_ALLOWED_PACKAGES, gson.toJson(packageOverrides, ENABLED_MAP))
                .commit();
        if (!saved) {
            throw new IllegalStateException("Could not save imported adapters");
        }
        return incoming.adapters.size();
    }

    private AdapterBundle decodeImported(String rawJson) {
        if (rawJson == null
                || rawJson.getBytes(StandardCharsets.UTF_8).length > AdapterBundleValidator.MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Adapter JSON is empty or too large");
        }
        final AdapterBundle incoming;
        try {
            incoming = validator.validateImported(gson.fromJson(rawJson, AdapterBundle.class));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Adapter JSON is malformed", exception);
        }
        return incoming;
    }

    public synchronized List<String> configuredPackages() {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : adapters()) {
            packages.addAll(adapter.packages);
        }
        return new ArrayList<>(packages);
    }

    public synchronized boolean isPackageAllowed(String packageName) {
        Map<String, Boolean> overrides = packageOverrides();
        return !overrides.containsKey(packageName)
                || Boolean.TRUE.equals(overrides.get(packageName));
    }

    public synchronized void setPackageAllowed(String packageName, boolean allowed) {
        require(configuredPackages().contains(packageName), "Unknown package: " + packageName);
        Map<String, Boolean> overrides = packageOverrides();
        overrides.put(packageName, allowed);
        preferences.edit()
                .putString(KEY_ALLOWED_PACKAGES, gson.toJson(overrides, ENABLED_MAP))
                .commit();
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
        AdapterBundle imported = imported();
        for (NotificationAdapterConfig adapter : imported.adapters) {
            overrides.remove(adapter.id);
        }
        Set<String> builtInPackages = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : builtins().adapters) {
            builtInPackages.addAll(adapter.packages);
        }
        Map<String, Boolean> packageOverrides = packageOverrides();
        for (NotificationAdapterConfig adapter : imported.adapters) {
            for (String packageName : adapter.packages) {
                if (!builtInPackages.contains(packageName)) {
                    packageOverrides.remove(packageName);
                }
            }
        }
        SharedPreferences.Editor edit = preferences.edit().remove(KEY_IMPORTED);
        if (overrides.isEmpty()) {
            edit.remove(KEY_ENABLED);
        } else {
            edit.putString(KEY_ENABLED, gson.toJson(overrides, ENABLED_MAP));
        }
        if (packageOverrides.isEmpty()) {
            edit.remove(KEY_ALLOWED_PACKAGES);
        } else {
            edit.putString(KEY_ALLOWED_PACKAGES, gson.toJson(packageOverrides, ENABLED_MAP));
        }
        edit.commit();
    }

    public synchronized PortableState exportPortableState() {
        String importedBundle = preferences.getString(KEY_IMPORTED, "");
        if (importedBundle == null) {
            importedBundle = "";
        }
        AdapterBundle imported = importedBundle.trim().isEmpty()
                ? emptyImported()
                : decodeImported(importedBundle);
        LinkedHashSet<String> adapterIds = new LinkedHashSet<>();
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : builtins().adapters) {
            adapterIds.add(adapter.id);
            packages.addAll(adapter.packages);
        }
        for (NotificationAdapterConfig adapter : imported.adapters) {
            adapterIds.add(adapter.id);
            packages.addAll(adapter.packages);
        }
        return new PortableState(
                importedBundle,
                filteredOverrides(enabledOverrides(), adapterIds),
                filteredOverrides(packageOverrides(), packages));
    }

    public synchronized void importPortableState(PortableState state) {
        require(state != null, "Adapter settings are missing");
        String rawImported = state.importedBundle == null ? "" : state.importedBundle;
        AdapterBundle imported = rawImported.trim().isEmpty()
                ? emptyImported()
                : decodeImported(rawImported);
        Map<String, Boolean> enabled = validatedOverrides(
                state.enabledOverrides, adapterIds(builtins(), imported), "adapter");
        Map<String, Boolean> allowed = validatedOverrides(
                state.allowedPackageOverrides, packageNames(builtins(), imported), "package");
        SharedPreferences.Editor edit = preferences.edit();
        if (rawImported.trim().isEmpty()) {
            edit.remove(KEY_IMPORTED);
        } else {
            edit.putString(KEY_IMPORTED, gson.toJson(imported));
        }
        if (enabled.isEmpty()) {
            edit.remove(KEY_ENABLED);
        } else {
            edit.putString(KEY_ENABLED, gson.toJson(enabled, ENABLED_MAP));
        }
        if (allowed.isEmpty()) {
            edit.remove(KEY_ALLOWED_PACKAGES);
        } else {
            edit.putString(KEY_ALLOWED_PACKAGES, gson.toJson(allowed, ENABLED_MAP));
        }
        if (!edit.commit()) {
            throw new IllegalStateException("Could not restore adapter settings");
        }
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
            return validator.validateImported(gson.fromJson(raw, AdapterBundle.class));
        } catch (IllegalArgumentException | JsonParseException exception) {
            return emptyImported();
        }
    }

    private Map<String, Boolean> enabledOverrides() {
        return booleanOverrides(KEY_ENABLED);
    }

    private Map<String, Boolean> packageOverrides() {
        return booleanOverrides(KEY_ALLOWED_PACKAGES);
    }

    private Map<String, Boolean> booleanOverrides(String key) {
        String raw = preferences.getString(key, "");
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

    private static Map<String, Boolean> filteredOverrides(
            Map<String, Boolean> source, Set<String> allowedKeys) {
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : source.entrySet()) {
            if (allowedKeys.contains(entry.getKey()) && entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Boolean> validatedOverrides(
            Map<String, Boolean> source, Set<String> allowedKeys, String kind) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        require(source.size() <= 256, "Too many " + kind + " overrides");
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : source.entrySet()) {
            require(entry.getKey() != null && entry.getKey().length() <= 255
                            && allowedKeys.contains(entry.getKey()),
                    "Unknown " + kind + ": " + entry.getKey());
            require(entry.getValue() != null, "Invalid " + kind + " override");
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Set<String> adapterIds(AdapterBundle first, AdapterBundle second) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : first.adapters) {
            result.add(adapter.id);
        }
        for (NotificationAdapterConfig adapter : second.adapters) {
            result.add(adapter.id);
        }
        return result;
    }

    private static Set<String> packageNames(AdapterBundle first, AdapterBundle second) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (NotificationAdapterConfig adapter : first.adapters) {
            result.addAll(adapter.packages);
        }
        for (NotificationAdapterConfig adapter : second.adapters) {
            result.addAll(adapter.packages);
        }
        return result;
    }

    public static final class PortableState {
        public final String importedBundle;
        public final Map<String, Boolean> enabledOverrides;
        public final Map<String, Boolean> allowedPackageOverrides;

        public PortableState(String importedBundle, Map<String, Boolean> enabledOverrides,
                Map<String, Boolean> allowedPackageOverrides) {
            this.importedBundle = importedBundle == null ? "" : importedBundle;
            this.enabledOverrides = enabledOverrides == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(enabledOverrides);
            this.allowedPackageOverrides = allowedPackageOverrides == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(allowedPackageOverrides);
        }
    }

    private static AdapterBundle emptyImported() {
        return new AdapterBundle(
                AdapterBundleValidator.SCHEMA_VERSION,
                new AdapterBundle.Metadata("local.imported", "Imported adapters", "local", 1),
                List.of());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
