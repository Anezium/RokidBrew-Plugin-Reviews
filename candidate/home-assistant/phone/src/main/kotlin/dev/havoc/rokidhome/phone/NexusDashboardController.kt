package dev.havoc.rokidhome.phone

import dev.havoc.rokidhome.shared.model.PageConfig
import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.RuntimeValue
import dev.havoc.rokidhome.shared.model.ValueSource
import dev.havoc.rokidhome.shared.model.WidgetConfig
import dev.havoc.rokidhome.shared.model.WidgetType
import dev.havoc.rokidhome.shared.state.DynamicPageState
import dev.havoc.rokidhome.shared.state.GlassesControlFocus
import dev.havoc.rokidhome.shared.state.GlassesNavigation

data class DashboardLine(
    val text: String,
    val badge: String? = null,
    val trail: List<String> = emptyList(),
)

data class DashboardPresentation(
    val title: String,
    val lines: List<DashboardLine>,
    val footer: String,
    val contentKey: String,
    val handlesBack: Boolean,
)

sealed interface DashboardTapResult {
    data object Render : DashboardTapResult
    data object Ignored : DashboardTapResult
    data class Execute(val widgetId: String, val toggleOn: Boolean? = null) : DashboardTapResult
}

data class DashboardSliderChange(val widgetId: String, val value: Double, val direction: Int)

/** Local page and focus state driven only by Nexus input callbacks. */
class NexusDashboardController {
    private var configuration: PublishedConfiguration? = null
    private var values: Map<String, RuntimeValue> = emptyMap()
    private var dynamic: DynamicPageState? = null
    private var navigation: GlassesNavigation? = null
    private var focus = GlassesControlFocus()
    private var actionInFlightWidgetId: String? = null
    private val optimisticToggleStates = mutableMapOf<String, Boolean>()
    private val optimisticSliderValues = mutableMapOf<String, Double>()
    private var adjustingSliderWidgetId: String? = null
    private var message: String? = null

    val selectedIndex: Int get() = navigation?.selectedIndex ?: 0
    val pageCount: Int get() = navigation?.pageIds?.size ?: 0
    val selectedWidgetId: String? get() = focus.selectedWidgetId
    val activeContextPageId: String? get() = dynamic?.activePageId
    val pendingContextPageId: String? get() = dynamic?.pendingPageId
    val currentPageId: String? get() = currentPage()?.id

    fun setConfiguration(config: PublishedConfiguration?) {
        if (config == null) {
            configuration = null
            values = emptyMap()
            dynamic = null
            navigation = null
            focus = GlassesControlFocus()
            optimisticToggleStates.clear()
            optimisticSliderValues.clear()
            adjustingSliderWidgetId = null
            return
        }
        if (configuration?.configVersion == config.configVersion && configuration?.checksum == config.checksum) {
            configuration = config
            return
        }
        configuration = config
        values = emptyMap()
        dynamic = DynamicPageState(config.defaultPageId)
        navigation = navigationFor(config, config.defaultPageId)
        focus = GlassesControlFocus()
        actionInFlightWidgetId = null
        optimisticToggleStates.clear()
        optimisticSliderValues.clear()
        adjustingSliderWidgetId = null
        message = null
    }

    fun setValues(updated: Map<String, RuntimeValue>) {
        val previous = values
        values = updated
        configuration?.pages?.flatMap(PageConfig::widgets)?.forEach { widget ->
            val stateKey = "${widget.id}.state"
            val state = updated[stateKey]
            if (state != null && !state.stale && state.updatedAtEpochMs != previous[stateKey]?.updatedAtEpochMs) {
                optimisticToggleStates.remove(widget.id)
            }
            val progressKey = "${widget.id}.progress"
            val progress = updated[progressKey]
            if (
                widget.id != adjustingSliderWidgetId &&
                progress != null && !progress.stale &&
                progress.updatedAtEpochMs != previous[progressKey]?.updatedAtEpochMs
            ) {
                optimisticSliderValues.remove(widget.id)
            }
        }
    }

