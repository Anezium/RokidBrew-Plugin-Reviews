package dev.havoc.taxihud.phone;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import com.havoc.rokid.plugin.taxihudpin.R;

import java.util.Locale;

public final class TaxiLocale {
    static final String ENGLISH = "en";
    static final String RUSSIAN = "ru";
    private static final String PREFERENCES = "taxi_plate_locale";
    private static final String KEY_LANGUAGE = "language";

    private TaxiLocale() {
    }

    public static Context localized(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return context;
        }
        String tag = selectedLanguageTag(context);
        if (tag.isEmpty()) {
            return context;
        }
        Configuration configuration = new Configuration(
                context.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(tag));
        return context.createConfigurationContext(configuration);
    }

    static String selectedLanguageTag(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager == null || manager.getApplicationLocales().isEmpty()) {
                return "";
            }
            return normalize(manager.getApplicationLocales().get(0).getLanguage());
        }
        return normalize(preferences(context).getString(KEY_LANGUAGE, ""));
    }

    static int selectedLabel(Context context) {
        switch (selectedLanguageTag(context)) {
            case ENGLISH:
                return R.string.language_english;
            case RUSSIAN:
                return R.string.language_russian;
            default:
                return R.string.language_system;
        }
    }

    static void setLanguage(Context context, String languageTag) {
        String normalized = normalize(languageTag);
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                manager.setApplicationLocales(normalized.isEmpty()
                        ? LocaleList.getEmptyLocaleList()
                        : LocaleList.forLanguageTags(normalized));
            }
            return;
        }
        preferences(context).edit().putString(KEY_LANGUAGE, normalized).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String language = Locale.forLanguageTag(value).getLanguage();
        if (ENGLISH.equals(language) || RUSSIAN.equals(language)) {
            return language;
        }
        return "";
    }
}
