package com.havoc.rokidbus.plugin.otps

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.anezium.rokidbus.client.PluginRegistrationResult
import com.anezium.rokidbus.client.plugin.NexusActivity
import com.anezium.rokidbus.client.plugin.NexusActivityAction
import com.anezium.rokidbus.client.plugin.NexusActivityProgress
import com.anezium.rokidbus.client.plugin.NexusPluginCallbacks
import com.anezium.rokidbus.client.plugin.NexusPluginClient
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import org.json.JSONObject

/** Owns one short-lived bus connection for the currently visible OTP alert. */
internal class OtpAlertRuntime(context: Context) : NexusPluginCallbacks {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val settings = OtpSettings(appContext)
    private var client: NexusPluginClient? = null
    private var pending: OtpRecord? = null
    private var activeRecord: OtpRecord? = null
    private var activityStartedAtMs: Long? = null
    private var activityDurationMs: Long? = null
    private var activityTick: Runnable? = null
    private var wakeHeartbeat: Runnable? = null
    private var generation = 0

    fun show(record: OtpRecord) = onMain {
        if (OtpRuntimeControl.historyOpen || !settings.enabled()) return@onMain
        generation += 1
        val expectedGeneration = generation
        closeClient()
        pending = record
        client = NexusPluginClient.create(appContext, PLUGIN_ID, this).also(NexusPluginClient::connect)
        tryShowPending()
        main.postDelayed({
            if (generation == expectedGeneration && pending != null) {
                Log.i(TAG, "OTP alert delivery timed out")
                closeClient()
            }
        }, SHOW_TIMEOUT_MS)
    }

    fun shutdown() = onMain {
        generation += 1
        if (activeRecord != null) client?.endActivity()
        closeClient()
    }

    override fun onOpen() = Unit

    override fun onClose() = onMain { closeClient() }

    override fun onInput(event: NexusInputEvent) = Unit

    override fun onLinkState(state: Int) = onMain { tryShowPending() }

    override fun onRegistrationState(result: Int) = onMain {
        if (result == PluginRegistrationResult.APPROVED) tryShowPending() else closeClient()
    }

    override fun onActivityAction(id: String) = onMain {
        if (id == ACTION_CLOSE) dismiss()
    }

    override fun onActivityClosed(reason: String) = onMain { closeClient() }

    override fun onMessage(path: String, id: String, payload: JSONObject) = Unit

    private fun tryShowPending() {
        val record = pending ?: return
        val currentClient = client ?: return
        if (!currentClient.isApproved) return
        if (!currentClient.supportsActivitySurface) return

        val durationMs = if (settings.autoClose()) {
            settings.durationSeconds() * 1000L
        } else {
            null
        }
        val result = currentClient.startActivity(activity(record, durationMs, elapsedMs = 0L))
        Log.i(TAG, "OTP alert show mode=ACTIVITY timer=${durationMs != null} result=$result")
        if (result == NexusSdkResult.SENT) {
            pending = null
            activeRecord = record
            activityDurationMs = durationMs
            activityStartedAtMs = SystemClock.elapsedRealtime()
            // Activity start only opts into waking. Use one significant update
            // for the initial wake, then immediately settle back to the stable
            // panel presentation. Countdown ticks must stay ordinary: Nexus
            // deliberately renders every significant update as a flare or pulse.
            val initial = activity(record, durationMs, elapsedMs = 0L)
            currentClient.updateActivity(initial, significant = true)
            currentClient.updateActivity(initial, significant = false)
            scheduleActivityTick()
            scheduleWakeHeartbeat()
        } else if (result !in RETRYABLE_RESULTS) {
            closeClient()
        }
    }

