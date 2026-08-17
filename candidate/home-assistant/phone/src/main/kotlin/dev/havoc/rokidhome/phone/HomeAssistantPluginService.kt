package dev.havoc.rokidhome.phone

import android.util.Log
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusCardLine
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import dev.havoc.rokidhome.phone.ha.ActionCallResult
import dev.havoc.rokidhome.phone.ha.HaConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeAssistantPluginService : NexusPluginService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = NexusDashboardController()
    private lateinit var runtime: PhoneRuntime
    private var surface: NexusSurfaceSession? = null
    private var observationJob: Job? = null
    private var actionJob: Job? = null
    private var sliderJob: Job? = null
    private val pendingSliderChanges = ArrayDeque<DashboardSliderChange>()
    private var surfaceShown = false
    private var runtimeAcquired = false

    override fun onCreate() {
        runtime = PhoneRuntime.get(this)
        super.onCreate()
    }

    override fun onDestroy() {
        observationJob?.cancel()
        actionJob?.cancel()
        sliderJob?.cancel()
        pendingSliderChanges.clear()
        if (runtimeAcquired) {
            runtime.release()
            runtimeAcquired = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNexusOpen() {
        if (!runtimeAcquired) {
            runtime.acquire()
            runtimeAcquired = true
        }
        controller.setConfiguration(runtime.configuration.value)
        controller.setValues(runtime.values.value)
        controller.setContextPage(runtime.contextPageId.value)
        controller.open()
        surface = nexusSurfaceSession(SURFACE_ID)
        surfaceShown = false
        render(show = true)
        observeRuntime()
    }

    override fun onNexusClose() {
        observationJob?.cancel()
        observationJob = null
        actionJob?.cancel()
        actionJob = null
        sliderJob?.cancel()
        sliderJob = null
        pendingSliderChanges.clear()
        controller.close()
        surface?.hide()
        surface = null
        surfaceShown = false
        if (runtimeAcquired) {
            runtime.release()
            runtimeAcquired = false
        }
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                controller.move(1)?.let(::scheduleSlider)
                render(show = false)
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            -> {
                controller.move(-1)?.let(::scheduleSlider)
                render(show = false)
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> when (
                val result = controller.tap(
                    actionsEnabled = runtime.status.value.haState == HaConnectionState.ONLINE,
                )
            ) {
                is DashboardTapResult.Execute -> {
                    render(show = false)
                    execute(result)
                }
                DashboardTapResult.Render -> render(show = false)
                DashboardTapResult.Ignored -> Unit
            }
            KeyEvent.KEYCODE_BACK -> {
                if (controller.back()) surface?.hide() else render(show = false)
            }
        }
    }

    private fun observeRuntime() {
        observationJob?.cancel()
        observationJob = serviceScope.launch {
            combine(
                runtime.configuration,
                runtime.values,
                runtime.contextPageId,
                runtime.status,
            ) { configuration, values, contextPageId, status ->
                RuntimeFrame(configuration, values, contextPageId, status.haState)
            }.collect { frame ->
                controller.setConfiguration(frame.configuration)
                controller.setValues(frame.values)
                controller.setContextPage(frame.contextPageId)
                render(show = false)
            }
        }
    }

    private fun execute(request: DashboardTapResult.Execute) {
        if (actionJob?.isActive == true) return
        actionJob = serviceScope.launch {
            val result = runtime.execute(request.widgetId, request.toggleOn)
            val message = when (result) {
                ActionCallResult.Success -> "Готово"
                is ActionCallResult.Failure -> result.message
                ActionCallResult.TimeoutUnknown -> "Таймаут: результат неизвестен"
            }
            controller.actionFinished(
                resultMessage = message,
                appliedToggleState = request.toggleOn.takeIf { result == ActionCallResult.Success },
            )
            render(show = false)
        }
    }

    private fun scheduleSlider(change: DashboardSliderChange) {
        pendingSliderChanges.addLast(change)
        if (sliderJob?.isActive == true) return
        sliderJob = serviceScope.launch {
            delay(SLIDER_DEBOUNCE_MS)
            while (pendingSliderChanges.isNotEmpty()) {
                val next = pendingSliderChanges.removeFirst()
                val result = runtime.executeSlider(next.widgetId, next.value, next.direction)
                if (result !is ActionCallResult.Success) {
                    pendingSliderChanges.clear()
                    controller.sliderFinished(
                        when (result) {
                            is ActionCallResult.Failure -> result.message
                            ActionCallResult.TimeoutUnknown -> "Таймаут: результат неизвестен"
                            ActionCallResult.Success -> "Готово"
                        },
                    )
                    render(show = false)
                }
            }
        }
    }

    private fun render(show: Boolean) {
        val target = surface ?: return
        val presentation = controller.presentation(
            homeAssistantOnline = runtime.status.value.haState == HaConnectionState.ONLINE,
        )
        val card = NexusCard(
            title = presentation.title,
            lines = emptyList(),
            richLines = presentation.lines.map {
                NexusCardLine(text = it.text, badge = it.badge, trail = it.trail)
            },
            footer = presentation.footer,
            contentKey = presentation.contentKey,
            handlesBack = presentation.handlesBack,
        )
        val result = when {
            show -> target.showCard(card)
            surfaceShown -> target.updateCard(card)
            else -> return
        }
        if (show) surfaceShown = result == NexusSdkResult.SENT
        if (result != NexusSdkResult.SENT) {
            Log.i(TAG, "Nexus card ${if (show) "show" else "update"} refused: $result")
        }
    }

    private data class RuntimeFrame(
        val configuration: dev.havoc.rokidhome.shared.model.PublishedConfiguration?,
        val values: Map<String, dev.havoc.rokidhome.shared.model.RuntimeValue>,
        val contextPageId: String?,
        val haState: HaConnectionState,
    )

    private companion object {
        const val SURFACE_ID = "dashboard"
        const val TAG = "RokidHomeNexus"
        const val SLIDER_DEBOUNCE_MS = 140L
    }
}
