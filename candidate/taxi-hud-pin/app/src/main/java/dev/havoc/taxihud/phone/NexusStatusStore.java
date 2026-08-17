package dev.havoc.taxihud.phone;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.anezium.rokidbus.client.PluginRegistrationResult;

public final class NexusStatusStore {
    public static final String ACTION_STATUS =
            "com.havoc.rokid.plugin.taxihudpin.NEXUS_STATUS";

    private static final String PREFS = "taxi_hud_nexus";
    private static final String KEY_REGISTRATION = "registration";
    private static final String KEY_LINK_STATE = "link_state";

    private final Context context;
    private final SharedPreferences preferences;

    public NexusStatusStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int registrationState() {
        return preferences.getInt(
                KEY_REGISTRATION, PluginRegistrationResult.PENDING_USER_APPROVAL);
    }

    public boolean isApproved() {
        return registrationState() == PluginRegistrationResult.APPROVED;
    }

    public int linkState() {
        return preferences.getInt(KEY_LINK_STATE, 0);
    }

    public void setRegistrationState(int state) {
        preferences.edit().putInt(KEY_REGISTRATION, state).apply();
        notifyChanged();
    }

    public void setLinkState(int state) {
        preferences.edit().putInt(KEY_LINK_STATE, state).apply();
        notifyChanged();
    }

    private void notifyChanged() {
        context.sendBroadcast(new Intent(ACTION_STATUS).setPackage(context.getPackageName()));
    }
}
