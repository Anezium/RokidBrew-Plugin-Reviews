package dev.havoc.taxihud.phone.backup;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import dev.havoc.taxihud.phone.config.AdapterRepository;

public final class TaxiSettingsBackup {
    public static final String APP_ID = "com.havoc.rokid.plugin.taxihudpin";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    public final int schemaVersion;
    public final String appId;
    public final long exportedAt;
    public final AdapterRepository.PortableState adapters;
    public final boolean autoTripPin;
    public final String languageTag;

    public TaxiSettingsBackup(AdapterRepository.PortableState adapters, boolean autoTripPin,
            String languageTag) {
        this(SCHEMA_VERSION, APP_ID, System.currentTimeMillis(), adapters, autoTripPin,
                languageTag);
    }

    TaxiSettingsBackup(int schemaVersion, String appId, long exportedAt,
            AdapterRepository.PortableState adapters, boolean autoTripPin, String languageTag) {
        this.schemaVersion = schemaVersion;
        this.appId = appId;
        this.exportedAt = exportedAt;
        this.adapters = adapters;
        this.autoTripPin = autoTripPin;
        this.languageTag = languageTag;
    }

    public String encode() {
        validate();
        return GSON.toJson(this);
    }

    public static TaxiSettingsBackup decode(String value) {
        try {
            TaxiSettingsBackup backup = GSON.fromJson(value, TaxiSettingsBackup.class);
            if (backup == null) {
                throw new IllegalArgumentException("Backup payload is empty");
            }
            backup.validate();
            return backup;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Backup payload is malformed", exception);
        }
    }

    private void validate() {
        require(schemaVersion == SCHEMA_VERSION, "Unsupported settings schema");
        require(APP_ID.equals(appId), "Settings belong to another app");
        require(exportedAt > 0, "Invalid export timestamp");
        require(adapters != null, "Adapter settings are missing");
        require("".equals(languageTag) || "en".equals(languageTag)
                        || "ru".equals(languageTag),
                "Unsupported language");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
