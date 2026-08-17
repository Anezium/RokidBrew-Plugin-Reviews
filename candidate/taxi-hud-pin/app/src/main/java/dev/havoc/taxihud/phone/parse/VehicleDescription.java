package dev.havoc.taxihud.phone.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VehicleDescription {
    private static final Pattern LEADING_COLOR = Pattern.compile(
            "(?iu)^(белый|чёрный|черный|серый|серебристый|синий|голубой|красный|"
                    + "зелёный|зеленый|жёлтый|желтый|оранжевый|бежевый|коричневый|"
                    + "фиолетовый|бордовый)(?:\\s+|$)(.*)$");

    public final String color;
    public final String makeModel;

    private VehicleDescription(String color, String makeModel) {
        this.color = color;
        this.makeModel = makeModel;
    }

    public static VehicleDescription split(String raw) {
        String cleaned = clean(raw);
        Matcher matcher = LEADING_COLOR.matcher(cleaned);
        if (!matcher.matches()) {
            return new VehicleDescription("", cleaned);
        }
        return new VehicleDescription(matcher.group(1), clean(matcher.group(2)));
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
