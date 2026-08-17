package dev.havoc.taxihud.phone.parse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.havoc.taxihud.phone.NotificationTiming;
import dev.havoc.taxihud.phone.config.NotificationAdapterConfig;

public final class NotificationAdapterEngine {
    static final int MAX_MATCH_BODY_CHARS = 8_192;

    public AdapterParseResult parse(
            String packageName,
            String title,
            String text,
            String bigText,
            List<String> textLines,
            List<NotificationAdapterConfig> adapters) {
        return parse(packageName, title, text, bigText, textLines, adapters,
                0L, NotificationTiming.NONE);
    }

    public AdapterParseResult parse(
            String packageName,
            String title,
            String text,
            String bigText,
            List<String> textLines,
            List<NotificationAdapterConfig> adapters,
            long eventTimestampMs,
            NotificationTiming timing) {
        String body = joinNotificationText(title, text, bigText, textLines);
        if (adapters == null) {
            return AdapterParseResult.noMatch();
        }
        for (NotificationAdapterConfig adapter : adapters) {
            if (!adapter.matchesPackage(packageName)) {
                continue;
            }
            TaxiUpdate update = parseAdapter(
                    body, adapter, eventTimestampMs,
                    timing == null ? NotificationTiming.NONE : timing);
            if (update.kind != TaxiUpdate.Kind.NO_MATCH) {
                return AdapterParseResult.matched(adapter, update.withSource(
                        adapter.id, adapter.displayName, packageName, adapter.pinTtlMs));
            }
        }
        return AdapterParseResult.noMatch();
    }

    private TaxiUpdate parseAdapter(
            String body,
            NotificationAdapterConfig adapter,
            long eventTimestampMs,
            NotificationTiming timing) {
        String event = firstEvent(body, adapter.eventRules, adapter.imported);
        if ("STARTED".equals(event)) {
            return TaxiUpdate.tripStarted(timing.countdownDeadline(eventTimestampMs));
        }
        if ("CANCELLED".equals(event)) {
            return TaxiUpdate.lifecycle(TaxiUpdate.Kind.CANCELLED);
        }
        if ("ENDED".equals(event)) {
            return TaxiUpdate.lifecycle(TaxiUpdate.Kind.ENDED);
        }
        if ("SEARCHING".equals(event)) {
            return TaxiUpdate.lifecycle(TaxiUpdate.Kind.SEARCHING);
        }

        Map<String, String> values = extractFields(
                truncate(body, adapter.truncateBeforePatterns, adapter.imported),
                adapter.fieldRules,
                adapter.imported);
        long countdownDeadline = timing.countdownDeadline(eventTimestampMs);
        if (countdownDeadline > 0L) {
            return TaxiUpdate.tripProgress(countdownDeadline);
        }
        long tripDurationMinutes = durationMinutes(value(values.get("TRIP_DURATION")));
        if (tripDurationMinutes > 0L && eventTimestampMs > 0L) {
            return TaxiUpdate.tripProgress(
                    eventTimestampMs + tripDurationMinutes * 60_000L);
        }
        String plate = value(values.get("PLATE"));
        String vehicleText = value(values.get("VEHICLE"));
        VehicleDescription vehicle = VehicleDescription.split(vehicleText);
        String color = prefer(values.get("COLOR"), vehicle.color);
        String model = prefer(values.get("MODEL"), vehicle.makeModel);
        String arrival = value(values.get("ARRIVAL_MINUTES"));
        String waiting = value(values.get("WAITING_MINUTES"));

        if ("WAITING".equals(event)) {
            return plate.isEmpty() || model.isEmpty()
                    ? TaxiUpdate.waiting(waiting)
                    : TaxiUpdate.waiting(plate, color, model, waiting);
        }
        if (!hasAny(values, adapter.activeWhenAny)) {
            return TaxiUpdate.noMatch();
        }
        if (!plate.isEmpty() && !hasAll(values, adapter.requiredWithPlate)) {
            return TaxiUpdate.noMatch();
        }
        if (plate.isEmpty()) {
            return arrival.isEmpty() ? TaxiUpdate.noMatch() : TaxiUpdate.arrival(arrival);
        }
        return TaxiUpdate.active(plate, color, model, arrival);
    }

    private static String firstEvent(
            String body, List<NotificationAdapterConfig.EventRule> rules, boolean imported) {
        for (NotificationAdapterConfig.EventRule rule : rules) {
            if (find(rule.pattern, rule.ignoreCase, body, imported) != null) {
                return upper(rule.event);
            }
        }
        return "";
    }

    private static Map<String, String> extractFields(
            String body,
            List<NotificationAdapterConfig.FieldRule> rules,
            boolean imported) {
        Map<String, String> values = new LinkedHashMap<>();
        for (NotificationAdapterConfig.FieldRule rule : rules) {
            String field = upper(rule.field);
            if (values.containsKey(field)) {
                continue;
            }
            FoundMatch match = find(rule.pattern, rule.ignoreCase, body, imported);
            if (match == null) {
                continue;
            }
            String raw = match.group(rule.group);
            String transformed = transform(raw, rule.transforms);
            if (!transformed.isEmpty()) {
                values.put(field, transformed);
            }
        }
        return values;
    }

