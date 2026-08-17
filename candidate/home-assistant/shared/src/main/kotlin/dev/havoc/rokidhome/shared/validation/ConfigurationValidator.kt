package dev.havoc.rokidhome.shared.validation

import dev.havoc.rokidhome.shared.model.CONFIG_SCHEMA_VERSION
import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import dev.havoc.rokidhome.shared.model.MAX_PAGES
import dev.havoc.rokidhome.shared.model.MAX_WIDGETS_PER_PAGE
import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.SLIDER_VALUE_PLACEHOLDER
import dev.havoc.rokidhome.shared.model.ValueSource
import dev.havoc.rokidhome.shared.model.WidgetType
import dev.havoc.rokidhome.shared.model.actions
import dev.havoc.rokidhome.shared.model.valueSources
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ValidationIssue(val path: String, val message: String)

object ConfigurationValidator {
    private val idPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")
    private val actionPattern = Regex("[a-z0-9_]+\\.[a-z0-9_]+")

    fun validate(config: PublishedConfiguration): List<ValidationIssue> = buildList {
        if (config.schemaVersion != CONFIG_SCHEMA_VERSION) add(ValidationIssue("schemaVersion", "Unsupported schema"))
        if (config.configVersion <= 0) add(ValidationIssue("configVersion", "Must be positive"))
        if (config.pages.isEmpty()) add(ValidationIssue("pages", "At least one page is required"))
        if (config.pages.size > MAX_PAGES) add(ValidationIssue("pages", "Maximum $MAX_PAGES pages"))
        val pageIds = config.pages.map { it.id }
        if (pageIds.toSet().size != pageIds.size) add(ValidationIssue("pages", "Page ids must be unique"))
        if (config.defaultPageId !in pageIds) add(ValidationIssue("defaultPageId", "Default page does not exist"))
        config.pages.forEachIndexed { pageIndex, page ->
            val pagePath = "pages[$pageIndex]"
            if (!idPattern.matches(page.id)) add(ValidationIssue("$pagePath.id", "Invalid id"))
            if (page.name.isBlank() || page.name.length > 80) add(ValidationIssue("$pagePath.name", "Name must be 1..80 characters"))
            if (page.widgets.size > MAX_WIDGETS_PER_PAGE) add(ValidationIssue("$pagePath.widgets", "Maximum $MAX_WIDGETS_PER_PAGE widgets"))
            val widgetIds = page.widgets.map { it.id }
            if (widgetIds.toSet().size != widgetIds.size) add(ValidationIssue("$pagePath.widgets", "Widget ids must be unique within a page"))
            page.widgets.forEachIndexed { widgetIndex, widget ->
                val widgetPath = "$pagePath.widgets[$widgetIndex]"
                if (!idPattern.matches(widget.id)) add(ValidationIssue("$widgetPath.id", "Invalid id"))
                widget.valueSources().forEachIndexed { sourceIndex, source ->
                    validateSource(source, "$widgetPath.sources[$sourceIndex]")?.let(::add)
                }
                when (widget.type) {
                    WidgetType.BUTTON -> if (widget.action == null) add(ValidationIssue("$widgetPath.action", "Button requires an action"))
                    WidgetType.TOGGLE -> if (widget.onAction == null || widget.offAction == null) add(ValidationIssue(widgetPath, "Toggle requires on and off actions"))
                    WidgetType.PROGRESS -> if (widget.progress == null) add(ValidationIssue("$widgetPath.progress", "Progress source required"))
                    WidgetType.SLIDER -> {
                        if (widget.progress == null) add(ValidationIssue("$widgetPath.progress", "Slider value source required"))
                        if (widget.action == null && (widget.onAction == null || widget.offAction == null)) {
                            add(ValidationIssue(widgetPath, "Slider requires an absolute action or up and down actions"))
                        } else if (widget.action != null && !widget.action.data.containsSliderPlaceholder()) {
                            add(ValidationIssue("$widgetPath.action.data", "Slider action data must contain $SLIDER_VALUE_PLACEHOLDER"))
                        }
                    }
                    else -> Unit
                }
            }
        }
        config.actions().forEachIndexed { index, action -> validateAction(action, "actions[$index]")?.let(::add) }
        val ruleIds = config.contextRules.map { it.id }
        if (ruleIds.toSet().size != ruleIds.size) add(ValidationIssue("contextRules", "Rule ids must be unique"))
        config.contextRules.forEachIndexed { index, rule ->
            val path = "contextRules[$index]"
            if (!idPattern.matches(rule.id)) add(ValidationIssue("$path.id", "Invalid id"))
            if (rule.conditionTemplate.isBlank() || rule.conditionTemplate.length > 4_096) add(ValidationIssue("$path.conditionTemplate", "Template must be 1..4096 characters"))
            if (rule.pageId !in pageIds) add(ValidationIssue("$path.pageId", "Target page does not exist"))
            if (rule.activateAfterMs !in 0..60_000) add(ValidationIssue("$path.activateAfterMs", "Delay out of range"))
            if (rule.deactivateAfterMs !in 0..60_000) add(ValidationIssue("$path.deactivateAfterMs", "Delay out of range"))
        }
    }

