package com.beyondlevi.nexus.plugin.agenda

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusCardLine
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusRowTone
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import java.util.Locale
import java.util.concurrent.Executors
import java.time.ZoneId

/**
 * The Agenda plugin: the wearer's next seven days as a HUD list.
 *
 * The service is only an adapter — the agenda itself comes from
 * [CalendarRepository], the rows from [AgendaFormatter] and the navigation from
 * [AgendaState], all of which are testable without a device.
 */
class AgendaPluginService : NexusPluginService() {

    private fun log(message: String) = android.util.Log.i("ROKIDBUS", "agenda: $message")

    private val state = AgendaState()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var repository: CalendarRepository
    private lateinit var prefs: AgendaPrefs
    private lateinit var labels: AgendaLabels

    private var surface: NexusSurfaceSession? = null
    private var events: List<AgendaEvent> = emptyList()
    private var shown = false
    private var accessGranted = true
    private var capped = false

    /**
     * Attendees are fetched per event, the first time a detail is rendered, and
     * cached for the session: the guest list is only ever needed for the one
     * event the wearer opened, and the provider query must not sit on the main
     * thread between an input and the surface it changes.
     */
    private val attendeeCache = mutableMapOf<Long, List<AgendaAttendee>>()
    private val attendeesLoading = mutableSetOf<Long>()

