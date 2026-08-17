package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;

import android.app.Notification;
import android.os.Process;
import android.os.UserHandle;
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

    @Test
    public void packageChoiceAppliesBeforeReadingNotificationsFromEveryDeliveredProfile() {
        dev.havoc.taxihud.phone.config.AdapterRepository repository =
                new dev.havoc.taxihud.phone.config.AdapterRepository(
                        RuntimeEnvironment.getApplication());
        repository.setPackageAllowed("ru.yandex.go", false);

        StatusBarNotification personal = notification(
                "ru.yandex.go", 1, 1_000L, Process.myUserHandle());
        StatusBarNotification work = notification(
                "ru.yandex.go", 2, 2_000L, UserHandle.getUserHandleForUid(1_010_000));
        StatusBarNotification privateProfile = notification(
                "ru.yandex.go", 3, 3_000L, UserHandle.getUserHandleForUid(1_110_000));

        assertEquals(0, TaxiNotificationListenerService.selectActiveConfiguredNotifications(
                new StatusBarNotification[] {personal, work, privateProfile}, repository).size());

        repository.setPackageAllowed("ru.yandex.go", true);
        assertEquals(3, TaxiNotificationListenerService.selectActiveConfiguredNotifications(
                new StatusBarNotification[] {personal, work, privateProfile}, repository).size());
    }

    private static StatusBarNotification notification(String packageName, int id, long postTime) {
        return notification(packageName, id, postTime, Process.myUserHandle());
    }

    private static StatusBarNotification notification(
            String packageName, int id, long postTime, UserHandle user) {
        return new StatusBarNotification(
                packageName,
                packageName,
                id,
                null,
                10_000,
                20_000,
                0,
                new Notification(),
                user,
                postTime);
    }
}
