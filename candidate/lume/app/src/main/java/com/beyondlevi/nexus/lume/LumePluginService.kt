package com.beyondlevi.nexus.lume

import com.anezium.rokidbus.client.PluginRegistrationResult
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.client.plugin.NexusTimedLines
import com.anezium.rokidbus.shared.plugin.NexusInputEvent

/**
 * The single exported Nexus plugin service — a thin adapter. All domain logic
 * lives in [LumeRuntime] (and the pure [ReaderModel] / [LibrarySelection]); the
 * service only owns the surface session and forwards lifecycle + input.
 */
class LumePluginService : NexusPluginService() {
    private var surface: NexusSurfaceSession? = null
    private var runtime: LumeRuntime? = null

    private val host = object : LumeRuntime.Host {
        override fun showCard(card: NexusCard, show: Boolean) {
            val session = surfaceSession() ?: return
            if (show) session.showCard(card) else session.updateCard(card)
        }

        override fun showTimedLines(lines: NexusTimedLines, show: Boolean) {
            val session = surfaceSession() ?: return
            if (show) session.showTimedLines(lines) else session.updateTimedLines(lines)
        }

        override fun hide() {
            surface?.hide()
        }
    }

    override fun onNexusOpen() {
        surfaceSession()
        val rt = LumeRuntime(host, DocumentStore(applicationContext), SettingsStore(applicationContext))
        runtime = rt
        rt.open()
    }

    override fun onNexusClose() {
        runtime?.close()
        runtime = null
        surface = null
    }

    override fun onNexusInput(event: NexusInputEvent) {
        runtime?.input(event)
    }

    override fun onNexusRegistrationState(result: Int) {
        if (result == PluginRegistrationResult.APPROVED) {
            runtime?.registrationApproved()
        } else {
            runtime?.close()
            runtime = null
            surface = null
        }
    }

    private fun surfaceSession(): NexusSurfaceSession? =
        surface ?: nexusSurfaceSession(SURFACE_ID).also { surface = it }

    private companion object {
        const val SURFACE_ID = "lume"
    }
}
