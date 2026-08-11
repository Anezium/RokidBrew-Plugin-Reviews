package com.beyondlevi.nexus.lume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySelectionTest {

    @Test
    fun emptyLibraryHasNoSelectionAndIgnoresMoves() {
        val s = LibrarySelection(0)
        assertNull(s.selected())
        s.move(1)
        s.move(-1)
        assertEquals(0, s.selectedIndex)
        assertNull(s.selected())
    }

    @Test
    fun nextAndPrevWrapAround() {
        val s = LibrarySelection(3)
        s.move(-1)
        assertEquals(2, s.selectedIndex)  // PREV from top wraps to last
        s.move(1)
        assertEquals(0, s.selectedIndex)  // NEXT from last wraps to first
        s.move(1)
        assertEquals(1, s.selectedIndex)
    }

    @Test
    fun reseedClampsSelectionIntoRange() {
        val s = LibrarySelection(3)
        s.move(1); s.move(1)
        assertEquals(2, s.selectedIndex)
        s.setCount(2)                     // e.g. a document was removed on the phone
        assertEquals(1, s.selectedIndex)
        s.setCount(0)
        assertNull(s.selected())
    }
}
