package dev.havoc.taxihud.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class CountdownExpiryReceiver extends BroadcastReceiver {
    public static final String ACTION_EXPIRED =
            "com.havoc.rokid.plugin.taxihudpin.action.COUNTDOWN_EXPIRED";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_REVISION = "revision";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_EXPIRED.equals(intent.getAction())) {
            PendingResult pendingResult = goAsync();
            try {
                TaxiCoordinator.get(context).onCountdownExpired().whenComplete(
                        (ignored, throwable) -> pendingResult.finish());
            } catch (Throwable throwable) {
                pendingResult.finish();
            }
        }
    }
}