    fun setContextPage(pageId: String?) {
        val config = configuration ?: return
        if (pageId == null || config.pages.none { it.id == pageId }) return
        val oldDynamic = dynamic ?: DynamicPageState(config.defaultPageId)
        val next = oldDynamic.receive(pageId, navigation?.selectedIndex == 0)
        dynamic = next
        if (next.activePageId != oldDynamic.activePageId) {
            navigation = navigationForCurrentSelection(config, next.activePageId)
            focus = GlassesControlFocus()
        }
    }

    fun open() {
        commitPending()
        val config = configuration ?: return
        navigation = navigationFor(config, dynamic?.activePageId ?: config.defaultPageId)
        focus = GlassesControlFocus()
        actionInFlightWidgetId = null
        optimisticToggleStates.clear()
        optimisticSliderValues.clear()
        adjustingSliderWidgetId = null
        message = null
    }

    fun close() {
        commitPending()
        focus = GlassesControlFocus()
        actionInFlightWidgetId = null
        adjustingSliderWidgetId = null
    }

    fun move(delta: Int): DashboardSliderChange? {
        if (delta == 0) return null
        val page = currentPage() ?: return null
        adjustingSliderWidgetId?.let { widgetId ->
            val widget = page.widgets.firstOrNull { it.id == widgetId && it.type == WidgetType.SLIDER }
                ?: run {
                    adjustingSliderWidgetId = null
                    return null
                }
            val range = sliderRange(widget)
            val next = (sliderValue(widget) + delta * range.step).coerceIn(range.minimum, range.maximum)
            optimisticSliderValues[widget.id] = next
            message = null
            return DashboardSliderChange(widget.id, next, delta.coerceIn(-1, 1))
        }
        if (focus.active) {
            focus = focus.move(delta, page.actionableIds())
            message = null
            return null
        }
        val old = navigation ?: return null
        if (old.selectedIndex == 0 && delta > 0) commitPending()
        navigation = navigation?.swipe(delta)
        focus = GlassesControlFocus()
        message = null
        return null
    }

    fun tap(actionsEnabled: Boolean): DashboardTapResult {
        val page = currentPage() ?: return DashboardTapResult.Ignored
        val actionable = page.widgets.filter(WidgetConfig::isActionable)
        if (!focus.active) {
            focus = focus.enter(actionable.map(WidgetConfig::id))
            message = if (focus.active) null else "На странице нет действий"
            return DashboardTapResult.Render
        }
        val widget = actionable.firstOrNull { it.id == focus.selectedWidgetId }
            ?: run {
                focus = GlassesControlFocus()
                return DashboardTapResult.Render
            }
        if (widget.type == WidgetType.SLIDER && adjustingSliderWidgetId == widget.id) {
            adjustingSliderWidgetId = null
            message = null
            return DashboardTapResult.Render
        }
        if (!actionsEnabled) {
            message = "Home Assistant offline"
            return DashboardTapResult.Render
        }
        if (widget.type == WidgetType.SLIDER) {
            if (!sliderValueIsFresh(widget)) {
                message = "Обновление состояния…"
                return DashboardTapResult.Render
            }
            adjustingSliderWidgetId = widget.id
            message = null
            return DashboardTapResult.Render
        }
        if (actionInFlightWidgetId != null) return DashboardTapResult.Ignored
        actionInFlightWidgetId = widget.id
        message = "Выполняется…"
        return if (widget.type == WidgetType.TOGGLE) {
            val checked = toggleChecked(widget) ?: run {
                actionInFlightWidgetId = null
                message = "Обновление состояния…"
                return DashboardTapResult.Render
            }
            DashboardTapResult.Execute(widget.id, toggleOn = !checked)
        } else {
            DashboardTapResult.Execute(widget.id)
        }
    }

    /** Returns true only when the root surface should close. */
    fun back(): Boolean {
        if (!focus.active) return true
        if (adjustingSliderWidgetId != null) {
            adjustingSliderWidgetId = null
            message = null
            return false
        }
        focus = GlassesControlFocus()
        message = null
        return false
    }

    fun actionFinished(resultMessage: String, appliedToggleState: Boolean? = null) {
        val widgetId = actionInFlightWidgetId
        if (widgetId != null && appliedToggleState != null) {
            optimisticToggleStates[widgetId] = appliedToggleState
        }
        actionInFlightWidgetId = null
        message = resultMessage
    }

    fun sliderFinished(resultMessage: String) {
        message = resultMessage
    }

