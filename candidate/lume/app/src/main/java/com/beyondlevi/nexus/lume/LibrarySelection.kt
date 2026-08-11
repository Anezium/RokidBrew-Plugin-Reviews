package com.beyondlevi.nexus.lume

/**
 * One-axis selection over the library list (the R08 ring contract): NEXT/PREV
 * walk the list with wrap-around, SELECT acts on the focused row. Pure and
 * unit-testable — this is the ring-navigability proof for the library surface.
 */
class LibrarySelection(initialCount: Int = 0) {
    var count: Int = initialCount
        private set

    var selectedIndex: Int = 0
        private set

    /** Re-seeds the list, keeping the selection in range (e.g. after a delete on the phone). */
    fun setCount(newCount: Int) {
        count = newCount.coerceAtLeast(0)
        selectedIndex = if (count == 0) 0 else selectedIndex.coerceIn(0, count - 1)
    }

    fun move(delta: Int) {
        if (count <= 0) {
            selectedIndex = 0
            return
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, count)
    }

    /** The focused row index, or null when the library is empty. */
    fun selected(): Int? = if (count <= 0) null else selectedIndex
}
