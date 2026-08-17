package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import dev.havoc.taxihud.phone.state.RideSnapshot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaxiHudNotificationPublisherTest {
    @Test
    public void postsOneSilentDismissibleNotificationWithoutReplyAction() {
        Context context = RuntimeEnvironment.getApplication();

        TaxiHudNotificationPublisher publisher = new TaxiHudNotificationPublisher(context);
        publisher.show(snapshot());

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Notification notification = shadowOf(manager)
                .getNotification(TaxiHudNotificationPublisher.NOTIFICATION_ID);
        assertEquals(42701, TaxiHudNotificationPublisher.NOTIFICATION_ID);
        assertNotNull(notification);
        assertEquals(TaxiHudNotificationPublisher.CHANNEL_ID, notification.getChannelId());
        assertFalse((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
        assertEquals(0, notification.actions == null ? 0 : notification.actions.length);
        assertNotNull(notification.deleteIntent);

        NotificationChannel channel = manager.getNotificationChannel(
                TaxiHudNotificationPublisher.CHANNEL_ID);
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.getImportance());
        assertNull(channel.getSound());
        assertFalse(channel.shouldVibrate());
        assertTrue(publisher.isShowing());
        publisher.cancel();
        assertFalse(publisher.isShowing());
    }

    private static RideSnapshot snapshot() {
        return new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "5", false, "",
                1L, 2L, true, false, 0L, false, 0L);
    }
}
