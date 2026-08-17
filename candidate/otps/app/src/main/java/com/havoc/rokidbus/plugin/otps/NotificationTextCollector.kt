package com.havoc.rokidbus.plugin.otps

import android.app.Notification
import android.os.Build
import android.os.Parcelable

internal object NotificationTextCollector {
    fun collect(notification: Notification): List<CharSequence> {
        val extras = notification.extras
        val parts = mutableListOf<CharSequence>()
        listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ).forEach { key -> extras.getCharSequence(key)?.let(parts::add) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().forEach(parts::add)
        runCatching {
            val bundles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            }
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
                .sortedBy { it.timestamp.takeIf { timestamp -> timestamp > 0L } ?: Long.MAX_VALUE }
                .forEach { message ->
                    message.senderPerson?.name?.let(parts::add)
                    message.text?.let(parts::add)
                }
        }
        notification.tickerText?.let(parts::add)
        return parts.map { it.toString().trim() }.filter(String::isNotEmpty).distinct()
    }
}
