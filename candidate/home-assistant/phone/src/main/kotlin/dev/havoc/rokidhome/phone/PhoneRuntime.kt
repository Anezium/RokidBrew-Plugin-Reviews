package dev.havoc.rokidhome.phone

import android.content.Context
import dev.havoc.rokidhome.phone.data.AppDatabase
import dev.havoc.rokidhome.phone.data.ConfigurationRepository
import dev.havoc.rokidhome.phone.ha.ActionCallResult
import dev.havoc.rokidhome.phone.ha.HaConnectionState
import dev.havoc.rokidhome.phone.ha.HaEntity
import dev.havoc.rokidhome.phone.ha.HomeAssistantClient
import dev.havoc.rokidhome.phone.security.CredentialStore
import dev.havoc.rokidhome.shared.model.ContextRule
import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.RuntimeValue
import dev.havoc.rokidhome.shared.model.SLIDER_VALUE_PLACEHOLDER
import dev.havoc.rokidhome.shared.model.ValueSource
import dev.havoc.rokidhome.shared.model.WidgetConfig
import dev.havoc.rokidhome.shared.state.ContextSelector
import dev.havoc.rokidhome.shared.validation.CanonicalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

data class RuntimeStatus(
    val haState: HaConnectionState = HaConnectionState.DISCONNECTED,
    val message: String? = null,
    val publishedVersion: Long? = null,
)

/**
 * Process-local Home Assistant runtime shared by the Nexus plugin service and its
 * settings activities. Network work is reference-counted: a dormant plugin owns no
 * Home Assistant socket, timer loop, foreground service, or Hi Rokid connection.
 */
class PhoneRuntime private constructor(context: Context) {
    private val application = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = ConfigurationRepository(AppDatabase.get(application))
    val credentials = CredentialStore(application)
    val homeAssistant = HomeAssistantClient(scope)

    private val mutableStatus = MutableStateFlow(RuntimeStatus())
    val status: StateFlow<RuntimeStatus> = mutableStatus.asStateFlow()

    private val mutableConfiguration = MutableStateFlow<PublishedConfiguration?>(null)
    val configuration: StateFlow<PublishedConfiguration?> = mutableConfiguration.asStateFlow()

    private val mutableValues = MutableStateFlow<Map<String, RuntimeValue>>(emptyMap())
    val values: StateFlow<Map<String, RuntimeValue>> = mutableValues.asStateFlow()

    private val mutableContextPageId = MutableStateFlow<String?>(null)
    val contextPageId: StateFlow<String?> = mutableContextPageId.asStateFlow()

    private var activeOwners = 0
    private var templateJob: Job? = null
    private val lastPatches = mutableMapOf<String, String>()
    private val bootstrapPreferences =
        application.getSharedPreferences("configuration-bootstrap", Context.MODE_PRIVATE)

    init {
        scope.launch {
            repository.ensureSeeded()
            if (!bootstrapPreferences.getBoolean(STARTER_CONFIGURATION_V10, false)) {
                val changed = repository.installStarterConfiguration(
                    restoreMissingStarterContent = false,
                )
                if (changed || repository.currentPublished() == null) {
                    repository.publish()
                        .onSuccess {
                            bootstrapPreferences.edit()
                                .putBoolean(STARTER_CONFIGURATION_V10, true)
                                .apply()
                        }
                        .onFailure { error ->
                            mutableStatus.update {
                                it.copy(message = "Не удалось опубликовать базовую конфигурацию: ${error.message}")
                            }
                        }
                } else {
                    bootstrapPreferences.edit().putBoolean(STARTER_CONFIGURATION_V10, true).apply()
                }
            }
        }
        scope.launch {
            repository.published.filterNotNull().collect { row ->
                activate(
                    CanonicalData.json.decodeFromString(
                        PublishedConfiguration.serializer(),
                        row.json,
                    ),
                )
            }
        }
        scope.launch {
            homeAssistant.state.collect { state ->
                mutableStatus.update { current ->
                    val message = when (state) {
                        HaConnectionState.AUTH_ERROR ->
                            "Токен Home Assistant недействителен — сохраните новый long-lived access token"
                        HaConnectionState.ONLINE ->
                            current.message.takeUnless {
                                it?.startsWith("Токен Home Assistant недействителен") == true
                            }
                        else -> current.message
                    }
                    current.copy(haState = state, message = message)
                }
            }
        }
        scope.launch {
            homeAssistant.entities.collect(::emitEntityValues)
        }
    }

