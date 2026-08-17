package com.havoc.rokidbus.plugin.otps

import android.content.Context

internal class OtpSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun autoClose(): Boolean = prefs.getBoolean(KEY_AUTO_CLOSE, true)

    fun setAutoClose(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLOSE, value).apply()
    }

    fun durationSeconds(): Int = prefs.getInt(KEY_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)
        .coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)

    fun setDurationSeconds(value: Int) {
        prefs.edit()
            .putInt(KEY_DURATION_SECONDS, value.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS))
            .apply()
    }

    fun backup() = OtpSettingsBackup(enabled(), autoClose(), durationSeconds())

    fun restore(backup: OtpSettingsBackup) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, backup.enabled)
            .putBoolean(KEY_AUTO_CLOSE, backup.autoClose)
            .putInt(KEY_DURATION_SECONDS, backup.durationSeconds)
            .commit()
    }

    companion object {
        const val DEFAULT_DURATION_SECONDS = 12
        const val MIN_DURATION_SECONDS = 2
        const val MAX_DURATION_SECONDS = 45

        private const val PREFS = "otp_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_CLOSE = "auto_close"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
    }
}
