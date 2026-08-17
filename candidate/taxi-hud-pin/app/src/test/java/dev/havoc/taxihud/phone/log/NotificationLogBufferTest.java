package dev.havoc.taxihud.phone.log;

import static org.junit.Assert.assertEquals;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class NotificationLogBufferTest {
    @Test public void keepsNewestTwoHundredEvents() {
        NotificationLogBuffer buffer = new NotificationLogBuffer();
        for (int i = 0; i < 205; i++) buffer.add(event(i));
        assertEquals(200, buffer.entries().size());
        assertEquals(204L, buffer.entries().get(0).timestampMs);
        assertEquals(5L, buffer.entries().get(199).timestampMs);
    }

    @Test public void jsonlPreservesAdapterIdentityAndDecision() {
        NotificationLogEvent output = NotificationLogBuffer.fromJsonl(
                NotificationLogBuffer.toJsonl(Collections.singletonList(event(100)))).get(0);
        assertEquals("local-cab", output.adapterId);
        assertEquals("Local Cab", output.adapterDisplayName);
        assertEquals("ACTIVE", output.parserResult.status);
        assertEquals("SHOW_OR_UPDATE", output.decision);
    }

    @Test public void malformedLineDoesNotHideValidEntries() {
        String first = NotificationLogBuffer.toJsonl(Collections.singletonList(event(2)));
        String second = NotificationLogBuffer.toJsonl(Collections.singletonList(event(1)));
        List<NotificationLogEvent> entries = NotificationLogBuffer.fromJsonl(
                first + "\n{not-json}\n" + second);
        assertEquals(2, entries.size());
    }

    private static NotificationLogEvent event(long timestamp) {
        return new NotificationLogEvent(timestamp, "com.example.cab", "local-cab", "Local Cab",
                "posted", "key", 0, "title", "text", "big", Collections.emptyList(),
                new NotificationParserResult("ACTIVE", "А111АА777", "Синий", "Kia", "3", ""),
                "SHOW_OR_UPDATE");
    }
}
