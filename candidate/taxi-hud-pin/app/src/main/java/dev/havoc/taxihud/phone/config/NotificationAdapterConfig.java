package dev.havoc.taxihud.phone.config;

import java.util.Collections;
import java.util.List;

public final class NotificationAdapterConfig {
    public final String id;
    public final String displayName;
    public final boolean enabled;
    public final List<String> packages;
    public final List<EventRule> eventRules;
    public final List<FieldRule> fieldRules;
    public final List<String> truncateBeforePatterns;
    public final List<String> activeWhenAny;
    public final List<String> requiredWithPlate;
    public final long pinTtlMs;
    public final boolean imported;

    @SuppressWarnings("unused")
    private NotificationAdapterConfig() {
        this("", "", true, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 30L * 60L * 1_000L, false);
    }

    public NotificationAdapterConfig(
            String id,
            String displayName,
            boolean enabled,
            List<String> packages,
            List<EventRule> eventRules,
            List<FieldRule> fieldRules,
            List<String> truncateBeforePatterns,
            List<String> activeWhenAny,
            List<String> requiredWithPlate,
            long pinTtlMs) {
        this(id, displayName, enabled, packages, eventRules, fieldRules, truncateBeforePatterns,
                activeWhenAny, requiredWithPlate, pinTtlMs, false);
    }

    private NotificationAdapterConfig(
            String id,
            String displayName,
            boolean enabled,
            List<String> packages,
            List<EventRule> eventRules,
            List<FieldRule> fieldRules,
            List<String> truncateBeforePatterns,
            List<String> activeWhenAny,
            List<String> requiredWithPlate,
            long pinTtlMs,
            boolean imported) {
        this.id = value(id);
        this.displayName = value(displayName);
        this.enabled = enabled;
        this.packages = stable(packages);
        this.eventRules = stable(eventRules);
        this.fieldRules = stable(fieldRules);
        this.truncateBeforePatterns = stable(truncateBeforePatterns);
        this.activeWhenAny = stable(activeWhenAny);
        this.requiredWithPlate = stable(requiredWithPlate);
        this.pinTtlMs = pinTtlMs;
        this.imported = imported;
    }

    public boolean matchesPackage(String packageName) {
        return enabled && packages.contains(packageName);
    }

    public NotificationAdapterConfig withRuntimeState(boolean enabled, boolean imported) {
        return new NotificationAdapterConfig(
                id, displayName, enabled, packages, eventRules, fieldRules, truncateBeforePatterns,
                activeWhenAny, requiredWithPlate, pinTtlMs, imported);
    }

    public static final class EventRule {
        public final String event;
        public final String pattern;
        public final boolean ignoreCase;

        @SuppressWarnings("unused")
        private EventRule() {
            this("", "", true);
        }

        public EventRule(String event, String pattern, boolean ignoreCase) {
            this.event = value(event);
            this.pattern = value(pattern);
            this.ignoreCase = ignoreCase;
        }
    }

    public static final class FieldRule {
        public final String field;
        public final String pattern;
        public final int group;
        public final boolean ignoreCase;
        public final List<String> transforms;

        @SuppressWarnings("unused")
        private FieldRule() {
            this("", "", 1, true, Collections.emptyList());
        }

        public FieldRule(
                String field,
                String pattern,
                int group,
                boolean ignoreCase,
                List<String> transforms) {
            this.field = value(field);
            this.pattern = value(pattern);
            this.group = group;
            this.ignoreCase = ignoreCase;
            this.transforms = stable(transforms);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> stable(List<T> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
    }
}
