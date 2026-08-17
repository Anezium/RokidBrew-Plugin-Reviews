package dev.havoc.taxihud.phone.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public final class NotificationLogStoreTest {
    @Test public void persistsNewestTwoHundredEventsAndClearsThem() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationLogStore store = new NotificationLogStore(context);
        store.clear();
        for (int i = 0; i < 205; i++) store.append(event(i));
        assertEquals(200, new NotificationLogStore(context).entries().size());
        store.clear();
        assertTrue(new NotificationLogStore(context).entries().isEmpty());
    }
    private static NotificationLogEvent event(long timestamp) {
        return new NotificationLogEvent(timestamp, "com.example.cab", "local-cab", "Local Cab",
                "posted", "key", 0, "title", "text", "big", Collections.emptyList(),
                NotificationParserResult.empty(), "NONE");
    }
}