    @Synchronized
    fun acquire() {
        activeOwners += 1
        if (activeOwners != 1) return
        connectSavedCredentials()
        mutableConfiguration.value?.let(::restartTemplates)
    }

    @Synchronized
    fun release() {
        if (activeOwners == 0) return
        activeOwners -= 1
        if (activeOwners != 0) return
        templateJob?.cancel()
        templateJob = null
        homeAssistant.disconnect()
    }

    @Synchronized
    fun reconnect() {
        if (activeOwners == 0) return
        connectSavedCredentials()
        mutableConfiguration.value?.let(::restartTemplates)
    }

    suspend fun publish(): Result<PublishedConfiguration> = repository.publish()

    suspend fun execute(widgetId: String, toggleOn: Boolean? = null): ActionCallResult {
        val config = mutableConfiguration.value
            ?: return ActionCallResult.Failure("Нет опубликованной конфигурации")
        val widget = config.pages.asSequence()
            .flatMap { it.widgets.asSequence() }
            .firstOrNull { it.id == widgetId }
            ?: return ActionCallResult.Failure("Виджет отсутствует в опубликованной конфигурации")
        val action = when {
            toggleOn == true -> widget.onAction
            toggleOn == false -> widget.offAction
            else -> widget.action
        } ?: return ActionCallResult.Failure("Для виджета не настроено действие")
        if (homeAssistant.state.value != HaConnectionState.ONLINE) {
            return ActionCallResult.Failure("Home Assistant offline")
        }
        return homeAssistant.callAction(action)
    }

    suspend fun executeSlider(widgetId: String, value: Double, direction: Int): ActionCallResult {
        val config = mutableConfiguration.value
            ?: return ActionCallResult.Failure("Нет опубликованной конфигурации")
        val widget = config.pages.asSequence()
            .flatMap { it.widgets.asSequence() }
            .firstOrNull { it.id == widgetId }
            ?: return ActionCallResult.Failure("Виджет отсутствует в опубликованной конфигурации")
        val action = widget.action ?: when {
            direction > 0 -> widget.onAction
            direction < 0 -> widget.offAction
            else -> null
        } ?: return ActionCallResult.Failure("Для слайдера не настроено действие")
        if (homeAssistant.state.value != HaConnectionState.ONLINE) {
            return ActionCallResult.Failure("Home Assistant offline")
        }
        return if (widget.action != null) {
            homeAssistant.callAction(
                action.copy(data = replaceSliderValue(action.data, value) as JsonObject),
            )
        } else {
            homeAssistant.callAction(action)
        }
    }

    private fun connectSavedCredentials() {
        val saved = credentials.load()
        if (saved.homeAssistantUrl.isNotBlank() && saved.homeAssistantToken.isNotBlank()) {
            runCatching {
                homeAssistant.connect(saved.homeAssistantUrl, saved.homeAssistantToken)
            }.onFailure { failure ->
                mutableStatus.update { it.copy(message = failure.message) }
            }
        }
    }

    private fun activate(config: PublishedConfiguration) {
        mutableConfiguration.value = config
        mutableContextPageId.value = config.defaultPageId
        mutableValues.value = emptyMap()
        lastPatches.clear()
        mutableStatus.update {
            it.copy(publishedVersion = config.configVersion, message = null)
        }
        emitEntityValues(homeAssistant.entities.value)
        synchronized(this) {
            if (activeOwners > 0) restartTemplates(config)
        }
    }

    private fun restartTemplates(config: PublishedConfiguration) {
        templateJob?.cancel()
        templateJob = scope.launch { runTemplates(config) }
    }

