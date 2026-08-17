package dev.havoc.rokidhome.phone.ha

import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeAssistantWebSocketContractTest {
    @Test fun authInvalidRemainsStableWhenServerClosesSocket() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().webSocketUpgrade(InvalidTokenHomeAssistant()).build())
            server.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HomeAssistantClient(scope)

            client.connect(server.url("/").toString().trimEnd('/'), "revoked-token")
            withTimeout(5_000) { client.state.first { it == HaConnectionState.AUTH_ERROR } }
            delay(1_500)

            assertEquals(HaConnectionState.AUTH_ERROR, client.state.value)
            assertEquals(1, server.requestCount)
            client.disconnect()
            scope.cancel()
        }
    }

    @Test fun repeatedConnectWithSameCredentialsKeepsSingleSocket() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().webSocketUpgrade(FakeHomeAssistant()).build())
            server.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HomeAssistantClient(scope)
            val baseUrl = server.url("/").toString().trimEnd('/')

            client.connect(baseUrl, "token")
            withTimeout(5_000) { client.state.first { it == HaConnectionState.ONLINE } }
            client.connect(baseUrl, "token")
            delay(300)

            assertEquals(1, server.requestCount)
            assertEquals(HaConnectionState.ONLINE, client.state.value)
            client.disconnect()
            scope.cancel()
        }
    }

    @Test fun authStatesTemplatesActionsDisconnectAndReconnectFollowContract() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().webSocketUpgrade(FakeHomeAssistant()).build())
            server.enqueue(MockResponse.Builder().code(200).body("true").build())
            server.enqueue(MockResponse.Builder().webSocketUpgrade(FakeHomeAssistant()).build())
            server.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HomeAssistantClient(scope)
            val baseUrl = server.url("/").toString().trimEnd('/')

            client.connect(baseUrl, "token")
            withTimeout(5_000) { client.state.first { it == HaConnectionState.ONLINE } }
            assertEquals("playing", client.entities.value["media_player.tv"]?.state)
            withTimeout(5_000) { client.entities.first { it["media_player.tv"]?.state == "playing" } }
            assertEquals(listOf("media_player.tv"), withTimeout(5_000) { client.entityRegistry.first { it.isNotEmpty() } })
            assertEquals("/api/websocket", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)?.url?.encodedPath)
            assertEquals("true", client.renderTemplate("{{ true }}").getOrThrow())
            val templateRequest = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(templateRequest)
            assertEquals("/api/template", templateRequest!!.url.encodedPath)
            assertEquals("Bearer token", templateRequest.headers["Authorization"])
            assertEquals("{{ true }}", Json.parseToJsonElement(requireNotNull(templateRequest.body).utf8()).jsonObject["template"]?.jsonPrimitive?.content)
            assertEquals(ActionCallResult.Success, client.callAction(HomeAssistantAction("media_player.media_pause")))

            client.disconnect()
            assertEquals(HaConnectionState.DISCONNECTED, client.state.value)
            assertTrue(client.entities.value.isEmpty())
            client.connect(baseUrl, "token")
            withTimeout(5_000) { client.state.first { it == HaConnectionState.ONLINE } }
            client.disconnect()
            scope.cancel()
        }
    }

    private class FakeHomeAssistant : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"type":"auth_required","ha_version":"2026.6.3"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = Json.parseToJsonElement(text).jsonObject
            val id = message["id"]?.jsonPrimitive?.longOrNull
            when (message["type"]?.jsonPrimitive?.content) {
                "auth" -> webSocket.send("""{"type":"auth_ok","ha_version":"2026.6.3"}""")
                "get_states" -> result(webSocket, id, buildJsonArray {
                    add(buildJsonObject {
                        put("entity_id", "media_player.tv")
                        put("state", "playing")
                        put("attributes", buildJsonObject { put("friendly_name", "TV") })
                    })
                })
                "config/entity_registry/list_for_display" -> result(webSocket, id, buildJsonObject {
                    put("entities", buildJsonArray { add(buildJsonObject { put("ei", "media_player.tv") }) })
                })
                "subscribe_events" -> result(webSocket, id, JsonNull)
                "render_template" -> {
                    val keys = message["data"]!!.jsonObject.keys
                    result(webSocket, id, buildJsonObject { keys.forEach { put(it, "true") } })
                }
                "call_service" -> result(webSocket, id, buildJsonObject { put("context", JsonObject(emptyMap())); put("response", JsonNull) })
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        private fun result(socket: WebSocket, id: Long?, value: JsonElement) {
            socket.send(buildJsonObject {
                put("id", requireNotNull(id)); put("type", "result"); put("success", true); put("result", value)
            }.toString())
        }
    }

    private class InvalidTokenHomeAssistant : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"type":"auth_required","ha_version":"2026.6.3"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            webSocket.send("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
            webSocket.close(1008, "auth invalid")
        }
    }
}
