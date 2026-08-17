package dev.havoc.taxihud.phone;

import com.havoc.rokid.plugin.taxihudpin.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import dev.havoc.taxihud.phone.state.RideSnapshot;

public class TaxiHudNotificationPublisher {
    public static final int NOTIFICATION_ID = 42701;
    public static final String CHANNEL_ID = "taxi_hud_ride_v1";
    private static final String GROUP_KEY = "taxi_hud_ride";

    private final Context context;
    private final NotificationManager notificationManager;
    public TaxiHudNotificationPublisher(Context context) {
        this.context = context.getApplicationContext();
        notificationManager = this.context.getSystemService(NotificationManager.class);
    }

    public void show(RideSnapshot snapshot) {
        ensureChannel();
        Context strings = TaxiLocale.localized(context);
        String plate = RideText.plate(strings, snapshot);
        String vehicle = RideText.vehicle(strings, snapshot);
        String status = RideText.status(strings, snapshot, System.currentTimeMillis());
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                new Intent(context, NotificationActionReceiver.class)
                        .setAction(NotificationActionReceiver.ACTION_DISMISSED),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.nexus_glyph_taxi_plate)
                .setContentTitle(plate)
                .setContentText(vehicle)
                .setSubText(status)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        plate + "\n" + vehicle + "\n" + status))
                .setDeleteIntent(dismissPendingIntent)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setGroup(GROUP_KEY)
                .setOngoing(false)
                .setAutoCancel(false)
                .build();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    public void cancel() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public boolean isShowing() {
        for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
            if (notification.getId() == NOTIFICATION_ID) {
                return true;
            }
        }
        return false;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        Context strings = TaxiLocale.localized(context);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                strings.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(strings.getString(R.string.notification_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setVibrationPattern(null);
        notificationManager.createNotificationChannel(channel);
    }
}