    private static String transform(String raw, List<String> transforms) {
        String result = value(raw);
        for (String transform : transforms) {
            switch (upper(transform)) {
                case "TRIM":
                    result = result.trim();
                    break;
                case "UPPERCASE":
                    result = result.toUpperCase(Locale.ROOT);
                    break;
                case "REMOVE_WHITESPACE":
                    result = result.replaceAll("\\s+", "");
                    break;
                case "NORMALIZE_PLATE_LETTERS":
                    result = normalizePlateLetters(result);
                    break;
                case "NORMALIZE_RANGE":
                    result = result.replaceAll("\\s*[–—-]\\s*", "–").trim();
                    break;
                case "CLEAN_VEHICLE":
                    result = cleanVehicle(result);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported transform: " + transform);
            }
        }
        return result;
    }

    private static String truncate(String body, List<String> patterns, boolean imported) {
        int end = body.length();
        for (String expression : patterns) {
            FoundMatch match = find(expression, true, body, imported);
            if (match != null) {
                end = Math.min(end, match.start());
            }
        }
        return body.substring(0, end);
    }

    private static boolean hasAny(Map<String, String> values, List<String> fields) {
        for (String field : fields) {
            if (!value(values.get(upper(field))).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAll(Map<String, String> values, List<String> fields) {
        for (String field : fields) {
            if (value(values.get(upper(field))).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Pattern pattern(String expression, boolean ignoreCase) {
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        return Pattern.compile(expression, flags);
    }

    private static FoundMatch find(
            String expression, boolean ignoreCase, String input, boolean imported) {
        if (imported) {
            int flags = ignoreCase ? com.google.re2j.Pattern.CASE_INSENSITIVE : 0;
            com.google.re2j.Matcher matcher =
                    com.google.re2j.Pattern.compile(expression, flags).matcher(input);
            if (!matcher.find()) {
                return null;
            }
            return new FoundMatch() {
                @Override
                public String group(int group) {
                    return matcher.group(group);
                }

                @Override
                public int start() {
                    return matcher.start();
                }
            };
        }
        Matcher matcher = pattern(expression, ignoreCase).matcher(input);
        if (!matcher.find()) {
            return null;
        }
        return new FoundMatch() {
            @Override
            public String group(int group) {
                return matcher.group(group);
            }

            @Override
            public int start() {
                return matcher.start();
            }
        };
    }

    private static String cleanVehicle(String raw) {
        String cleaned = value(raw)
                .replaceAll("(?iu)(?:^|\\s)\\d+\\s*мин(?:ут)?\\.?(?=\\s|$)", " ")
                .replaceAll("(?iu)(?:^|\\s)(?:к|вам|вас|такси|машина|авто|номер|госномер|будет|уже|"
                        + "жд[её]т|ожидает|назначен[ао]?|через|мин(?:ут)?\\.)(?=\\s|$)", " ")
                .replaceAll("(?iu)[,.:;()№]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("(?iu)\\s+(?:приедет|подъедет|едет).*$", "").trim();
        cleaned = cleaned.replaceAll("(?iu)\\s+(?:номер|госномер).*$", "").trim();
        return cleaned.length() >= 3 && containsLetter(cleaned) ? cleaned : "";
    }

    private static String normalizePlateLetters(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            switch (raw.charAt(i)) {
                case 'A': builder.append('А'); break;
                case 'B': builder.append('В'); break;
                case 'E': builder.append('Е'); break;
                case 'K': builder.append('К'); break;
                case 'M': builder.append('М'); break;
                case 'H': builder.append('Н'); break;
                case 'O': builder.append('О'); break;
                case 'P': builder.append('Р'); break;
                case 'C': builder.append('С'); break;
                case 'T': builder.append('Т'); break;
                case 'Y': builder.append('У'); break;
                case 'X': builder.append('Х'); break;
                default: builder.append(raw.charAt(i)); break;
            }
        }
        return builder.toString();
    }

    private static boolean containsLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String joinNotificationText(
            String title, String text, String bigText, List<String> textLines) {
        StringBuilder builder = new StringBuilder();
        append(builder, title);
        append(builder, text);
        append(builder, bigText);
        if (textLines != null) {
            for (String line : textLines) {
                append(builder, line);
            }
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || builder.length() >= MAX_MATCH_BODY_CHARS) {
            return;
        }
        int separatorChars = builder.length() > 0 ? 1 : 0;
        int remaining = MAX_MATCH_BODY_CHARS - builder.length() - separatorChars;
        if (remaining <= 0) {
            return;
        }
        String bounded = value.length() > remaining ? value.substring(0, remaining) : value;
        bounded = bounded.trim();
        if (bounded.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(bounded);
    }

    private static String prefer(String preferred, String fallback) {
        return value(preferred).isEmpty() ? value(fallback) : value(preferred);
    }

    private static long durationMinutes(String value) {
        if (value.isEmpty()) {
            return 0L;
        }
        Matcher hours = pattern("(\\d+)\\s*(?:ч(?:ас(?:а|ов)?)?\\.?|h(?:ours?)?)", true)
                .matcher(value);
        Matcher minutes = pattern("(\\d+)\\s*(?:мин(?:ут[ыа]?)?\\.?|m(?:in(?:utes?)?)?\\.?)", true)
                .matcher(value);
        long total = 0L;
        if (hours.find()) {
            total += Long.parseLong(hours.group(1)) * 60L;
        }
        if (minutes.find()) {
            total += Long.parseLong(minutes.group(1));
        }
        return total;
    }

    private static String upper(String value) {
        return value(value).trim().toUpperCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private interface FoundMatch {
        String group(int group);

        int start();
    }
}
