package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import com.anezium.rokidbus.shared.plugin.NexusInputEvent;
import org.junit.Test;

public final class TaxiHudPluginInputTest {
    @Test public void tapAndEnterClearNotification() {
        assertTrue(TaxiHudPluginService.isClearInput(event(KeyEvent.KEYCODE_DPAD_CENTER)));
        assertTrue(TaxiHudPluginService.isClearInput(event(KeyEvent.KEYCODE_ENTER)));
    }

    @Test public void backAndKeyUpDoNotClearNotification() {
        assertFalse(TaxiHudPluginService.isClearInput(event(KeyEvent.KEYCODE_BACK)));
        assertFalse(TaxiHudPluginService.isClearInput(new NexusInputEvent(
                "ride", KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP)));
    }

    private static NexusInputEvent event(int keyCode) {
        return new NexusInputEvent("ride", keyCode, KeyEvent.ACTION_DOWN);
    }
}
