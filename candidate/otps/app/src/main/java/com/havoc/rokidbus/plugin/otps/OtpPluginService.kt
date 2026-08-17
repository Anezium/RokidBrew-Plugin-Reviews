package com.havoc.rokidbus.plugin.otps

import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.plugin.NexusInputEvent

/** Glasses-launched ten-code history. */
class OtpPluginService : NexusPluginService() {
    private lateinit var history: OtpHistoryStore
    private var surface: NexusSurfaceSession? = null

    override fun onCreate() {
        history = OtpHistoryStore(applicationContext)
        super.onCreate()
    }

    override fun onNexusOpen() {
        OtpRuntimeControl.historyOpened(this)
        surface = nexusSurfaceSession(SURFACE_ID)
        render(show = true)
    }

    override fun onNexusClose() {
        OtpRuntimeControl.historyClosed(this)
        surface = null
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        if (event.keyCode == KeyEvent.KEYCODE_BACK) surface?.hide()
    }

    internal fun onHistoryChanged() = render(show = false)

    private fun render(show: Boolean) {
        val currentSurface = surface ?: return
        val records = history.snapshot()
        val card = NexusCard(
            title = "OTPs",
            lines = if (records.isEmpty()) {
                listOf("No codes yet")
            } else {
                records.map { record -> "${record.app}: ${record.code}".take(MAX_LINE_CHARS) }
            },
            subtitle = if (records.isEmpty()) "History is empty" else "Last ${records.size} codes",
            footer = "Back to close",
            contentKey = records.joinToString("|") { "${it.app}:${it.code}" }.hashCode().toUInt().toString(16),
        )
        if (show) currentSurface.showCard(card) else currentSurface.updateCard(card)
    }

    private companion object {
        const val SURFACE_ID = "history"
        const val MAX_LINE_CHARS = 240
    }
}
