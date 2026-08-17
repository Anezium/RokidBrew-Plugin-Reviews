package dev.havoc.taxihud.phone;

import android.content.Context;

import com.havoc.rokid.plugin.taxihudpin.R;

import dev.havoc.taxihud.phone.parse.TaxiUpdate;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletionStage;

public final class TestWidgetFlow {
    private static final char[] PLATE_LETTERS = "АВЕКМНОРСТУХ".toCharArray();
    private static final int[] REGIONS = {77, 99, 197, 750, 790, 799};
    private static final String[] DEFAULT_COLORS = {
            "White", "Black", "Gray", "Blue", "Red", "Yellow"
    };
    private static final String[] MODELS = {
            "Kia K5", "Hyundai Solaris", "Toyota Camry",
            "Skoda Rapid", "Geely Coolray", "Chery Tiggo 7 Pro"
    };

    public interface UpdateSink {
        CompletionStage<Void> send(TaxiUpdate update);
    }

    private final UpdateSink sink;
    private final Random random;
    private final String[] colors;

    public TestWidgetFlow(UpdateSink sink) {
        this(sink, new Random(), DEFAULT_COLORS);
    }

    public TestWidgetFlow(Context context, UpdateSink sink) {
        this(
                sink,
                new Random(),
                TaxiLocale.localized(context).getResources()
                        .getStringArray(R.array.test_vehicle_colors));
    }

    TestWidgetFlow(UpdateSink sink, Random random) {
        this(sink, random, DEFAULT_COLORS);
    }

    private TestWidgetFlow(UpdateSink sink, Random random, String[] colors) {
        this.sink = Objects.requireNonNull(sink);
        this.random = Objects.requireNonNull(random);
        this.colors = Objects.requireNonNull(colors).clone();
    }

    public CompletionStage<Void> run() {
        return sink.send(TaxiUpdate.syntheticTest(
                randomPlate(),
                pick(colors),
                pick(MODELS),
                Integer.toString(1 + random.nextInt(12))));
    }

    private String randomPlate() {
        return String.format(
                Locale.ROOT,
                "%c%03d%c%c%d",
                randomLetter(),
                1 + random.nextInt(999),
                randomLetter(),
                randomLetter(),
                REGIONS[random.nextInt(REGIONS.length)]);
    }

    private char randomLetter() {
        return PLATE_LETTERS[random.nextInt(PLATE_LETTERS.length)];
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }
}
