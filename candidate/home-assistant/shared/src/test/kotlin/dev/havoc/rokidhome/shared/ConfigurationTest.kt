package dev.havoc.rokidhome.shared

import dev.havoc.rokidhome.shared.model.ContextRule
import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import dev.havoc.rokidhome.shared.model.PageConfig
import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.ValueSource
import dev.havoc.rokidhome.shared.model.WidgetConfig
import dev.havoc.rokidhome.shared.model.WidgetType
import dev.havoc.rokidhome.shared.protocol.ConfigSnapshotPayload
import dev.havoc.rokidhome.shared.protocol.*
import dev.havoc.rokidhome.shared.validation.CanonicalData
import dev.havoc.rokidhome.shared.validation.ConfigurationValidator
import dev.havoc.rokidhome.shared.state.ActionRequestGate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.KSerializer

class ConfigurationTest {
    private fun config(version: Long = 1): PublishedConfiguration {
        val action = HomeAssistantAction(
            action = "media_player.media_pause",
            target = mapOf("entity_id" to "media_player.tv"),
            data = buildJsonObject { put("source", JsonPrimitive("glasses")) },
        )
        return PublishedConfiguration(
            configVersion = version,
            defaultPageId = "home",
            pages = listOf(
                PageConfig("home", "Home", listOf(WidgetConfig("title", WidgetType.TEXT, primary = ValueSource.Literal("Home")))),
                PageConfig("tv", "TV", listOf(WidgetConfig("pause", WidgetType.BUTTON, label = ValueSource.Literal("Pause"), action = action))),
            ),
            contextRules = listOf(ContextRule("tv_active", conditionTemplate = "{{ is_state('media_player.tv','playing') }}", pageId = "tv", priority = 100, order = 0)),
        )
    }

    @Test fun validConfigurationGetsStableChecksum() {
        val checked = CanonicalData.withChecksum(config())
        assertTrue(ConfigurationValidator.validate(checked).isEmpty())
        assertTrue(CanonicalData.verifyChecksum(checked))
        assertEquals(checked.checksum, CanonicalData.withChecksum(config()).checksum)
        assertNotEquals(checked.checksum, CanonicalData.withChecksum(config(2)).checksum)
    }