    private fun validateSource(source: ValueSource, path: String): ValidationIssue? = when (source) {
        is ValueSource.Literal -> if (source.value.length > 1_024) ValidationIssue(path, "Literal too long") else null
        is ValueSource.Entity -> if (!source.entityId.contains('.')) ValidationIssue(path, "Invalid entity id") else null
        is ValueSource.Template -> if (source.template.isBlank() || source.template.length > 4_096) ValidationIssue(path, "Invalid template") else null
    }

    private fun validateAction(action: HomeAssistantAction, path: String): ValidationIssue? = when {
        !actionPattern.matches(action.action) -> ValidationIssue("$path.action", "Expected domain.action")
        action.target.size > 16 -> ValidationIssue("$path.target", "Too many target fields")
        canonicalJson(action.data).length > 16_384 -> ValidationIssue("$path.data", "Action data is too large")
        else -> null
    }
}

private fun JsonElement.containsSliderPlaceholder(): Boolean = when (this) {
    is JsonObject -> values.any(JsonElement::containsSliderPlaceholder)
    is JsonArray -> any(JsonElement::containsSliderPlaceholder)
    is JsonPrimitive -> isString && content == SLIDER_VALUE_PLACEHOLDER
}

object CanonicalData {
    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "kind"
        ignoreUnknownKeys = false
    }

    fun normalizeAction(action: HomeAssistantAction): HomeAssistantAction = action.copy(
        action = action.action.trim().lowercase(),
        target = action.target.toSortedMap().mapValues { it.value.trim() },
        data = canonicalize(action.data) as JsonObject,
    )

    fun actionHash(action: HomeAssistantAction): String = sha256(
        json.encodeToString(normalizeAction(action)),
    )

    fun withChecksum(config: PublishedConfiguration): PublishedConfiguration {
        val normalized = config.copy(
            pages = config.pages.map { page ->
                page.copy(widgets = page.widgets.map { widget ->
                    widget.copy(
                        action = widget.action?.let(::normalizeAction),
                        onAction = widget.onAction?.let(::normalizeAction),
                        offAction = widget.offAction?.let(::normalizeAction),
                    )
                })
            },
            checksum = "",
        )
        return normalized.copy(checksum = sha256(json.encodeToString(normalized)))
    }

    fun verifyChecksum(config: PublishedConfiguration): Boolean =
        config.checksum.isNotBlank() && withChecksum(config).checksum == config.checksum

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun canonicalJson(element: JsonElement): String = CanonicalData.json.encodeToString(
    JsonElement.serializer(),
    canonicalize(element),
)

private fun canonicalize(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
    is JsonArray -> JsonArray(element.map(::canonicalize))
    is JsonPrimitive -> element
}