    private fun activity(record: OtpRecord, durationMs: Long?, elapsedMs: Long): NexusActivity {
        val countdown = durationMs?.let { OtpCountdown.frame(it, elapsedMs) }
        return NexusActivity(
            glyph = "otps-key",
            primary = record.code,
            secondary = record.app.take(ACTIVITY_SECONDARY_CHARS),
            progress = countdown?.let { NexusActivityProgress.Percent(it.progressPercent) },
            eta = countdown?.let { "${it.remainingSeconds}s" },
            detail = listOf("Tap Close to dismiss"),
            actions = listOf(NexusActivityAction(ACTION_CLOSE, "cancel", "Close")),
            maxDurationMs = null,
            wakeDisplay = true,
        )
    }

    private fun scheduleActivityTick() {
        activityTick?.let(main::removeCallbacks)
        val expectedGeneration = generation
        val tick = Runnable {
            if (generation != expectedGeneration) return@Runnable
            val currentClient = client ?: return@Runnable
            val record = activeRecord ?: return@Runnable
            val startedAt = activityStartedAtMs ?: return@Runnable
            val elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val durationMs = activityDurationMs
            if (durationMs != null && elapsedMs >= durationMs) {
                dismiss()
                return@Runnable
            }
            val result = currentClient.updateActivity(
                activity(record, durationMs, elapsedMs),
                significant = false,
            )
            if (result == NexusSdkResult.SENT) {
                main.postDelayed(activityTick ?: return@Runnable, ACTIVITY_TICK_MS)
            } else if (result !in RETRYABLE_RESULTS) {
                Log.w(TAG, "OTP activity tick failed elapsedMs=$elapsedMs result=$result")
                closeClient()
            }
        }
        activityTick = tick
        main.postDelayed(tick, ACTIVITY_TICK_MS)
    }

    private fun scheduleWakeHeartbeat() {
        wakeHeartbeat?.let(main::removeCallbacks)
        val expectedGeneration = generation
        val heartbeat = Runnable {
            if (generation != expectedGeneration) return@Runnable
            val currentClient = client ?: return@Runnable
            val record = activeRecord ?: return@Runnable
            val startedAt = activityStartedAtMs ?: return@Runnable
            val elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val durationMs = activityDurationMs
            if (durationMs != null && elapsedMs >= durationMs) {
                dismiss()
                return@Runnable
            }
            val result = currentClient.updateActivity(
                activity(record, durationMs, elapsedMs),
                significant = true,
            )
            if (result == NexusSdkResult.SENT) {
                main.postDelayed(wakeHeartbeat ?: return@Runnable, WAKE_HEARTBEAT_MS)
            } else if (result !in RETRYABLE_RESULTS) {
                Log.w(TAG, "OTP wake heartbeat failed elapsedMs=$elapsedMs result=$result")
                closeClient()
            }
        }
        wakeHeartbeat = heartbeat
        main.postDelayed(heartbeat, WAKE_HEARTBEAT_MS)
    }

    private fun dismiss() {
        activityTick?.let(main::removeCallbacks)
        activityTick = null
        wakeHeartbeat?.let(main::removeCallbacks)
        wakeHeartbeat = null
        client?.endActivity()
        main.postDelayed(::closeClient, DISMISS_FALLBACK_MS)
    }

    private fun closeClient() {
        activityTick?.let(main::removeCallbacks)
        activityTick = null
        wakeHeartbeat?.let(main::removeCallbacks)
        wakeHeartbeat = null
        pending = null
        activeRecord = null
        activityStartedAtMs = null
        activityDurationMs = null
        val old = client
        client = null
        old?.close()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "NexusOtpsAlert"
        const val PLUGIN_ID = "otps"
        const val ACTION_CLOSE = "close"
        const val ACTIVITY_SECONDARY_CHARS = 28
        const val ACTIVITY_TICK_MS = 1_000L
        const val WAKE_HEARTBEAT_MS = 5_100L
        const val SHOW_TIMEOUT_MS = 5_000L
        const val DISMISS_FALLBACK_MS = 500L
        val RETRYABLE_RESULTS = setOf(NexusSdkResult.NOT_REGISTERED)
    }
}
