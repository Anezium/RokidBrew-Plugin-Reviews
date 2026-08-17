package com.havoc.rokidbus.plugin.otps

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class OtpNotificationListener : NotificationListenerService() {
    private val alertRuntime by lazy { OtpAlertRuntime(applicationContext) }
    private val settings by lazy { OtpSettings(applicationContext) }
    private val history by lazy { OtpHistoryStore(applicationContext) }
    private val deduplicator = OtpDeliveryDeduplicator()

    override fun onListenerConnected() {
        super.onListenerConnected()
        OtpRuntimeControl.attach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!settings.enabled() || sbn.packageName == packageName) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val detection = OtpParser.detect(*NotificationTextCollector.collect(sbn.notification).toTypedArray())
            ?: return
        val now = System.currentTimeMillis()
        if (!deduplicator.shouldDeliver(sbn.key, detection.code, now)) return

        val record = OtpRecord(
            app = appLabel(sbn.packageName).take(MAX_APP_LABEL_CHARS),
            code = detection.code,
            receivedAtMs = sbn.postTime.takeIf { it > 0L } ?: now,
        )
        history.add(record)
        OtpRuntimeControl.notifyHistoryChanged()
        Log.i(TAG, "OTP accepted package=${sbn.packageName} confidence=${detection.confidence} chars=${detection.code.length}")
        if (!OtpRuntimeControl.historyOpen) alertRuntime.show(record)
    }

    override fun onListenerDisconnected() {
        alertRuntime.shutdown()
        OtpRuntimeControl.detach(this)
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, OtpNotificationListener::class.java))
    }

    override fun onDestroy() {
        alertRuntime.shutdown()
        OtpRuntimeControl.detach(this)
        super.onDestroy()
    }

    internal fun suspendAlert() = alertRuntime.shutdown()

    internal fun onSettingsChanged() {
        if (!settings.enabled()) alertRuntime.shutdown()
    }

    private fun appLabel(sourcePackage: String): String = runCatching {
        val info = packageManager.getApplicationInfo(sourcePackage, 0)
        packageManager.getApplicationLabel(info).toString().trim()
    }.getOrNull().takeUnless { it.isNullOrBlank() } ?: sourcePackage.substringAfterLast('.')

    private companion object {
        const val TAG = "NexusOtpsListener"
        const val MAX_APP_LABEL_CHARS = 64
    }
}
