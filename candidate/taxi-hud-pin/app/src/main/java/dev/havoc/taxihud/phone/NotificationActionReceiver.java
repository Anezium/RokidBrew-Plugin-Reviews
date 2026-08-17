package dev.havoc.taxihud.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_DISMISSED =
            "com.havoc.rokid.plugin.taxihudpin.action.DISMISSED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_DISMISSED.equals(intent.getAction())) {
            PendingResult pendingResult = goAsync();
            try {
                TaxiCoordinator.get(context).onManualDismiss().whenComplete(
                        (ignored, throwable) -> pendingResult.finish());
            } catch (Throwable throwable) {
                pendingResult.finish();
            }
        }
    }
}
