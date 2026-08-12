package com.beyondlevi.nexus.plugin.agenda

import android.content.Context

/** Plugin state, in the plugin's own package (uninstalling removes it all). */
class AgendaPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** How far ahead the agenda looks. The product default is one week. */
    var horizonDays: Int
        get() = prefs.getInt(KEY_HORIZON, DEFAULT_HORIZON_DAYS).coerceIn(1, 31)
        set(value) = prefs.edit().putInt(KEY_HORIZON, value.coerceIn(1, 31)).apply()

    var includeAllDay: Boolean
        get() = prefs.getBoolean(KEY_ALL_DAY, true)
        set(value) = prefs.edit().putBoolean(KEY_ALL_DAY, value).apply()

    var hideDeclined: Boolean
        get() = prefs.getBoolean(KEY_HIDE_DECLINED, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_DECLINED, value).apply()

    /**
     * The chosen calendars, or `null` while the wearer has never chosen — which
     * means "every calendar the phone shows", the sane zero-config default.
     *
     * `null` and the empty set are deliberately different: turning every calendar
     * off is a choice ("show me nothing"), not a reset to the default. Absent key
     * vs. stored-empty is what tells them apart.
     */
    val calendarSelection: Set<Long>?
        get() = if (!prefs.contains(KEY_CALENDARS)) {
            null
        } else {
            prefs.getStringSet(KEY_CALENDARS, emptySet())
                .orEmpty()
                .mapNotNull(String::toLongOrNull)
                .toSet()
        }

    var selectedCalendarIds: Set<Long>
        get() = calendarSelection.orEmpty()
        set(value) = prefs.edit()
            .putStringSet(KEY_CALENDARS, value.map(Long::toString).toSet())
            .apply()

    fun toggleCalendar(id: Long, enabled: Boolean, allIds: Set<Long>) {
        val current = calendarSelection ?: allIds
        selectedCalendarIds = if (enabled) current + id else current - id
    }

    fun isCalendarEnabled(id: Long): Boolean =
        calendarSelection?.contains(id) ?: true

    private companion object {
        const val NAME = "nexus_plugin_agenda"
        const val KEY_HORIZON = "horizon_days"
        const val KEY_ALL_DAY = "include_all_day"
        const val KEY_HIDE_DECLINED = "hide_declined"
        const val KEY_CALENDARS = "calendar_ids"
        const val DEFAULT_HORIZON_DAYS = 7
    }
}
