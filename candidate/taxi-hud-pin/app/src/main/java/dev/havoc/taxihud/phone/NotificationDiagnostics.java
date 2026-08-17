package dev.havoc.taxihud.phone;

import android.app.NotificationManager;

public final class NotificationDiagnostics {
    private NotificationDiagnostics() {
    }

    public static boolean notificationsReady(boolean permissionGranted, boolean appEnabled) {
        return permissionGranted && appEnabled;
    }

    public static boolean channelReady(boolean exists, int importance) {
        return exists && importance != NotificationManager.IMPORTANCE_NONE;
    }
}
