package dev.havoc.rokidhome.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val CONFIG_SCHEMA_VERSION = 1
const val MAX_PAGES = 32
const val MAX_WIDGETS_PER_PAGE = 24
const val SLIDER_VALUE_PLACEHOLDER = "\$value"

@Serializable
sealed interface ValueSource {
    @Serializable
    @SerialName("literal")
    data class Literal(val value: String) : ValueSource

    @Serializable
    @SerialName("entity")
    data class Entity(
        val entityId: String,
        val attribute: String? = null,
        val fallback: String = "—",
    ) : ValueSource

    @Serializable
    @SerialName("template")
    data class Template(
        val template: String,
        val fallback: String = "—",
    ) : ValueSource
}

@Serializable
enum class WidgetType { TEXT, STATUS, BUTTON, TOGGLE, PROGRESS, SLIDER }

@Serializable
data class HomeAssistantAction(
    val action: String,
    val target: Map<String, String> = emptyMap(),
    val data: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class WidgetConfig(
    val id: String,
    val type: WidgetType,
    val label: ValueSource? = null,
    val primary: ValueSource? = null,
    val secondary: ValueSource? = null,
    val icon: String? = null,
    val state: ValueSource? = null,
    val progress: ValueSource? = null,
    val minimum: ValueSource? = null,
    val maximum: ValueSource? = null,
    val step: ValueSource? = null,
    val action: HomeAssistantAction? = null,
    val onAction: HomeAssistantAction? = null,
    val offAction: HomeAssistantAction? = null,
)

@Serializable
data class PageConfig(
    val id: String,
    val name: String,
    val widgets: List<WidgetConfig> = emptyList(),
)

@Serializable
data class ContextRule(
    val id: String,
    val enabled: Boolean = true,
    val conditionTemplate: String,
    val pageId: String,
    val priority: Int,
    val order: Int,
    val activateAfterMs: Long = 500,
    val deactivateAfterMs: Long = 1_500,
)

@Serializable
data class PublishedConfiguration(
    val schemaVersion: Int = CONFIG_SCHEMA_VERSION,
    val configVersion: Long,
    val defaultPageId: String,
    val pages: List<PageConfig>,
    val contextRules: List<ContextRule> = emptyList(),
    val checksum: String = "",
)

@Serializable
data class RuntimeValue(
    val value: String,
    val stale: Boolean = false,
    val error: String? = null,
    val updatedAtEpochMs: Long,
)

fun WidgetConfig.valueSources(): List<ValueSource> =
    listOfNotNull(label, primary, secondary, state, progress, minimum, maximum, step)

fun PublishedConfiguration.actions(): List<HomeAssistantAction> = pages.flatMap { page ->
    page.widgets.flatMap { widget -> listOfNotNull(widget.action, widget.onAction, widget.offAction) }
}