    private val refreshTick = object : Runnable {
        override fun run() {
            loadAgenda()
            main.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = CalendarRepository(this)
        prefs = AgendaPrefs(this)
        labels = AgendaLabels(
            title = getString(R.string.hud_title),
            now = getString(R.string.hud_now),
            today = getString(R.string.hud_today),
            tomorrow = getString(R.string.hud_tomorrow),
            allDay = getString(R.string.hud_all_day),
            untilPrefix = getString(R.string.hud_until),
            noEvents = getString(R.string.hud_no_events),
            noAccess = getString(R.string.hud_no_access),
            noAccessHint = getString(R.string.hud_no_access_hint),
            loading = getString(R.string.hud_loading),
            listFooter = getString(R.string.hud_list_footer),
            detailFooter = getString(R.string.hud_detail_footer),
            eventsCount = getString(R.string.hud_events_count),
            more = getString(R.string.hud_more),
            untitled = getString(R.string.hud_untitled),
            detailWhere = getString(R.string.hud_where),
            detailNotes = getString(R.string.hud_notes),
            detailWho = getString(R.string.hud_who),
            participants = getString(R.string.hud_participants),
            notes = getString(R.string.hud_notes_title),
            participantsCount = getString(R.string.hud_people_count),
            openHint = getString(R.string.hud_open_hint),
            organizer = getString(R.string.hud_organizer),
            rsvpAccepted = getString(R.string.hud_rsvp_yes),
            rsvpDeclined = getString(R.string.hud_rsvp_no),
            rsvpTentative = getString(R.string.hud_rsvp_maybe),
            rsvpPending = getString(R.string.hud_rsvp_pending),
            pageOf = getString(R.string.hud_page_of),
            pageFooter = getString(R.string.hud_page_footer),
            listOfFooter = getString(R.string.hud_position_footer),
        )
    }

    override fun onNexusOpen() {
        // PLUGIN_OPEN is re-entrant: always reset and re-show.
        state.reset()
        events = emptyList()
        shown = false
        surface = nexusSurfaceSession(SURFACE_ID)
        // Ack fast with a placeholder, then fill it in off the main thread — a
        // provider query must never sit between the open and the first surface.
        render()
        main.removeCallbacks(refreshTick)
        main.post(refreshTick)
    }

    override fun onNexusClose() {
        main.removeCallbacks(refreshTick)
        attendeeCache.clear()
        attendeesLoading.clear()
        surface?.hide()
        surface = null
        shown = false
        events = emptyList()
        state.reset()
    }

    override fun onDestroy() {
        main.removeCallbacks(refreshTick)
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        log("input keyCode=${event.keyCode} view=${state.view} index=${state.selectedIndex}")
        val effect = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> state.move(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> state.move(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> state.select()
            KeyEvent.KEYCODE_BACK -> state.back()
            else -> return
        }
        log("effect=$effect view=${state.view}")
        when (effect) {
            AgendaState.Effect.RENDER -> render()
            AgendaState.Effect.CLOSE -> {
                main.removeCallbacks(refreshTick)
                surface?.hide()
            }
            AgendaState.Effect.NONE -> Unit
        }
    }

    private fun loadAgenda() {
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        io.execute {
            val granted = repository.hasAccess()
            val loaded = if (granted) {
                repository.agenda(
                    nowMs = nowMs,
                    horizonDays = prefs.horizonDays,
                    selectedCalendarIds = prefs.calendarSelection,
                    includeAllDay = prefs.includeAllDay,
                    hideDeclined = prefs.hideDeclined,
                    zone = zone,
                )
            } else {
                emptyList()
            }
            val ordered = AgendaFormatter.prepareForHud(loaded, zone)
            main.post {
                if (surface == null) return@post
                val focusedId = events.getOrNull(state.selectedIndex)?.id
                accessGranted = granted
                capped = ordered.size >= CalendarRepository.MAX_EVENTS
                events = ordered
                state.setItems(
                    count = ordered.size,
                    keepIndex = ordered.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 }
                        ?: state.selectedIndex,
                )
                render()
            }
        }
    }

    private fun render() {
        val session = surface ?: return
        val card = when {
            !accessGranted -> messageCard(labels.noAccess, labels.noAccessHint, "agenda-no-access")
            events.isEmpty() && !shown -> messageCard(labels.loading, null, "agenda-loading")
            events.isEmpty() -> messageCard(
                String.format(Locale.ROOT, labels.noEvents, prefs.horizonDays),
                null,
                "agenda-empty",
            )
            state.view == AgendaView.DETAIL -> detailCard()
            state.view == AgendaView.PARTICIPANTS -> participantsCard()
            state.view == AgendaView.NOTES -> notesCard()
            else -> listCard()
        }
        if (shown) session.updateCard(card) else session.showCard(card).also { shown = true }
    }

    private fun listCard(): NexusCard {
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        val rows = AgendaFormatter.listRows(events, nowMs, zone, locale, labels)
        val page = AgendaFormatter.page(rows, state.selectedIndex)
        val firstOnPage = page.hiddenAbove
        return NexusCard(
            title = labels.title,
            lines = emptyList(),
            subtitle = AgendaFormatter.listSubtitle(
                eventCount = events.size,
                horizonDays = prefs.horizonDays,
                capped = capped,
                labels = labels,
            ),
            footer = AgendaFormatter.listFooter(state.selectedIndex, events.size, labels),
            contentKey = contentKey("list", state.selectedIndex, events.size),
            handlesBack = true,
            richLines = page.rows.mapIndexed { index, row ->
                row.toCardLine(selected = firstOnPage + index == state.selectedIndex)
            },
        )
    }

    private fun detailCard(): NexusCard {
        val event = events.getOrNull(state.selectedIndex) ?: return listCard()
        val attendees = attendeesFor(event)
        val rows = AgendaFormatter.detailRows(
            event = event,
            attendees = attendees,
            nowMs = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            locale = Locale.getDefault(),
            labels = labels,
        )
        state.setDetailContent(
            targets = rows.mapNotNull { it.target },
            participantCount = attendees.size,
            notesPageCount = AgendaFormatter.notesPages(event.description).size,
        )
        return NexusCard(
            title = event.title.ifBlank { labels.title }.take(MAX_TITLE),
            lines = emptyList(),
            subtitle = AgendaFormatter.detailSubtitle(
                event = event,
                selectedIndex = state.selectedIndex,
                total = events.size,
                zone = ZoneId.systemDefault(),
                locale = Locale.getDefault(),
                labels = labels,
            ),
            footer = AgendaFormatter.detailFooter(state.focusedTarget, labels),
            contentKey = contentKey("detail", state.selectedIndex, events.size),
            handlesBack = true,
            richLines = AgendaFormatter.markFocused(rows, state.focusedTarget).map {
                it.toCardLine(selected = it.target != null && it.target == state.focusedTarget)
            },
        )
    }

    /** The full guest list, one row each — the axis walks it, the hub windows it. */
    private fun participantsCard(): NexusCard {
        val event = events.getOrNull(state.selectedIndex) ?: return listCard()
        val attendees = attendeesFor(event)
        if (attendees.isEmpty()) return detailCard()
        val rows = AgendaFormatter.participantRows(attendees, labels)
        val page = AgendaFormatter.page(rows, state.participantIndex)
        val firstOnPage = page.hiddenAbove
        return NexusCard(
            title = labels.participants,
            lines = emptyList(),
            subtitle = event.title.ifBlank { labels.title }.take(MAX_SUBTITLE),
            footer = String.format(
                Locale.ROOT,
                labels.listOfFooter,
                state.participantIndex + 1,
                attendees.size,
            ),
            contentKey = contentKey("who", state.selectedIndex, attendees.size),
            handlesBack = true,
            richLines = page.rows.mapIndexed { index, row ->
                row.toCardLine(selected = firstOnPage + index == state.participantIndex)
            },
        )
    }

    /** The whole description, one page of prose rows at a time. */
    private fun notesCard(): NexusCard {
        val event = events.getOrNull(state.selectedIndex) ?: return listCard()
        val pages = AgendaFormatter.notesPages(event.description)
        if (pages.isEmpty()) return detailCard()
        val pageIndex = state.notesPage.coerceIn(0, pages.lastIndex)
        return NexusCard(
            title = labels.notes,
            lines = emptyList(),
            subtitle = event.title.ifBlank { labels.title }.take(MAX_SUBTITLE),
            footer = if (pages.size <= 1) {
                labels.detailFooter
            } else {
                String.format(Locale.ROOT, labels.pageOf, pageIndex + 1, pages.size) +
                    " - " + labels.pageFooter
            },
            contentKey = contentKey("notes-$pageIndex", state.selectedIndex, pages.size),
            handlesBack = true,
            richLines = AgendaFormatter.notesRows(pages[pageIndex]).map {
                it.toCardLine(selected = false)
            },
        )
    }

    /**
     * Cached attendees, kicking off the provider query the first time. Returning
     * empty while it runs is deliberate: the detail appears immediately and grows
     * the participants row when the answer arrives.
     */
    private fun attendeesFor(event: AgendaEvent): List<AgendaAttendee> {
        attendeeCache[event.eventId]?.let { return it }
        if (event.eventId <= 0L || !attendeesLoading.add(event.eventId)) return emptyList()
        val eventId = event.eventId
        io.execute {
            val loaded = repository.attendees(eventId)
            main.post {
                attendeesLoading.remove(eventId)
                attendeeCache[eventId] = loaded
                if (surface != null) render()
            }
        }
        return emptyList()
    }

    private fun messageCard(title: String, hint: String?, key: String): NexusCard = NexusCard(
        title = labels.title,
        lines = listOfNotNull(title, hint),
        footer = labels.listFooter,
        contentKey = key,
        handlesBack = true,
    )

    /** contentKey is capped at 128 chars — key on identity, never on content. */
    private fun contentKey(view: String, index: Int, total: Int): String =
        "agenda-$view-$index-$total-${events.firstOrNull()?.id.orEmpty().hashCode()}".take(128)

    private fun AgendaRow.toneAsSdk(): NexusRowTone = when (tone) {
        AgendaTone.ALERT -> NexusRowTone.ALERT
        AgendaTone.NORMAL -> NexusRowTone.NORMAL
        AgendaTone.DIM -> NexusRowTone.DIM
        AgendaTone.BODY -> NexusRowTone.BODY
    }

    private fun AgendaRow.toCardLine(selected: Boolean) = NexusCardLine(
        text = text,
        badge = badge,
        trail = trail,
        sub = sub,
        tone = toneAsSdk(),
        selected = selected,
    )

    private companion object {
        const val SURFACE_ID = "main"
        const val REFRESH_MS = 60_000L
        const val MAX_TITLE = 120
        const val MAX_SUBTITLE = 120
    }
}
