package dev.havoc.rokidhome.shared.protocol

import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.RuntimeValue
import dev.havoc.rokidhome.shared.validation.CanonicalData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

const val PROTOCOL_VERSION = 1
const val MAX_PROTOCOL_BYTES = 256 * 1024

@Serializable
data class ProtocolEnvelope(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val messageId: String,
    val payloadType: String,
    val payloadSchemaVersion: Int = 1,
    val configVersion: Long? = null,
    val payload: JsonObject,
)

@Serializable data class ConfigSnapshotPayload(val configuration: PublishedConfiguration)
@Serializable data class ConfigAcceptedPayload(val checksum: String)
@Serializable data class ConfigRejectedPayload(val reason: String)
@Serializable data class StatePatchPayload(val bindingId: String, val value: RuntimeValue)
@Serializable data class TemplatePatchPayload(val bindingId: String, val value: RuntimeValue)
@Serializable data class PendingContextPagePayload(val pageId: String, val contextRevision: Long)
@Serializable data class ConnectionStatePayload(val homeAssistantOnline: Boolean, val phoneOnline: Boolean, val message: String? = null)
@Serializable data class ReconcilePayload(val activeConfigVersion: Long?, val checksum: String?, val protocolVersion: Int = PROTOCOL_VERSION)
@Serializable data class ActionRequestPayload(
    val requestId: String,
    val action: HomeAssistantAction,
    val actionHash: String,
)
@Serializable data class ActionResultPayload(
    val requestId: String,
    val status: ActionStatus,
    val message: String? = null,
)
@Serializable enum class ActionStatus { SUCCESS, FAILURE, TIMEOUT_UNKNOWN, REJECTED }

object PayloadTypes {
    const val CONFIG_SNAPSHOT = "config.snapshot"
    const val CONFIG_ACCEPTED = "config.accepted"
    const val CONFIG_REJECTED = "config.rejected"
    const val STATE_PATCH = "state.patch"
    const val TEMPLATE_PATCH = "template.patch"
    const val PENDING_CONTEXT_PAGE = "context.pending_page"
    const val CONNECTION_STATE = "connection.state"
    const val RECONCILE = "reconcile"
    const val ACTION_REQUEST = "action.request"
    const val ACTION_RESULT = "action.result"
}

object ProtocolCodec {
    fun <T> encode(
        messageId: String,
        type: String,
        serializer: KSerializer<T>,
        payload: T,
        configVersion: Long? = null,
    ): String {
        require(messageId.length in 1..128)
        val payloadObject = CanonicalData.json.encodeToJsonElement(serializer, payload) as JsonObject
        val encoded = CanonicalData.json.encodeToString(
            ProtocolEnvelope.serializer(),
            ProtocolEnvelope(messageId = messageId, payloadType = type, configVersion = configVersion, payload = payloadObject),
        )
        require(encoded.toByteArray().size <= MAX_PROTOCOL_BYTES) { "Protocol message exceeds $MAX_PROTOCOL_BYTES bytes" }
        return encoded
    }

    fun decodeEnvelope(encoded: String): ProtocolEnvelope {
        require(encoded.toByteArray().size <= MAX_PROTOCOL_BYTES) { "Protocol message exceeds $MAX_PROTOCOL_BYTES bytes" }
        val envelope = CanonicalData.json.decodeFromString(ProtocolEnvelope.serializer(), encoded)
        require(envelope.protocolVersion == PROTOCOL_VERSION) { "Unsupported protocol ${envelope.protocolVersion}" }
        require(envelope.payloadSchemaVersion == 1) { "Unsupported payload schema" }
        require(envelope.messageId.length in 1..128) { "Invalid message id" }
        return envelope
    }

    fun <T> decodePayload(envelope: ProtocolEnvelope, serializer: KSerializer<T>): T =
        CanonicalData.json.decodeFromJsonElement(serializer, envelope.payload)
}