    fun presentation(homeAssistantOnline: Boolean): DashboardPresentation {
        val config = configuration
        val page = currentPage()
        if (config == null || page == null) {
            return DashboardPresentation(
                title = "Home Assistant",
                lines = listOf(DashboardLine("Откройте настройки и опубликуйте конфигурацию")),
                footer = "Back to close",
                contentKey = "ha-empty",
                handlesBack = false,
            )
        }
        val rendered = page.widgets.flatMap(::renderWidget).ifEmpty {
            listOf(DashboardLine("Нет виджетов"))
        }.take(MAX_CARD_ROWS)
        val position = "${selectedIndex + 1}/${pageCount.coerceAtLeast(1)}"
        val mode = when {
            adjustingSliderWidgetId != null -> "swipe: значение · tap/back: готово"
            focus.active -> "swipe: выбор · tap: action · back"
            else -> "swipe: страницы · tap: войти · back"
        }
        val status = buildList {
            add(position)
            add(mode)
            if (!homeAssistantOnline) add("HA offline")
            if (pendingContextPageId != null) add("page 0 pending")
            message?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" · ").limit(MAX_TEXT)
        return DashboardPresentation(
            title = page.name.limit(MAX_TITLE).ifBlank { "Home Assistant" },
            lines = rendered,
            footer = status,
            contentKey = "ha-${config.configVersion}-${page.id}".limit(MAX_CONTENT_KEY),
            handlesBack = focus.active,
        )
    }