    @Test fun protocolRoundTripIsVersioned() {
        val checked = CanonicalData.withChecksum(config())
        val encoded = ProtocolCodec.encode("m1", PayloadTypes.CONFIG_SNAPSHOT, ConfigSnapshotPayload.serializer(), ConfigSnapshotPayload(checked), checked.configVersion)
        val envelope = ProtocolCodec.decodeEnvelope(encoded)
        val decoded = ProtocolCodec.decodePayload(envelope, ConfigSnapshotPayload.serializer())
        assertEquals(checked, decoded.configuration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedProtocolMessageIsRejected() {
        ProtocolCodec.decodeEnvelope("x".repeat(300_000))
    }

    @Test fun everyProtocolPayloadRoundTrips() {
        val checked = CanonicalData.withChecksum(config())
        roundTrip(PayloadTypes.CONFIG_ACCEPTED, ConfigAcceptedPayload.serializer(), ConfigAcceptedPayload(checked.checksum))
        roundTrip(PayloadTypes.CONFIG_REJECTED, ConfigRejectedPayload.serializer(), ConfigRejectedPayload("bad"))
        val value = dev.havoc.rokidhome.shared.model.RuntimeValue("on", updatedAtEpochMs = 12)
        roundTrip(PayloadTypes.STATE_PATCH, StatePatchPayload.serializer(), StatePatchPayload("tv.state", value))
        roundTrip(PayloadTypes.TEMPLATE_PATCH, TemplatePatchPayload.serializer(), TemplatePatchPayload("tv.label", value))
        roundTrip(PayloadTypes.PENDING_CONTEXT_PAGE, PendingContextPagePayload.serializer(), PendingContextPagePayload("tv", 4))
        roundTrip(PayloadTypes.CONNECTION_STATE, ConnectionStatePayload.serializer(), ConnectionStatePayload(true, true))
        roundTrip(PayloadTypes.RECONCILE, ReconcilePayload.serializer(), ReconcilePayload(1, checked.checksum))
        val action = checked.pages[1].widgets[0].action!!
        roundTrip(PayloadTypes.ACTION_REQUEST, ActionRequestPayload.serializer(), ActionRequestPayload("request", action, CanonicalData.actionHash(action)))
        roundTrip(PayloadTypes.ACTION_RESULT, ActionResultPayload.serializer(), ActionResultPayload("request", ActionStatus.SUCCESS))
    }

    @Test fun canonicalActionHashIgnoresMapOrderAndWhitespace() {
        val one = HomeAssistantAction(" media_player.media_pause ", linkedMapOf("entity_id" to " media_player.tv ", "device_id" to "abc"))
        val two = HomeAssistantAction("media_player.media_pause", linkedMapOf("device_id" to "abc", "entity_id" to "media_player.tv"))
        assertEquals(CanonicalData.actionHash(one), CanonicalData.actionHash(two))
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompatibleProtocolIsRejected() {
        ProtocolCodec.decodeEnvelope("""{"protocolVersion":99,"messageId":"m","payloadType":"reconcile","payloadSchemaVersion":1,"payload":{}}""")
    }

    private fun <T> roundTrip(type: String, serializer: KSerializer<T>, value: T) {
        val encoded = ProtocolCodec.encode("m-$type", type, serializer, value, 1)
        val envelope = ProtocolCodec.decodeEnvelope(encoded)
        assertEquals(value, ProtocolCodec.decodePayload(envelope, serializer))
    }

    @Test fun duplicateActionRequestIsRejectedBeforeSecondExecution() {
        val checked = CanonicalData.withChecksum(config())
        val action = checked.pages[1].widgets[0].action!!
        val request = ActionRequestPayload("same-request", action, CanonicalData.actionHash(action))
        val gate = ActionRequestGate()
        assertEquals(null, gate.accept(checked, checked.configVersion, request))
        assertEquals("Повторный запрос", gate.accept(checked, checked.configVersion, request))
        assertTrue(gate.accept(checked, checked.configVersion + 1, request.copy(requestId = "new"))!!.contains("Устаревшая"))
    }

    @Test fun sliderAcceptsAbsoluteOrRelativeActions() {
        val slider = WidgetConfig(
            id = "volume",
            type = WidgetType.SLIDER,
            label = ValueSource.Literal("Volume"),
            progress = ValueSource.Entity("media_player.tv", "volume_level"),
            action = HomeAssistantAction(
                "media_player.volume_set",
                data = buildJsonObject { put("volume_level", JsonPrimitive("\$value")) },
            ),
        )
        val value = PublishedConfiguration(configVersion = 1, defaultPageId = "home", pages = listOf(PageConfig("home", "Home", listOf(slider))))
        assertTrue(ConfigurationValidator.validate(value).isEmpty())

        val broken = value.copy(pages = listOf(PageConfig("home", "Home", listOf(slider.copy(action = HomeAssistantAction("media_player.volume_set"))))))
        assertTrue(ConfigurationValidator.validate(broken).any { it.path.endsWith("action.data") })

        val relative = slider.copy(
            action = null,
            onAction = HomeAssistantAction("remote.send_command"),
            offAction = HomeAssistantAction("remote.send_command"),
        )
        val relativeValue = value.copy(pages = listOf(PageConfig("home", "Home", listOf(relative))))
        assertTrue(ConfigurationValidator.validate(relativeValue).isEmpty())

        val missingDown = relativeValue.copy(
            pages = listOf(PageConfig("home", "Home", listOf(relative.copy(offAction = null)))),
        )
        assertTrue(ConfigurationValidator.validate(missingDown).any { it.path.endsWith("widgets[0]") })
    }
}
