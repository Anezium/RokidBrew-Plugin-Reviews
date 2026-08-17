package dev.havoc.rokidhome.phone.ha

import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class HaConnectionState { DISCONNECTED, CONNECTING, AUTHENTICATING, ONLINE, AUTH_ERROR }
data class HaEntity(val entityId: String, val state: String, val attributes: JsonObject)

class HomeAssistantClient(
    private val scope: CoroutineScope,
    private val http: OkHttpClient = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build(),
) {
    private val ids = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val mutableState = MutableStateFlow(HaConnectionState.DISCONNECTED)
    private val mutableEntities = MutableStateFlow<Map<String, HaEntity>>(emptyMap())
    private val mutableEntityRegistry = MutableStateFlow<List<String>>(emptyList())
    val state: StateFlow<HaConnectionState> = mutableState.asStateFlow()
    val entities: StateFlow<Map<String, HaEntity>> = mutableEntities.asStateFlow()
    val entityRegistry: StateFlow<List<String>> = mutableEntityRegistry.asStateFlow()
    private var socket: WebSocket? = null
    private var baseUrl = ""
    private var token = ""
    private var manualClose = false
    private var reconnectJob: Job? = null
    private var connectionGeneration = 0L
    private var reconnectAttempt = 0

    @Synchronized
    fun connect(url: String, accessToken: String) {
        val normalized = normalizeUrl(url)
        require(accessToken.isNotBlank()) { "Введите long-lived access token" }
        val sameCredentials = baseUrl == normalized && token == accessToken
        if (sameCredentials && mutableState.value in ACTIVE_STATES) return

        closeCurrentSocket()
        manualClose = false
        baseUrl = normalized
        token = accessToken
        reconnectAttempt = 0
        openLocked()
    }

    @Synchronized
    fun disconnect() {
        manualClose = true
        connectionGeneration++
        reconnectJob?.cancel()
        reconnectJob = null
        closeCurrentSocket()
        mutableEntities.value = emptyMap()
        mutableEntityRegistry.value = emptyList()
        mutableState.value = HaConnectionState.DISCONNECTED
    }

    suspend fun test(url: String, accessToken: String): Result<Unit> = runCatching {
        connect(url, accessToken)
        withTimeout(10_000) { state.firstOnline() }
    }

    suspend fun callAction(action: HomeAssistantAction): ActionCallResult {
        if (state.value != HaConnectionState.ONLINE) return ActionCallResult.Failure("Home Assistant offline")
        val (domain, service) = normalizeAction(action.action)
        val target = buildJsonObject { action.target.forEach { (key, value) -> put(key, value) } }
        return try {
            val response = request(buildJsonObject {
                put("type", "call_service"); put("domain", domain); put("service", service)
                put("target", target); put("service_data", action.data); put("return_response", false)
            }, 15_000)
            if (response["success"]?.jsonPrimitive?.booleanOrNull == true) ActionCallResult.Success
            else ActionCallResult.Failure(response["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "Action rejected")
        } catch (_: TimeoutCancellationException) {
            ActionCallResult.TimeoutUnknown
        } catch (failure: Throwable) {
            ActionCallResult.Failure(failure.message ?: "Action failed")
        }
    }

    suspend fun renderTemplate(template: String): Result<String> = renderTemplates(setOf(template)).map { it.getValue(template) }

    suspend fun renderTemplates(templates: Set<String>): Result<Map<String, String>> = runCatching {
        if (templates.isEmpty()) return@runCatching emptyMap()
        coroutineScope {
            templates.map { template ->
                async(Dispatchers.IO) { template to renderTemplateViaRest(template) }
            }.awaitAll().toMap()
        }
    }

    private fun renderTemplateViaRest(template: String): String {
        val payload = buildJsonObject { put("template", template) }.toString()
        val request = Request.Builder()
            .url("$baseUrl/api/template")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return http.newCall(request).execute().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) {
                "Template HTTP ${response.code}: ${body.take(512)}"
            }
            body
        }
    }

    private fun closeCurrentSocket() {
        socket?.close(1000, "client close")
        socket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    @Synchronized
    private fun openLocked() {
        if (manualClose || baseUrl.isBlank() || token.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = null
        val generation = ++connectionGeneration
        mutableState.value = HaConnectionState.CONNECTING
        socket = http.newWebSocket(
            Request.Builder().url(webSocketUrl(baseUrl)).build(),
            Listener(generation),
        )
    }

    private suspend fun request(body: JsonObject, timeoutMs: Long = 10_000): JsonObject {
        val id = ids.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val payload = JsonObject(body + ("id" to JsonPrimitive(id)))
        check(socket?.send(payload.toString()) == true) { "WebSocket unavailable" }
        return try { withTimeout(timeoutMs) { deferred.await() } } finally { pending.remove(id) }
    }

    private suspend fun loadStatesAndSubscribe() {
        val states = request(buildJsonObject { put("type", "get_states") })["result"]?.jsonArray.orEmpty()
        mutableEntities.value = states.mapNotNull(::decodeEntity).associateBy(HaEntity::entityId)
        runCatching {
            val registry = request(buildJsonObject { put("type", "config/entity_registry/list_for_display") })["result"]
            val entries = when (registry) {
                is JsonArray -> registry
                is JsonObject -> registry["entities"]?.jsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            mutableEntityRegistry.value = entries.mapNotNull { entry ->
                val value = entry.jsonObject
                value["ei"]?.jsonPrimitive?.contentOrNull ?: value["entity_id"]?.jsonPrimitive?.contentOrNull
            }.sorted()
        }
        val response = request(buildJsonObject { put("type", "subscribe_events"); put("event_type", "state_changed") })
        check(response["success"]?.jsonPrimitive?.booleanOrNull == true)
    }

    private fun handleMessage(generation: Long, webSocket: WebSocket, text: String) {
        if (!isCurrent(generation, webSocket)) return
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (json["type"]?.jsonPrimitive?.contentOrNull) {
            "auth_required" -> {
                mutableState.value = HaConnectionState.AUTHENTICATING
                webSocket.send(buildJsonObject { put("type", "auth"); put("access_token", token) }.toString())
            }
            "auth_ok" -> {
                reconnectAttempt = 0
                scope.launch {
                    runCatching { loadStatesAndSubscribe() }
                        .onSuccess {
                            if (isCurrent(generation, webSocket)) {
                                mutableState.value = HaConnectionState.ONLINE
                            }
                        }
                        .onFailure {
                            webSocket.cancel()
                            connectionLost(generation, webSocket)
                        }
                }
            }
            "auth_invalid" -> mutableState.value = HaConnectionState.AUTH_ERROR
            "result" -> json["id"]?.jsonPrimitive?.longOrNull?.let { pending.remove(it)?.complete(json) }
            "event" -> {
                val data = json["event"]?.jsonObject?.get("data")?.jsonObject
                val entity = data?.get("new_state")?.jsonObject?.let(::decodeEntity)
                entity?.let { mutableEntities.value = mutableEntities.value + (it.entityId to it) }
            }
        }
    }

    private fun decodeEntity(json: JsonElement): HaEntity? = runCatching {
        val value = json.jsonObject
        HaEntity(value.getValue("entity_id").jsonPrimitive.content, value.getValue("state").jsonPrimitive.content, value["attributes"]?.jsonObject ?: JsonObject(emptyMap()))
    }.getOrNull()

    @Synchronized
    private fun connectionLost(generation: Long, webSocket: WebSocket) {
        if (!isCurrent(generation, webSocket)) return
        val authenticationFailed = mutableState.value == HaConnectionState.AUTH_ERROR
        socket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        mutableEntities.value = emptyMap()
        mutableEntityRegistry.value = emptyList()
        if (authenticationFailed) return
        mutableState.value = HaConnectionState.DISCONNECTED
        scheduleReconnectLocked(generation)
    }

    @Synchronized
    private fun scheduleReconnectLocked(failedGeneration: Long) {
        if (manualClose || mutableState.value == HaConnectionState.AUTH_ERROR || failedGeneration != connectionGeneration) return
        reconnectJob?.cancel()
        val delayMs = (1_000L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(30_000L)
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(delayMs)
            synchronized(this@HomeAssistantClient) {
                if (!manualClose && failedGeneration == connectionGeneration && mutableState.value == HaConnectionState.DISCONNECTED) {
                    openLocked()
                }
            }
        }
    }

    @Synchronized
    private fun isCurrent(generation: Long, webSocket: WebSocket): Boolean =
        !manualClose && generation == connectionGeneration && socket === webSocket

    private inner class Listener(private val generation: Long) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(generation, webSocket, text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(generation, webSocket, bytes.utf8())
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = connectionLost(generation, webSocket)
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = connectionLost(generation, webSocket)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val ACTIVE_STATES = setOf(
            HaConnectionState.CONNECTING,
            HaConnectionState.AUTHENTICATING,
            HaConnectionState.ONLINE,
        )

        fun normalizeUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "URL должен начинаться с http:// или https://" }
            return trimmed.removeSuffix("/api/websocket")
        }
        fun webSocketUrl(value: String) = normalizeUrl(value).replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
        fun normalizeAction(value: String): Pair<String, String> {
            val normalized = value.trim().replace('.', '/')
            val parts = normalized.split('/', limit = 2)
            require(parts.size == 2 && parts.all(String::isNotBlank)) { "Action должен иметь вид domain.service" }
            return parts[0] to parts[1]
        }
    }
}

private suspend fun StateFlow<HaConnectionState>.firstOnline() {
    this.first { it == HaConnectionState.ONLINE || it == HaConnectionState.AUTH_ERROR }
    check(value == HaConnectionState.ONLINE) { "Authentication failed" }
}

sealed interface ActionCallResult {
    data object Success : ActionCallResult
    data class Failure(val message: String) : ActionCallResult
    data object TimeoutUnknown : ActionCallResult
}