    private fun renderWidget(widget: WidgetConfig): List<DashboardLine> = when (widget.type) {
        WidgetType.TEXT -> listOfNotNull(
            widget.primary?.let { DashboardLine(resolve(widget, "primary", it).display()) },
            widget.secondary?.let { DashboardLine(resolve(widget, "secondary", it).display()) },
        )
        WidgetType.STATUS -> listOf(
            DashboardLine(
                text = resolve(widget, "label", widget.label).display(),
                badge = resolve(widget, "state", widget.state).display(MAX_BADGE),
            ),
        )
        WidgetType.BUTTON -> listOf(
            DashboardLine(
                text = focusMarker(widget) + resolve(widget, "label", widget.label).display(MAX_TEXT - 2),
                badge = if (actionInFlightWidgetId == widget.id) "WAIT" else "ACTION",
            ),
        )
        WidgetType.TOGGLE -> {
            val checked = toggleChecked(widget)
            val (labelSlot, label) = when {
                checked == true && widget.primary != null -> "primary" to widget.primary
                checked == false && widget.secondary != null -> "secondary" to widget.secondary
                else -> "label" to widget.label
            }
            listOf(
                DashboardLine(
                    text = focusMarker(widget) + resolve(widget, labelSlot, label).display(MAX_TEXT - 2),
                    badge = when {
                        actionInFlightWidgetId == widget.id -> "WAIT"
                        checked == true -> "ON"
                        checked == false -> "OFF"
                        else -> "SYNC"
                    },
                ),
            )
        }
        WidgetType.PROGRESS -> {
            val raw = resolve(widget, "progress", widget.progress)
            val number = raw.value.toFloatOrNull()
            val percent = number?.let { if (it <= 1f) it * 100 else it }?.toInt()?.coerceIn(0, 100)
            listOf(
                DashboardLine(
                    text = resolve(widget, "label", widget.label).display(),
                    badge = percent?.let { "$it%" } ?: raw.display(MAX_BADGE),
                ),
            )
        }
        WidgetType.SLIDER -> {
            val range = sliderRange(widget)
            val raw = sliderValue(widget)
            val percent = if (range.maximum > range.minimum) {
                (((raw - range.minimum) / (range.maximum - range.minimum)) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            listOf(
                DashboardLine(
                    text = focusMarker(widget) + resolve(widget, "label", widget.label).display(MAX_TEXT - 2),
                    badge = if (sliderValueIsFresh(widget)) "$percent%" else "SYNC",
                ),
            )
        }
    }

    private fun focusMarker(widget: WidgetConfig): String =
        if (focus.selectedWidgetId == widget.id) "> " else "  "

    private fun toggleChecked(widget: WidgetConfig): Boolean? {
        optimisticToggleStates[widget.id]?.let { return it }
        val state = resolve(widget, "state", widget.state)
        if (state.stale) return null
        return state.value.lowercase() in TOGGLE_ON_VALUES
    }

    private fun sliderValueIsFresh(widget: WidgetConfig): Boolean =
        optimisticSliderValues.containsKey(widget.id) || !resolve(widget, "progress", widget.progress).stale

    private fun sliderValue(widget: WidgetConfig): Double =
        optimisticSliderValues[widget.id]
            ?: resolve(widget, "progress", widget.progress).value.toDoubleOrNull()
            ?: sliderRange(widget).minimum

    private fun sliderRange(widget: WidgetConfig): SliderRange {
        val minimum = resolve(widget, "minimum", widget.minimum).value.toDoubleOrNull() ?: 0.0
        val maximum = resolve(widget, "maximum", widget.maximum).value.toDoubleOrNull()?.takeIf { it > minimum } ?: 1.0
        val step = resolve(widget, "step", widget.step).value.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: ((maximum - minimum) / 20.0)
        return SliderRange(minimum, maximum, step)
    }

    private fun resolve(widget: WidgetConfig, slot: String, source: ValueSource?): RuntimeValue {
        val fallback = when (source) {
            is ValueSource.Literal -> source.value
            is ValueSource.Entity -> source.fallback
            is ValueSource.Template -> source.fallback
            null -> "—"
        }
        return values["${widget.id}.$slot"]
            ?: RuntimeValue(fallback, stale = source !is ValueSource.Literal, updatedAtEpochMs = 0)
    }

    private fun RuntimeValue.display(max: Int = MAX_TEXT): String =
        (value + if (stale) " ⟳" else "").limit(max)

    private fun currentPage(): PageConfig? {
        val config = configuration ?: return null
        val nav = navigation ?: return null
        val pageId = if (nav.selectedIndex == 0) {
            dynamic?.activePageId ?: config.defaultPageId
        } else {
            nav.pageIds[nav.selectedIndex]
        }
        return config.pages.firstOrNull { it.id == pageId }
    }

    private fun commitPending() {
        val config = configuration ?: return
        val old = dynamic ?: DynamicPageState(config.defaultPageId)
        val next = old.commitPending()
        dynamic = next
        if (next.activePageId != old.activePageId) {
            navigation = navigationForCurrentSelection(config, next.activePageId)
        }
    }

    private fun navigationForCurrentSelection(
        config: PublishedConfiguration,
        activePageId: String,
    ): GlassesNavigation {
        val old = navigation
        val selectedPageId = when {
            old == null || old.selectedIndex == 0 -> DYNAMIC_SLOT
            else -> old.pageIds[old.selectedIndex]
        }
        return navigationFor(config, activePageId, selectedPageId)
    }

    private fun PageConfig.actionableIds(): List<String> =
        widgets.filter(WidgetConfig::isActionable).map(WidgetConfig::id)

    companion object {
        private const val DYNAMIC_SLOT = "__dynamic__"
        private const val MAX_TITLE = 120
        private const val MAX_TEXT = 240
        private const val MAX_BADGE = 24
        private const val MAX_CONTENT_KEY = 128
        private const val MAX_CARD_ROWS = 64
        private val TOGGLE_ON_VALUES = setOf("on", "true", "1", "yes")

        private fun navigationFor(
            config: PublishedConfiguration,
            activePageId: String,
            selectedPageId: String = DYNAMIC_SLOT,
        ): GlassesNavigation {
            val pageIds = listOf(DYNAMIC_SLOT) + config.pages.map(PageConfig::id)
                .filterNot { it == activePageId }
            val selectedIndex = when {
                selectedPageId == DYNAMIC_SLOT || selectedPageId == activePageId -> 0
                else -> pageIds.indexOf(selectedPageId).takeIf { it >= 0 } ?: 0
            }
            return GlassesNavigation(pageIds = pageIds, selectedIndex = selectedIndex)
        }
    }

    private data class SliderRange(val minimum: Double, val maximum: Double, val step: Double)
}

private fun WidgetConfig.isActionable(): Boolean =
    type == WidgetType.BUTTON || type == WidgetType.TOGGLE || type == WidgetType.SLIDER

private fun String.limit(max: Int): String = if (length <= max) this else take(max - 1) + "…"
