package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.havoc.rokid.plugin.taxihudpin.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class MainActivityUiTest {
    @Test
    public void nexusSettingsScreenKeepsAllPluginActionsAndUninstall() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = activity.findViewById(android.R.id.content);

        List<String> labels = new ArrayList<>();
        collectLabels(root, labels);

        assertTrue(labels.contains("Taxi Plate"));
        assertTrue(labels.contains("Continue setup"));
        assertTrue(labels.contains("Language"));
        assertTrue(labels.contains("Use phone language"));
        assertTrue(labels.contains("Notification access"));
        assertTrue(labels.contains("How to limit notification access"));
        assertTrue(labels.contains("ru.yandex.go"));
        assertTrue(labels.contains("Test widget"));
        assertTrue(labels.contains("IMPORT JSON"));
        assertTrue(labels.contains("BACKUP AND RESTORE"));
        assertTrue(labels.contains("EXPORT SETTINGS"));
        assertTrue(labels.contains("IMPORT SETTINGS"));
        assertTrue(labels.contains("Export adapter logs"));
        assertTrue(labels.contains("Uninstall Taxi Plate"));
    }

    @Test
    public void notificationGuideDescribesAndroidCategoryAndAppFilters() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        String guide = activity.getString(R.string.notification_access_guide_body);

        assertTrue(guide.contains("Notifications"));
        assertTrue(guide.contains("Conversations"));
        assertTrue(guide.contains("Real-time"));
        assertTrue(guide.contains("Silent"));
        assertTrue(guide.contains("See all apps"));
        assertFalse(guide.contains("important ongoing"));
        assertFalse(guide.contains("does not offer a per-source-app filter"));
    }

    @Test
    public void russianNotificationGuideUsesPixelSettingsLabels() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag("ru"));
        Context russianContext = activity.createConfigurationContext(configuration);
        String guide = russianContext.getString(R.string.notification_access_guide_body);

        assertTrue(guide.contains("«Уведомления»"));
        assertTrue(guide.contains("«Разговоры»"));
        assertTrue(guide.contains("«В реальном времени»"));
        assertTrue(guide.contains("«Без звука»"));
        assertTrue(guide.contains("«Посмотреть все приложения»"));
        assertFalse(guide.contains("персистентные"));
    }

    @Test
    public void notificationAccessActionTargetsTaxiPlateListenerDetails() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Intent intent = activity.notificationAccessSettingsIntent();

        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.getAction());
        assertEquals(
                new ComponentName(activity, TaxiNotificationListenerService.class)
                        .flattenToString(),
                intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME));
    }

    private static void collectLabels(View view, List<String> labels) {
        if (view instanceof TextView) {
            labels.add(((TextView) view).getText().toString());
        }
        CharSequence description = view.getContentDescription();
        if (description != null) {
            labels.add(description.toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectLabels(group.getChildAt(index), labels);
            }
        }
    }
}
