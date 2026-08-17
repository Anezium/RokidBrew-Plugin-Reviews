package dev.havoc.taxihud.phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Refreshes the rendered minute value exactly when the rounded-up countdown changes. */
final class TripPinScheduler {
    private static final int REQUEST_CODE = 42703;

    private final Context context;
    private final AlarmManager alarmManager;
    private final Handler mainHandler;
    private Runnable callback;

    TripPinScheduler(Context context) {
        this.context = context.getApplicationContext();
        alarmManager = this.context.getSystemService(AlarmManager.class);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    void schedule(long tripEndsAtEpochMs) {
        cancel();
        long now = System.currentTimeMillis();
        if (tripEndsAtEpochMs <= now) {
            return;
        }
        long remaining = tripEndsAtEpochMs - now;
        long delay = remaining % 60_000L;
        if (delay == 0L) {
            delay = 60_000L;
        }
        delay = Math.max(1_000L, delay);
        long triggerAt = now + delay;
        callback = () -> context.sendBroadcast(tickIntent());
        mainHandler.postDelayed(callback, delay);
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent());
    }

    void cancel() {
        if (callback != null) {
            mainHandler.removeCallbacks(callback);
            callback = null;
        }
        alarmManager.cancel(pendingIntent());
    }

    private Intent tickIntent() {
        return new Intent(context, TripPinTickReceiver.class)
                .setAction(TripPinTickReceiver.ACTION_TICK);
    }

    private PendingIntent pendingIntent() {
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                tickIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
