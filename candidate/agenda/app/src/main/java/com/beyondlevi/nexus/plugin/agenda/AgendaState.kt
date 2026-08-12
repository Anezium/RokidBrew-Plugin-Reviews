package com.beyondlevi.nexus.plugin.agenda

/** The HUD views. All of them are driven by the same single navigation axis. */
enum class AgendaView { LIST, DETAIL, PARTICIPANTS, NOTES }

/** What a selectable row of the detail view opens. */
enum class AgendaDetailTarget { PARTICIPANTS, NOTES }

/**
 * The R08 one-axis state machine: NEXT / PREV / SELECT / BACK and nothing else.
 * Pure Kotlin so the navigability contract is proven by JVM unit tests, which is
 * the only proof available without the glasses.
 *
 * The axis means "move the cursor of whatever view is open":
 *  - LIST: walk the events;
 *  - DETAIL: walk the rows that open something (participants, notes). A detail
 *    with nothing to open has no cursor, so the axis keeps walking the events —
 *    the wearer is never left with a verb that does nothing;
 *  - PARTICIPANTS: walk the attendees (the hub windows the list to the cursor);
 *  - NOTES: turn the page.
 */
class AgendaState {

    /** What the service must do after an input verb. */
    enum class Effect { RENDER, CLOSE, NONE }

    var view: AgendaView = AgendaView.LIST
        private set

    var selectedIndex: Int = 0
        private set

    var itemCount: Int = 0
        private set

    /** The rows of the open detail that lead somewhere, in render order. */
    var detailTargets: List<AgendaDetailTarget> = emptyList()
        private set

    var detailIndex: Int = 0
        private set

    var participantIndex: Int = 0
        private set

    var participantCount: Int = 0
        private set

    var notesPage: Int = 0
        private set

    var notesPageCount: Int = 0
        private set

    /**
     * Whether the wearer has moved the detail cursor since this detail opened.
     * Content arrives asynchronously (the guest list comes from the provider
     * after the first render), and a row inserted above an untouched cursor must
     * take the focus — otherwise opening an event and tapping lands on whatever
     * row happened to exist first.
     */
    private var detailCursorMoved = false

    /** The detail row the cursor is on, or null when this detail opens nothing. */
    val focusedTarget: AgendaDetailTarget?
        get() = detailTargets.getOrNull(detailIndex)

    /**
     * Applies a freshly loaded agenda. [keepIndex] is where the previously
     * selected event ended up (-1 when it is gone), so a reload does not throw
     * the wearer back to the top of the list.
     */
    fun setItems(count: Int, keepIndex: Int = selectedIndex) {
        itemCount = count.coerceAtLeast(0)
        selectedIndex = if (itemCount == 0) 0 else keepIndex.coerceIn(0, itemCount - 1)
        if (itemCount == 0) resetToList()
    }

    /**
     * Declares what the open event offers. Called by the service whenever the
     * detail is rendered, including after attendees finish loading — the cursor
     * survives if the row it was on is still there.
     */
    fun setDetailContent(
        targets: List<AgendaDetailTarget>,
        participantCount: Int,
        notesPageCount: Int,
    ) {
        val focused = focusedTarget
        detailTargets = targets
        detailIndex = if (detailCursorMoved) {
            targets.indexOf(focused).takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        this.participantCount = participantCount.coerceAtLeast(0)
        this.notesPageCount = notesPageCount.coerceAtLeast(0)
        participantIndex = participantIndex.coerceIn(0, (this.participantCount - 1).coerceAtLeast(0))
        notesPage = notesPage.coerceIn(0, (this.notesPageCount - 1).coerceAtLeast(0))
        // A page that vanished under the wearer sends them back to where it lived.
        if (view == AgendaView.PARTICIPANTS && this.participantCount == 0) view = AgendaView.DETAIL
        if (view == AgendaView.NOTES && this.notesPageCount == 0) view = AgendaView.DETAIL
    }

    /** NEXT (+1) / PREV (-1). Wraps, so the axis never dead-ends. */
    fun move(delta: Int): Effect = when (view) {
        AgendaView.LIST -> moveEvent(delta)
        AgendaView.DETAIL -> if (detailTargets.isEmpty()) {
            moveEvent(delta)
        } else {
            detailIndex = Math.floorMod(detailIndex + delta, detailTargets.size)
            detailCursorMoved = true
            Effect.RENDER
        }
        AgendaView.PARTICIPANTS -> if (participantCount == 0) {
            Effect.NONE
        } else {
            participantIndex = Math.floorMod(participantIndex + delta, participantCount)
            Effect.RENDER
        }
        AgendaView.NOTES -> if (notesPageCount <= 1) {
            Effect.NONE
        } else {
            notesPage = Math.floorMod(notesPage + delta, notesPageCount)
            Effect.RENDER
        }
    }

    private fun moveEvent(delta: Int): Effect {
        if (itemCount == 0) return Effect.NONE
        selectedIndex = Math.floorMod(selectedIndex + delta, itemCount)
        // A different event carries different rows; the cursor starts over.
        detailIndex = 0
        detailCursorMoved = false
        participantIndex = 0
        notesPage = 0
        return Effect.RENDER
    }

    /** SELECT: opens the focused event, then the focused row of its detail. */
    fun select(): Effect {
        if (itemCount == 0) return Effect.NONE
        return when (view) {
            AgendaView.LIST -> {
                view = AgendaView.DETAIL
                detailIndex = 0
                detailCursorMoved = false
                Effect.RENDER
            }
            AgendaView.DETAIL -> when (focusedTarget) {
                AgendaDetailTarget.PARTICIPANTS -> {
                    if (participantCount == 0) return Effect.NONE
                    participantIndex = 0
                    view = AgendaView.PARTICIPANTS
                    Effect.RENDER
                }
                AgendaDetailTarget.NOTES -> {
                    if (notesPageCount == 0) return Effect.NONE
                    notesPage = 0
                    view = AgendaView.NOTES
                    Effect.RENDER
                }
                null -> Effect.NONE
            }
            AgendaView.PARTICIPANTS, AgendaView.NOTES -> Effect.NONE
        }
    }

    /** BACK: one step out. From the list, it self-closes the plugin. */
    fun back(): Effect = when (view) {
        AgendaView.PARTICIPANTS, AgendaView.NOTES -> {
            view = AgendaView.DETAIL
            Effect.RENDER
        }
        AgendaView.DETAIL -> {
            view = AgendaView.LIST
            Effect.RENDER
        }
        AgendaView.LIST -> Effect.CLOSE
    }

    /** A fresh PLUGIN_OPEN is re-entrant: reset before re-showing. */
    fun reset() {
        resetToList()
        selectedIndex = 0
        itemCount = 0
    }

    private fun resetToList() {
        view = AgendaView.LIST
        detailTargets = emptyList()
        detailIndex = 0
        detailCursorMoved = false
        participantIndex = 0
        participantCount = 0
        notesPage = 0
        notesPageCount = 0
    }
}
