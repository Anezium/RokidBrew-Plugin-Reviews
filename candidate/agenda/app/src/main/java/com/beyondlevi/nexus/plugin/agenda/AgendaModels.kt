package com.beyondlevi.nexus.plugin.agenda

/**
 * A calendar entry normalized away from whatever source produced it (the Android
 * calendar provider today, an ICS feed or the Google Calendar API later).
 *
 * Times are absolute epoch millis. All-day entries keep [allDay] = true and their
 * [startMs]/[endMs] already converted to the local wall-clock day boundaries by
 * the source, so the rest of the plugin never has to think about the provider's
 * UTC-midnight convention.
 */
data class AgendaEvent(
    val id: String,
    /** Provider row id of the event, the key attendees are attached to. */
    val eventId: Long = 0L,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val location: String? = null,
    val description: String? = null,
    val calendarName: String? = null,
)

/** Where an attendee stands on the invitation. */
enum class AgendaRsvp { ACCEPTED, DECLINED, TENTATIVE, PENDING }

/**
 * Someone invited to the event. [name] falls back to the address when the
 * provider has no display name, which is common for external guests.
 */
data class AgendaAttendee(
    val name: String,
    val email: String?,
    val rsvp: AgendaRsvp,
    val organizer: Boolean,
    val self: Boolean,
)

/** HUD row weight. Mirrors NexusRowTone without dragging the SDK into unit tests. */
enum class AgendaTone { ALERT, NORMAL, DIM, BODY }

/**
 * One rendered HUD row: what the formatter produces and the service maps to the
 * SDK. [trail] is the right-hand meta of a list row (the only place a list row
 * shows a time); [badge] is the fixed label of a prose row — the hub ignores a
 * badge on a list row, so never put timing there.
 */
data class AgendaRow(
    val text: String,
    val badge: String? = null,
    val trail: List<String> = emptyList(),
    val sub: String? = null,
    val tone: AgendaTone = AgendaTone.NORMAL,
    /** Set when the row opens a page of its own; drives the detail cursor. */
    val target: AgendaDetailTarget? = null,
)

/**
 * Every user-visible word the formatter needs. Injected so the formatter stays a
 * pure JVM class (unit-testable) while the service fills it from string resources.
 */
data class AgendaLabels(
    val title: String = "Agenda",
    val now: String = "now",
    val today: String = "today",
    val tomorrow: String = "tomorrow",
    val allDay: String = "all day",
    val untilPrefix: String = "until",
    val noEvents: String = "No events in the next %d days",
    val noAccess: String = "Calendar access not granted",
    val noAccessHint: String = "Open Nexus > Plugins > Agenda on the phone",
    val loading: String = "Loading agenda...",
    val listFooter: String = "swipe - tap - back",
    val detailFooter: String = "back to list",
    val eventsCount: String = "%1\$d events - %2\$d days",
    val more: String = "+%d more",
    val untitled: String = "(untitled)",
    val detailWhere: String = "where",
    val detailNotes: String = "notes",
    val detailWho: String = "who",
    val participants: String = "Participants",
    val notes: String = "Notes",
    val participantsCount: String = "%1\$d people",
    val openHint: String = "tap to open",
    val organizer: String = "organizer",
    val you: String = "you",
    val rsvpAccepted: String = "yes",
    val rsvpDeclined: String = "no",
    val rsvpTentative: String = "maybe",
    val rsvpPending: String = "-",
    val pageOf: String = "page %1\$d/%2\$d",
    val pageFooter: String = "swipe to turn - back",
    val listOfFooter: String = "%1\$d/%2\$d - back",
)
