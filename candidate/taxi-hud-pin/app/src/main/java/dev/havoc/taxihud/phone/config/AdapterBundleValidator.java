package dev.havoc.taxihud.phone.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdapterBundleValidator {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_JSON_BYTES = 256 * 1024;
    private static final int MAX_ADAPTERS = 32;
    private static final int MAX_PACKAGES = 16;
    private static final int MAX_RULES = 64;
    private static final int MAX_PATTERN_CHARS = 2_048;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9._-]{2,63}");
    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Set<String> EVENTS = Set.of(
            "STARTED", "SEARCHING", "ENDED", "CANCELLED", "WAITING");
    private static final Set<String> FIELDS = Set.of(
            "PLATE", "VEHICLE", "COLOR", "MODEL", "ARRIVAL_MINUTES", "WAITING_MINUTES",
            "TRIP_DURATION");
    private static final Set<String> TRANSFORMS = Set.of(
            "TRIM", "UPPERCASE", "REMOVE_WHITESPACE", "NORMALIZE_PLATE_LETTERS",
            "NORMALIZE_RANGE", "CLEAN_VEHICLE");

    public AdapterBundle validate(AdapterBundle bundle) {
        require(bundle != null, "Adapter bundle is empty");
        require(bundle.schemaVersion == SCHEMA_VERSION, "Unsupported schemaVersion");
        require(!bundle.adapters.isEmpty(), "Bundle must contain adapters");
        require(bundle.adapters.size() <= MAX_ADAPTERS, "Too many adapters");
        Set<String> ids = new HashSet<>();
        for (NotificationAdapterConfig adapter : bundle.adapters) {
            validateAdapter(adapter);
            require(ids.add(adapter.id), "Duplicate adapter id: " + adapter.id);
        }
        return bundle;
    }

    private static void validateAdapter(NotificationAdapterConfig adapter) {
        require(adapter != null, "Adapter is null");
        require(ID.matcher(adapter.id).matches(), "Invalid adapter id: " + adapter.id);
        require(!adapter.displayName.trim().isEmpty() && adapter.displayName.length() <= 80,
                "Invalid adapter displayName: " + adapter.id);
        require(!adapter.packages.isEmpty() && adapter.packages.size() <= MAX_PACKAGES,
                "Invalid package list: " + adapter.id);
        Set<String> packages = new HashSet<>();
        for (String packageName : adapter.packages) {
            require(packageName != null && PACKAGE.matcher(packageName).matches(),
                    "Invalid package name: " + packageName);
            require(packages.add(packageName), "Duplicate package: " + packageName);
        }
        require(adapter.eventRules.size() <= MAX_RULES, "Too many event rules");
        require(!adapter.fieldRules.isEmpty() && adapter.fieldRules.size() <= MAX_RULES,
                "Invalid field rules: " + adapter.id);
        require(adapter.truncateBeforePatterns.size() <= MAX_RULES,
                "Too many truncate patterns");
        for (String pattern : adapter.truncateBeforePatterns) {
            compile(pattern, true, 0);
        }
        for (NotificationAdapterConfig.EventRule rule : adapter.eventRules) {
            require(rule != null && EVENTS.contains(upper(rule.event)),
                    "Unsupported event: " + (rule == null ? "null" : rule.event));
            compile(rule.pattern, rule.ignoreCase, 0);
        }
        Set<String> configuredFields = new HashSet<>();
        for (NotificationAdapterConfig.FieldRule rule : adapter.fieldRules) {
            require(rule != null && FIELDS.contains(upper(rule.field)),
                    "Unsupported field: " + (rule == null ? "null" : rule.field));
            compile(rule.pattern, rule.ignoreCase, rule.group);
            configuredFields.add(upper(rule.field));
            for (String transform : rule.transforms) {
                require(TRANSFORMS.contains(upper(transform)),
                        "Unsupported transform: " + transform);
            }
        }
        require(!adapter.activeWhenAny.isEmpty(), "activeWhenAny is required");
        for (String field : adapter.activeWhenAny) {
            require(FIELDS.contains(upper(field)) && configuredFields.contains(upper(field)),
                    "activeWhenAny references unknown field: " + field);
        }
        for (String field : adapter.requiredWithPlate) {
            require(FIELDS.contains(upper(field)) && configuredFields.contains(upper(field)),
                    "requiredWithPlate references unknown field: " + field);
        }
        require(adapter.pinTtlMs >= 1_000L && adapter.pinTtlMs <= 86_400_000L,
                "pinTtlMs is outside Nexus limits");
    }

    private static void compile(String pattern, boolean ignoreCase, int group) {
        require(pattern != null && !pattern.isEmpty() && pattern.length() <= MAX_PATTERN_CHARS,
                "Invalid regex length");
        require(group >= 0, "Capture group must be non-negative");
        try {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            Pattern compiled = Pattern.compile(pattern, flags);
            require(group <= compiled.matcher("").groupCount(),
                    "Capture group " + group + " does not exist");
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Invalid regex: " + exception.getDescription());
        }
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
