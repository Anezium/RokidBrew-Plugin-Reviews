package dev.havoc.rokidhome.phone.ha

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAssistantClientTest {
    @Test fun normalizesHttpAndWebSocketUrls() {
        assertEquals("https://ha.example.org", HomeAssistantClient.normalizeUrl(" https://ha.example.org/ "))
        assertEquals("wss://ha.example.org/api/websocket", HomeAssistantClient.webSocketUrl("https://ha.example.org"))
        assertEquals("light" to "turn_on", HomeAssistantClient.normalizeAction("light.turn_on"))
    }
}
