package com.beyondlevi.nexus.plugin.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The R08 navigability proof: the whole plugin is driven by four verbs on one
 * axis — NEXT (+1), PREV (-1), SELECT, BACK — and every view is reachable and
 * leavable with them alone.
 */
class AgendaStateTest {

    private fun stateWith(count: Int) = AgendaState().apply { setItems(count) }

    /** An open detail offering both a guest list and a paged description. */
    private fun stateInDetail(
        events: Int = 3,
        participants: Int = 4,
        notesPages: Int = 3,
    ) = stateWith(events).apply {
        select()
        setDetailContent(
            targets = listOf(AgendaDetailTarget.PARTICIPANTS, AgendaDetailTarget.NOTES),
            participantCount = participants,
            notesPageCount = notesPages,
        )
    }

    @Test
    fun `next and prev wrap around the list`() {
        val state = stateWith(3)
        assertEquals(0, state.selectedIndex)
        state.move(1)
        state.move(1)
        assertEquals(2, state.selectedIndex)
        state.move(1)
        assertEquals(0, state.selectedIndex)
        state.move(-1)
        assertEquals(2, state.selectedIndex)
    }

    @Test
    fun `select opens the detail of the focused event and back returns`() {
        val state = stateWith(3)
        state.move(1)
        assertEquals(AgendaState.Effect.RENDER, state.select())
        assertEquals(AgendaView.DETAIL, state.view)
        assertEquals(1, state.selectedIndex)

        assertEquals(AgendaState.Effect.RENDER, state.back())
        assertEquals(AgendaView.LIST, state.view)
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun `back on the list closes the plugin`() {
        val state = stateWith(2)
        assertEquals(AgendaState.Effect.CLOSE, state.back())
    }

    @Test
    fun `the axis still walks events while a detail with nothing to open is up`() {
        val state = stateWith(3)
        state.select()
        state.setDetailContent(emptyList(), participantCount = 0, notesPageCount = 0)
        state.move(1)
        assertEquals(AgendaView.DETAIL, state.view)
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun `the axis walks the openable rows of a detail that has them`() {
        val state = stateInDetail()
        assertEquals(AgendaDetailTarget.PARTICIPANTS, state.focusedTarget)
        state.move(1)
        assertEquals(AgendaDetailTarget.NOTES, state.focusedTarget)
        // The cursor stays on the rows, so the event underneath does not change.
        assertEquals(0, state.selectedIndex)
        state.move(1)
        assertEquals(AgendaDetailTarget.PARTICIPANTS, state.focusedTarget)
    }

    @Test
    fun `tapping the participants row opens the guest list, back returns`() {
        val state = stateInDetail()
        assertEquals(AgendaState.Effect.RENDER, state.select())
        assertEquals(AgendaView.PARTICIPANTS, state.view)
        assertEquals(0, state.participantIndex)

        state.move(1)
        assertEquals(1, state.participantIndex)
        state.move(-1)
        state.move(-1)
        assertEquals(3, state.participantIndex)

        assertEquals(AgendaState.Effect.RENDER, state.back())
        assertEquals(AgendaView.DETAIL, state.view)
        // And the detail cursor is still on the row that opened it.
        assertEquals(AgendaDetailTarget.PARTICIPANTS, state.focusedTarget)
    }

    @Test
    fun `tapping the notes row opens the description and the axis turns pages`() {
        val state = stateInDetail()
        state.move(1)
        state.select()
        assertEquals(AgendaView.NOTES, state.view)
        assertEquals(0, state.notesPage)
        state.move(1)
        assertEquals(1, state.notesPage)
        state.move(-1)
        state.move(-1)
        assertEquals(2, state.notesPage)
        state.back()
        assertEquals(AgendaView.DETAIL, state.view)
        assertEquals(AgendaDetailTarget.NOTES, state.focusedTarget)
    }

    @Test
    fun `back walks out one step at a time, never straight to the list`() {
        val state = stateInDetail()
        state.select()
        assertEquals(AgendaView.PARTICIPANTS, state.view)
        state.back()
        assertEquals(AgendaView.DETAIL, state.view)
        state.back()
        assertEquals(AgendaView.LIST, state.view)
        assertEquals(AgendaState.Effect.CLOSE, state.back())
    }

    @Test
    fun `a single-page description does not pretend to turn`() {
        val state = stateInDetail(notesPages = 1)
        state.move(1)
        state.select()
        assertEquals(AgendaView.NOTES, state.view)
        assertEquals(AgendaState.Effect.NONE, state.move(1))
        assertEquals(0, state.notesPage)
    }

    @Test
    fun `a guest list arriving late takes the focus of an untouched cursor`() {
        val state = stateWith(3)
        state.select()
        // First render: the guest list has not come back from the provider yet,
        // so notes is the only row and the cursor sits on it by default.
        state.setDetailContent(listOf(AgendaDetailTarget.NOTES), 0, 2)
        assertEquals(AgendaDetailTarget.NOTES, state.focusedTarget)
        // It arrives and inserts a row above. The wearer never chose anything, so
        // a tap must land on the first row, not on whatever existed first.
        state.setDetailContent(
            listOf(AgendaDetailTarget.PARTICIPANTS, AgendaDetailTarget.NOTES),
            participantCount = 5,
            notesPageCount = 2,
        )
        assertEquals(AgendaDetailTarget.PARTICIPANTS, state.focusedTarget)
    }

    @Test
    fun `late content never moves a cursor the wearer placed`() {
        val state = stateInDetail()
        state.move(1)
        assertEquals(AgendaDetailTarget.NOTES, state.focusedTarget)
        // A refresh re-declares the same rows; the deliberate choice survives.
        state.setDetailContent(
            listOf(AgendaDetailTarget.PARTICIPANTS, AgendaDetailTarget.NOTES),
            participantCount = 9,
            notesPageCount = 3,
        )
        assertEquals(AgendaDetailTarget.NOTES, state.focusedTarget)
    }

    @Test
    fun `an open page that loses its content drops back to the detail`() {
        val state = stateInDetail()
        state.select()
        assertEquals(AgendaView.PARTICIPANTS, state.view)
        state.setDetailContent(listOf(AgendaDetailTarget.NOTES), participantCount = 0, notesPageCount = 3)
        assertEquals(AgendaView.DETAIL, state.view)
    }

    @Test
    fun `walking to another event starts its detail from the top`() {
        val state = stateInDetail()
        state.move(1)
        assertEquals(AgendaDetailTarget.NOTES, state.focusedTarget)
        state.back()
        state.move(1)
        state.select()
        assertEquals(1, state.selectedIndex)
        assertEquals(0, state.detailIndex)
        assertEquals(0, state.notesPage)
    }

    @Test
    fun `select is inert on the detail view`() {
        val state = stateWith(2)
        state.select()
        assertEquals(AgendaState.Effect.NONE, state.select())
        assertEquals(AgendaView.DETAIL, state.view)
    }

    @Test
    fun `every row is reachable with next alone`() {
        val state = stateWith(9)
        val visited = mutableSetOf(state.selectedIndex)
        repeat(8) {
            state.move(1)
            visited += state.selectedIndex
        }
        assertEquals((0..8).toSet(), visited)
    }

    @Test
    fun `an empty agenda swallows the verbs instead of moving a phantom cursor`() {
        val state = stateWith(0)
        assertEquals(AgendaState.Effect.NONE, state.move(1))
        assertEquals(AgendaState.Effect.NONE, state.select())
        assertEquals(AgendaView.LIST, state.view)
        assertEquals(AgendaState.Effect.CLOSE, state.back())
    }

    @Test
    fun `a reload keeps the wearer on the same event`() {
        val state = stateWith(5)
        state.move(1)
        state.move(1)
        assertEquals(2, state.selectedIndex)
        // The focused event moved to index 1 after the one before it ended.
        state.setItems(count = 4, keepIndex = 1)
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun `a shrinking agenda clamps the cursor instead of pointing past the end`() {
        val state = stateWith(5)
        repeat(4) { state.move(1) }
        assertEquals(4, state.selectedIndex)
        state.setItems(count = 2)
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun `an emptied agenda drops back to the list view`() {
        val state = stateWith(3)
        state.select()
        state.setItems(0)
        assertEquals(AgendaView.LIST, state.view)
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun `reset returns to the opening state`() {
        val state = stateWith(4)
        state.move(2)
        state.select()
        state.reset()
        assertEquals(AgendaView.LIST, state.view)
        assertEquals(0, state.selectedIndex)
        assertEquals(0, state.itemCount)
    }
}
