package dev.havoc.taxihud.phone.state;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlateFormatter {
    static final String UNKNOWN = "###";
    private static final Pattern PLATE_PATTERN = Pattern.compile(
            "^([АВЕКМНОРСТУХ])(\\d{3})([АВЕКМНОРСТУХ]{2})(\\d{2,3})$");
    private static final char[] SUPERSCRIPT_DIGITS = {
            '⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹'
    };

    private PlateFormatter() {
    }

    public static String normalize(String plate) {
        if (plate == null) {
            return "";
        }
        String compact = plate.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(compact.length());
        for (int i = 0; i < compact.length(); i++) {
            switch (compact.charAt(i)) {
                case 'A': normalized.append('А'); break;
                case 'B': normalized.append('В'); break;
                case 'E': normalized.append('Е'); break;
                case 'K': normalized.append('К'); break;
                case 'M': normalized.append('М'); break;
                case 'H': normalized.append('Н'); break;
                case 'O': normalized.append('О'); break;
                case 'P': normalized.append('Р'); break;
                case 'C': normalized.append('С'); break;
                case 'T': normalized.append('Т'); break;
                case 'Y': normalized.append('У'); break;
                case 'X': normalized.append('Х'); break;
                default: normalized.append(compact.charAt(i)); break;
            }
        }
        return normalized.toString();
    }

    public static String display(String plate) {
        if (plate == null) {
            return "";
        }
        if (UNKNOWN.equals(plate)) {
            return "НОМЕР УТОЧНЯЕТСЯ";
        }
        Matcher matcher = PLATE_PATTERN.matcher(plate);
        if (!matcher.matches()) {
            return plate;
        }
        return matcher.group(1)
                + " "
                + matcher.group(2)
                + " "
                + matcher.group(3)
                + " "
                + superscript(matcher.group(4));
    }

    private static String superscript(String digits) {
        StringBuilder result = new StringBuilder(digits.length());
        for (int i = 0; i < digits.length(); i++) {
            char digit = digits.charAt(i);
            result.append(digit >= '0' && digit <= '9'
                    ? SUPERSCRIPT_DIGITS[digit - '0']
                    : digit);
        }
        return result.toString();
    }
}
