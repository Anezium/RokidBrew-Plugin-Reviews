package dev.havoc.taxihud.phone;

import android.content.Context;
import android.content.SharedPreferences;

public final class TaxiPlatePreferences {
    private static final String STORE =
            "com.havoc.rokid.plugin.taxihudpin.DISPLAY_PREFERENCES";
    private static final String AUTO_TRIP_PIN = "auto_trip_pin";

    private final SharedPreferences preferences;

    public TaxiPlatePreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public boolean autoTripPin() {
        return preferences.getBoolean(AUTO_TRIP_PIN, false);
    }

    public void setAutoTripPin(boolean enabled) {
        preferences.edit().putBoolean(AUTO_TRIP_PIN, enabled).apply();
    }
}
