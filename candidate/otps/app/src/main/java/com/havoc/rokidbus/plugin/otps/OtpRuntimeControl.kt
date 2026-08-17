package com.havoc.rokidbus.plugin.otps

import android.os.Handler
import android.os.Looper

internal object OtpRuntimeControl {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var listener: OtpNotificationListener? = null

    @Volatile
    private var historyService: OtpPluginService? = null

    @Volatile
    var historyOpen: Boolean = false
        private set

    fun attach(service: OtpNotificationListener) {
        listener = service
    }

    fun detach(service: OtpNotificationListener) {
        if (listener === service) listener = null
    }

    fun historyOpened(service: OtpPluginService) {
        historyService = service
        historyOpen = true
        main.post { listener?.suspendAlert() }
    }

    fun historyClosed(service: OtpPluginService) {
        if (historyService === service) historyService = null
        historyOpen = false
    }

    fun notifyHistoryChanged() {
        main.post { historyService?.onHistoryChanged() }
    }

    fun settingsChanged() {
        main.post { listener?.onSettingsChanged() }
    }
}
