package dev.havoc.taxihud.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TripPinTickReceiver extends BroadcastReceiver {
    public static final String ACTION_TICK =
            "com.havoc.rokid.plugin.taxihudpin.action.TRIP_PIN_TICK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TICK.equals(intent.getAction())) {
            return;
        }
        PendingResult pendingResult = goAsync();
        try {
            TaxiCoordinator.get(context).onTripPinTick().whenComplete(
                    (ignored, throwable) -> pendingResult.finish());
        } catch (Throwable throwable) {
            pendingResult.finish();
        }
    }
}
