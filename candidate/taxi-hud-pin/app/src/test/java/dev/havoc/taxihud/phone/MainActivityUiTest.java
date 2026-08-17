package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

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
        assertTrue(labels.contains("Test widget"));
        assertTrue(labels.contains("IMPORT JSON"));
        assertTrue(labels.contains("Export adapter logs"));
        assertTrue(labels.contains("Uninstall Taxi Plate"));
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
