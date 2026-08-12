package com.beyondlevi.nexus.plugin.agenda

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** A calendar the phone syncs, as shown in the settings picker. */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val visible: Boolean,
)

/**
 * Reads the phone's calendar provider ([CalendarContract]).
 *
 * This is the whole data layer of v0.1: every calendar the phone syncs — Google,
 * Exchange/Outlook, CalDAV through DAVx5, local ones — lands in the same provider,
 * and the `Instances` table already expands recurrence rules for a time window,
 * which is the expensive part to reimplement over a raw ICS/API feed.
 * Adding a network source later means implementing another producer of
 * [AgendaEvent]; nothing above this class changes.
 */
class CalendarRepository(private val context: Context) {

    fun hasAccess(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Every calendar on the device, for the settings picker. */
    fun calendars(): List<CalendarInfo> {
        if (!hasAccess()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
        )
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty(),
                                accountName = cursor.getString(2).orEmpty(),
                                color = cursor.getInt(3),
                                visible = cursor.getInt(4) == 1,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Instances starting or still running at [nowMs] and beginning within
     * [horizonDays], ordered by start time.
     *
     * [selectedCalendarIds] is `null` when the wearer never picked calendars (use
     * every visible one) and empty when they turned all of them off — which is an
     * empty agenda, not a reset to the default.
     */
    fun agenda(
        nowMs: Long,
        horizonDays: Int,
        selectedCalendarIds: Set<Long>?,
        includeAllDay: Boolean,
        hideDeclined: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = MAX_EVENTS,
    ): List<AgendaEvent> {
        if (!hasAccess()) return emptyList()
        if (selectedCalendarIds != null && selectedCalendarIds.isEmpty()) return emptyList()
        val windowEnd = nowMs + TimeUnit.DAYS.toMillis(horizonDays.toLong())
        // Instances are clipped to the query window, so an all-day or long event
        // that started yesterday is still returned with BEGIN before nowMs.
        val windowStart = nowMs - TimeUnit.DAYS.toMillis(1)

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { ContentUris.appendId(it, windowStart); ContentUris.appendId(it, windowEnd); it }
            .build()

        val selection = buildList {
            add("(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})")
            if (hideDeclined) {
                add("(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})")
            }
            if (!includeAllDay) add("${CalendarContract.Instances.ALL_DAY} = 0")
            if (selectedCalendarIds == null) {
                add("${CalendarContract.Instances.VISIBLE} = 1")
            } else {
                add("${CalendarContract.Instances.CALENDAR_ID} IN (${selectedCalendarIds.joinToString(",")})")
            }
        }.joinToString(" AND ")

        val events = runCatching {
            context.contentResolver.query(
                uri,
                PROJECTION,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toAgendaEvent(zone))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())

        return events
            .filter { it.endMs > nowMs && it.startMs <= windowEnd }
            .sortedWith(compareBy({ it.startMs }, { it.title }))
            .take(limit)
    }

    /**
     * Who was invited, organiser first. Queried per event rather than joined into
     * the agenda query: the wearer only ever looks at one event's guest list, and
     * a join would carry every attendee of every event over the bus for nothing.
     */
    fun attendees(eventId: Long): List<AgendaAttendee> {
        if (!hasAccess() || eventId <= 0L) return emptyList()
        val projection = arrayOf(
            CalendarContract.Attendees.ATTENDEE_NAME,
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_STATUS,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
            CalendarContract.Attendees.ATTENDEE_TYPE,
        )
        val attendees = runCatching {
            context.contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                projection,
                "${CalendarContract.Attendees.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0).orEmpty().trim()
                        val email = cursor.getString(1).orEmpty().trim()
                        if (name.isEmpty() && email.isEmpty()) continue
                        add(
                            AgendaAttendee(
                                name = name.ifEmpty { email },
                                email = email.ifEmpty { null },
                                rsvp = rsvpOf(cursor.getInt(2)),
                                organizer = cursor.getInt(3) ==
                                    CalendarContract.Attendees.RELATIONSHIP_ORGANIZER,
                                self = false,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())

        return attendees
            .distinctBy { (it.email ?: it.name).lowercase() }
            .sortedWith(compareBy({ !it.organizer }, { it.name.lowercase() }))
    }

    private fun rsvpOf(status: Int): AgendaRsvp = when (status) {
        CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AgendaRsvp.ACCEPTED
        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AgendaRsvp.DECLINED
        CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AgendaRsvp.TENTATIVE
        else -> AgendaRsvp.PENDING
    }

    private fun android.database.Cursor.toAgendaEvent(zone: ZoneId): AgendaEvent {
        val allDay = getInt(4) == 1
        val rawBegin = getLong(2)
        val rawEnd = getLong(3)
        return AgendaEvent(
            id = "${getLong(0)}@$rawBegin",
            eventId = getLong(0),
            title = getString(1).orEmpty(),
            startMs = if (allDay) localMidnight(rawBegin, zone) else rawBegin,
            endMs = if (allDay) localMidnight(rawEnd, zone) else rawEnd,
            allDay = allDay,
            location = getString(5),
            description = getString(6),
            calendarName = getString(7),
        )
    }

    /**
     * All-day instances are stored at UTC midnight of the event's date; the wall
     * clock day is what the wearer means, so re-anchor it to local midnight.
     */
    private fun localMidnight(utcMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(utcMs)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    companion object {
        const val MAX_EVENTS = 120

        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
    }
}
