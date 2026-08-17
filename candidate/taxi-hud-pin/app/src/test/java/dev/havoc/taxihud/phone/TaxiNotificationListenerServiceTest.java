package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;

import android.app.Notification;
import android.os.Process;
import android.service.notification.StatusBarNotification;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaxiNotificationListenerServiceTest {
    @Test
    public void activeSyncFiltersConfiguredPackagesAndSortsOldestFirst() {
        StatusBarNotification newer = notification("ru.yandex.go", 2, 2_000L);
        StatusBarNotification unrelated = notification("com.example", 3, 500L);
        StatusBarNotification older = notification("ru.yandex.taxi", 1, 1_000L);

        List<StatusBarNotification> selected =
                TaxiNotificationListenerService.selectActiveConfiguredNotifications(
                        new StatusBarNotification[] {newer, unrelated, older},
                        new dev.havoc.taxihud.phone.config.AdapterRepository(
                                RuntimeEnvironment.getApplication()));

        assertEquals(2, selected.size());
        assertEquals(1, selected.get(0).getId());
        assertEquals(2, selected.get(1).getId());
    }

    @Test
    public void activeSyncHandlesMissingNotificationArray() {
        assertEquals(0,
                TaxiNotificationListenerService.selectActiveConfiguredNotifications(
                        null, new dev.havoc.taxihud.phone.config.AdapterRepository(
                                RuntimeEnvironment.getApplication())).size());
    }

    private static StatusBarNotification notification(String packageName, int id, long postTime) {
        return new StatusBarNotification(
                packageName,
                packageName,
                id,
                null,
                10_000,
                20_000,
                0,
                new Notification(),
                Process.myUserHandle(),
                postTime);
    }
}
