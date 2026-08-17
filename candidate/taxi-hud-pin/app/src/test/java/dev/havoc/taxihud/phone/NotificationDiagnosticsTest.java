package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;

import org.junit.Test;

public final class NotificationDiagnosticsTest {
    @Test public void requiresPermissionAndAppNotificationsToBeEnabled() {
        assertFalse(NotificationDiagnostics.notificationsReady(false, true));
        assertFalse(NotificationDiagnostics.notificationsReady(true, false));
        assertTrue(NotificationDiagnostics.notificationsReady(true, true));
    }

    @Test public void requiresExistingChannelWithNonzeroImportance() {
        assertFalse(NotificationDiagnostics.channelReady(false, NotificationManager.IMPORTANCE_LOW));
        assertFalse(NotificationDiagnostics.channelReady(true, NotificationManager.IMPORTANCE_NONE));
        assertTrue(NotificationDiagnostics.channelReady(true, NotificationManager.IMPORTANCE_LOW));
    }
}
