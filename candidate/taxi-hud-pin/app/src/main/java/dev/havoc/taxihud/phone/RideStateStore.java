package dev.havoc.taxihud.phone;

import android.content.Context;
import android.content.SharedPreferences;

import dev.havoc.taxihud.phone.state.RideSnapshot;
import dev.havoc.taxihud.phone.state.RideSnapshotCodec;

public class RideStateStore {
    private static final String PREFS_NAME = "taxi_hud_ride_state";
    private static final String SNAPSHOT_KEY = "snapshot";

    private final SharedPreferences preferences;
    private final RideSnapshotCodec codec = new RideSnapshotCodec();

    public RideStateStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public RideSnapshot read() {
        return codec.decode(preferences.getString(SNAPSHOT_KEY, ""));
    }

    public void write(RideSnapshot snapshot) {
        preferences.edit().putString(SNAPSHOT_KEY, codec.encode(snapshot)).commit();
    }

    public void clear() {
        preferences.edit().remove(SNAPSHOT_KEY).commit();
    }
}
