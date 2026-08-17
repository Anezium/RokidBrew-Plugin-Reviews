package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.Configuration;

import dev.havoc.taxihud.phone.state.RideSnapshot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
public final class RideTextLocalizationTest {
    private final RideSnapshot ride = new RideSnapshot(
            "А111АА777", "Синий", "Demo Car", "3",
            false, "", 1L, 1L,
            true, false, 0L, false, 0L);

    @Test
    public void englishIsTheFallbackPresentationLanguage() {
        assertEquals("Arrives in: 3 min", RideText.status(context("en"), ride, 1_000L));
    }

    @Test
    public void russianResourcesLocalizeGlassesPresentation() {
        assertEquals("Приедет: 3 мин", RideText.status(context("ru"), ride, 1_000L));
    }

    private static Context context(String language) {
        Context base = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language));
        return base.createConfigurationContext(configuration);
    }
}
