package com.beyondlevi.nexus.lume

import android.content.Context

/**
 * Plugin-owned preferences: default reading speed and UI language. Stored in
 * the plugin's own SharedPreferences file per the Nexus settings-kit convention.
 *
 * `defaultWpm` seeds a reader session; the reader also persists the last speed
 * used so a returning reader resumes at its own pace (parity with the original,
 * where the glasses persisted the last WPM).
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_plugin_lume", Context.MODE_PRIVATE)

    var defaultWpm: Int
        get() = prefs.getInt(KEY_DEFAULT_WPM, RsvpEngine.DEFAULT_WPM)
            .coerceIn(RsvpEngine.MIN_WPM, RsvpEngine.MAX_WPM)
        set(value) = prefs.edit()
            .putInt(KEY_DEFAULT_WPM, value.coerceIn(RsvpEngine.MIN_WPM, RsvpEngine.MAX_WPM))
            .apply()

    var lastWpm: Int
        get() = prefs.getInt(KEY_LAST_WPM, defaultWpm)
            .coerceIn(RsvpEngine.MIN_WPM, RsvpEngine.MAX_WPM)
        set(value) = prefs.edit()
            .putInt(KEY_LAST_WPM, value.coerceIn(RsvpEngine.MIN_WPM, RsvpEngine.MAX_WPM))
            .apply()

    /** UI language for the HUD surfaces: "en" or "pt". */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, if (value == "pt") "pt" else "en").apply()

    private companion object {
        const val KEY_DEFAULT_WPM = "default_wpm"
        const val KEY_LAST_WPM = "last_wpm"
        const val KEY_LANGUAGE = "language"
    }
}
