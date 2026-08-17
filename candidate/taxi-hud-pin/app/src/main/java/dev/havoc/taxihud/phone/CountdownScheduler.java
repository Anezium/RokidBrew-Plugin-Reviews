package dev.havoc.taxihud.phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class CountdownScheduler {
    private static final int REQUEST_CODE = 42702;

    private final Context context;
    private final AlarmManager alarmManager;
    private final Handler mainHandler;
    private Runnable primaryCallback;

    public CountdownScheduler(Context context) {
        this.context = context.getApplicationContext();
        alarmManager = this.context.getSystemService(AlarmManager.class);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void schedule(String sessionId, long revision, long deadlineEpochMs) {
        cancel();
        PendingIntent expiryIntent = expiryPendingIntent(sessionId, revision);
        long remainingMs = Math.max(0L, deadlineEpochMs - System.currentTimeMillis());
        primaryCallback = () -> context.sendBroadcast(
                expiryBroadcast(sessionId, revision));
        mainHandler.postDelayed(primaryCallback, remainingMs);
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineEpochMs,
                expiryIntent);
    }

    public void cancel() {
        if (primaryCallback != null) {
            mainHandler.removeCallbacks(primaryCallback);
            primaryCallback = null;
        }
        alarmManager.cancel(expiryPendingIntent("", 0L));
    }

    private Intent expiryBroadcast(String sessionId, long revision) {
        return new Intent(context, CountdownExpiryReceiver.class)
                .setAction(CountdownExpiryReceiver.ACTION_EXPIRED)
                .putExtra(CountdownExpiryReceiver.EXTRA_SESSION_ID, sessionId)
                .putExtra(CountdownExpiryReceiver.EXTRA_REVISION, revision);
    }

    private PendingIntent expiryPendingIntent(String sessionId, long revision) {
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                expiryBroadcast(sessionId, revision),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