    private suspend fun runTemplates(config: PublishedConfiguration) {
        val selector = ContextSelector(config.contextRules, config.defaultPageId)
        val sources = config.pages.flatMap { page ->
            page.widgets.flatMap { widget ->
                listOf(
                    "${widget.id}.label" to widget.label,
                    "${widget.id}.primary" to widget.primary,
                    "${widget.id}.secondary" to widget.secondary,
                    "${widget.id}.state" to widget.state,
                    "${widget.id}.progress" to widget.progress,
                    "${widget.id}.minimum" to widget.minimum,
                    "${widget.id}.maximum" to widget.maximum,
                    "${widget.id}.step" to widget.step,
                )
            }
        }.filter { it.second is ValueSource.Template }
        val grouped = sources.groupBy(
            keySelector = { (it.second as ValueSource.Template).template },
            valueTransform = { it.first },
        )
        val allTemplates = grouped.keys + config.contextRules.map(ContextRule::conditionTemplate)
        var lastEntities: Map<String, HaEntity>? = null
        var lastRenderAt = 0L

        while (currentCoroutineContext().isActive) {
            if (homeAssistant.state.value != HaConnectionState.ONLINE) {
                delay(500)
                continue
            }
            val now = System.currentTimeMillis()
            val entities = homeAssistant.entities.value
            if (entities != lastEntities || now - lastRenderAt >= 30_000) {
                lastEntities = entities
                lastRenderAt = now
                homeAssistant.renderTemplates(allTemplates)
                    .onSuccess { rendered ->
                        mutableStatus.update { status ->
                            if (status.message?.startsWith("Ошибка Jinja:") == true) {
                                status.copy(message = null)
                            } else {
                                status
                            }
                        }
                        grouped.forEach { (template, bindings) ->
                            rendered[template]?.let { value ->
                                bindings.forEach { emitValue(it, value) }
                            }
                        }
                        config.contextRules.forEach { rule ->
                            rendered[rule.conditionTemplate]?.let {
                                selector.update(rule.id, it.isTruthy(), now)
                            }
                        }
                    }
                    .onFailure { failure ->
                        mutableStatus.update {
                            it.copy(message = "Ошибка Jinja: ${failure.message}; сохранены последние значения")
                        }
                    }
            }
            mutableContextPageId.value = selector.refresh(now)
            delay(100)
        }
    }

    private fun emitEntityValues(entities: Map<String, HaEntity>) {
        val config = mutableConfiguration.value ?: return
        config.pages.forEach { page ->
            page.widgets.forEach { widget ->
                widget.sources().forEach { (binding, source) ->
                    val entitySource = source as? ValueSource.Entity ?: return@forEach
                    val entity = entities[entitySource.entityId]
                    val value = entity?.let {
                        entitySource.attribute?.let { attribute ->
                            it.attributes[attribute]?.jsonPrimitive?.contentOrNull
                        } ?: it.state
                    } ?: entitySource.fallback
                    emitValue(binding, value, stale = entity == null)
                }
            }
        }
    }

    private fun emitValue(binding: String, value: String, stale: Boolean = false) {
        val signature = "$value|$stale"
        synchronized(lastPatches) {
            if (lastPatches.put(binding, signature) == signature) return
        }
        mutableValues.update {
            it + (
                binding to RuntimeValue(
                    value = value,
                    stale = stale,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun WidgetConfig.sources(): List<Pair<String, ValueSource?>> = listOf(
        "${id}.label" to label,
        "${id}.primary" to primary,
        "${id}.secondary" to secondary,
        "${id}.state" to state,
        "${id}.progress" to progress,
        "${id}.minimum" to minimum,
        "${id}.maximum" to maximum,
        "${id}.step" to step,
    )

    private fun replaceSliderValue(element: JsonElement, value: Double): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, child) -> replaceSliderValue(child, value) })
        is JsonArray -> JsonArray(element.map { replaceSliderValue(it, value) })
        is JsonPrimitive -> if (element.isString && element.content == SLIDER_VALUE_PLACEHOLDER) {
            JsonPrimitive(value)
        } else {
            element
        }
    }

    companion object {
        private const val STARTER_CONFIGURATION_V10 = "starter-configuration-v10"

        @Volatile
        private var instance: PhoneRuntime? = null

        fun get(context: Context): PhoneRuntime = instance ?: synchronized(this) {
            instance ?: PhoneRuntime(context).also { instance = it }
        }
    }
}

private fun String.isTruthy() = trim().lowercase() in setOf("true", "on", "yes", "1")
