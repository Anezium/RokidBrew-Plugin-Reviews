package dev.havoc.taxihud.phone.config;

import java.util.Collections;
import java.util.List;

public final class AdapterBundle {
    public final int schemaVersion;
    public final Metadata metadata;
    public final List<NotificationAdapterConfig> adapters;

    @SuppressWarnings("unused")
    private AdapterBundle() {
        this(SCHEMA_DEFAULT, null, Collections.emptyList());
    }

    public AdapterBundle(
            int schemaVersion,
            Metadata metadata,
            List<NotificationAdapterConfig> adapters) {
        this.schemaVersion = schemaVersion;
        this.metadata = metadata == null ? new Metadata("", "", "", 1) : metadata;
        this.adapters = adapters == null ? Collections.emptyList() : adapters;
    }

    public static final class Metadata {
        public final String id;
        public final String displayName;
        public final String author;
        public final int version;

        @SuppressWarnings("unused")
        private Metadata() {
            this("", "", "", 1);
        }

        public Metadata(String id, String displayName, String author, int version) {
            this.id = value(id);
            this.displayName = value(displayName);
            this.author = value(author);
            this.version = version;
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static final int SCHEMA_DEFAULT = 1;
}
