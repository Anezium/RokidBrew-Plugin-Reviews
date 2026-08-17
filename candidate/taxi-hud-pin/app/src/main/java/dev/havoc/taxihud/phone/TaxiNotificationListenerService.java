package dev.havoc.taxihud.phone;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class TaxiNotificationListenerService extends NotificationListenerService {
    public enum SyncResult {
        SENT,
        LISTENER_UNAVAILABLE,
        NO_CONFIGURED_NOTIFICATIONS,
        NO_CURRENT_RIDE
    }

    private static volatile WeakReference<TaxiNotificationListenerService> connectedService =
            new WeakReference<>(null);

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connectedService = new WeakReference<>(this);
        TaxiCoordinator.get(this).restore();
    }

    @Override
    public void onListenerDisconnected() {
        clearConnectedService(this);
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        clearConnectedService(this);
        NexusTaxiHudTransport.get(this).disconnect();
        super.onDestroy();
    }

    public static CompletionStage<SyncResult> syncActiveConfiguredNotifications() {
        TaxiNotificationListenerService service = connectedService.get();
        if (service == null) {
            return CompletableFuture.completedFuture(SyncResult.LISTENER_UNAVAILABLE);
        }
        return service.syncActiveConfiguredNotificationsNow();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isConfiguredNotification(sbn, new dev.havoc.taxihud.phone.config.AdapterRepository(this))) {
            return;
        }
        NotificationFields fields = NotificationFields.from(sbn.getNotification());
        TaxiCoordinator.get(this).onNotificationPosted(
                System.currentTimeMillis(),
                sbn.getPackageName(),
                sbn.getKey(),
                fields.title,
                fields.text,
                fields.bigText,
                fields.textLines,
                fields.timing);
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn,
            RankingMap rankingMap,
            int reason) {
        if (!isConfiguredNotification(sbn, new dev.havoc.taxihud.phone.config.AdapterRepository(this))) {
            return;
        }
        NotificationFields fields = NotificationFields.from(sbn.getNotification());
        TaxiCoordinator.get(this).onNotificationRemoved(
                System.currentTimeMillis(),
                sbn.getPackageName(),
                sbn.getKey(),
                reason,
                fields.title,
                fields.text,
                fields.bigText,
                fields.textLines,
                fields.timing);
    }

    private CompletionStage<SyncResult> syncActiveConfiguredNotificationsNow() {
        final List<StatusBarNotification> notifications;
        try {
            notifications = selectActiveConfiguredNotifications(
                    getActiveNotifications(),
                    new dev.havoc.taxihud.phone.config.AdapterRepository(this));
        } catch (RuntimeException exception) {
            CompletableFuture<SyncResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
        if (notifications.isEmpty()) {
            return CompletableFuture.completedFuture(SyncResult.NO_CONFIGURED_NOTIFICATIONS);
        }

        TaxiCoordinator coordinator = TaxiCoordinator.get(this);
        CompletionStage<Void> scan = CompletableFuture.completedFuture(null);
        for (StatusBarNotification sbn : notifications) {
            NotificationFields fields = NotificationFields.from(sbn.getNotification());
            scan = scan.thenCompose(ignored -> coordinator.onNotificationSynced(
                    System.currentTimeMillis(),
                    sbn.getPackageName(),
                    sbn.getKey(),
                    fields.title,
                    fields.text,
                    fields.bigText,
                    fields.textLines,
                    fields.timing));
        }
        return scan.thenCompose(ignored -> coordinator.resendCurrentToGlasses())
                .thenApply(sent -> sent ? SyncResult.SENT : SyncResult.NO_CURRENT_RIDE);
    }

    static List<StatusBarNotification> selectActiveConfiguredNotifications(
            StatusBarNotification[] activeNotifications,
            dev.havoc.taxihud.phone.config.AdapterRepository repository) {
        List<StatusBarNotification> selected = new ArrayList<>();
        if (activeNotifications != null) {
            for (StatusBarNotification notification : activeNotifications) {
                if (isConfiguredNotification(notification, repository)) {
                    selected.add(notification);
                }
            }
        }
        selected.sort(Comparator.comparingLong(StatusBarNotification::getPostTime));
        return selected;
    }

    private static void clearConnectedService(TaxiNotificationListenerService service) {
        if (connectedService.get() == service) {
            connectedService = new WeakReference<>(null);
        }
    }

    private static boolean isConfiguredNotification(
            StatusBarNotification sbn,
            dev.havoc.taxihud.phone.config.AdapterRepository repository) {
        if (sbn == null || sbn.getNotification() == null) {
            return false;
        }
        return repository.handlesPackage(sbn.getPackageName());
    }

    private static final class NotificationFields {
        final String title;
        final String text;
        final String bigText;
        final List<String> textLines;
        final NotificationTiming timing;

        NotificationFields(
                String title,
                String text,
                String bigText,
                List<String> textLines,
                NotificationTiming timing) {
            this.title = title;
            this.text = text;
            this.bigText = bigText;
            this.textLines = textLines;
            this.timing = timing;
        }

        static NotificationFields from(Notification notification) {
            Bundle extras = notification == null ? null : notification.extras;
            if (extras == null) {
                return new NotificationFields(
                        "", "", "", Collections.emptyList(), NotificationTiming.NONE);
            }
            List<String> lines = new ArrayList<>();
            CharSequence[] rawLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (rawLines != null) {
                for (CharSequence line : rawLines) {
                    if (line != null) {
                        lines.add(line.toString());
                    }
                }
            }
            addExtraLine(lines, extras, Notification.EXTRA_SUB_TEXT);
            addExtraLine(lines, extras, Notification.EXTRA_INFO_TEXT);
            addExtraLine(lines, extras, Notification.EXTRA_SUMMARY_TEXT);
            addExtraLine(lines, extras, Notification.EXTRA_TITLE_BIG);
            return new NotificationFields(
                    stringExtra(extras, Notification.EXTRA_TITLE),
                    stringExtra(extras, Notification.EXTRA_TEXT),
                    stringExtra(extras, Notification.EXTRA_BIG_TEXT),
                    lines,
                    new NotificationTiming(
                            notification.when,
                            extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
                            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
                            extras.getInt(Notification.EXTRA_PROGRESS, 0),
                            extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
                            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)));
        }

        private static void addExtraLine(List<String> lines, Bundle extras, String key) {
            String value = stringExtra(extras, key);
            if (!value.isEmpty() && !lines.contains(value)) {
                lines.add(value);
            }
        }

        private static String stringExtra(Bundle extras, String key) {
            CharSequence value = extras.getCharSequence(key);
            return value == null ? "" : value.toString();
        }
    }
}
