package com.beyondlevi.nexus.lume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModelTest {

    private val words = listOf("The", "cat.", "A", "dog", "runs.", "End")

    @Test
    fun timelineIsStrictlyIncreasingAndStartsAtZero() {
        val m = ReaderModel(words, 300)
        assertEquals(0L, m.posMsForWord(0))
        for (i in 0 until m.size - 1) {
            assertTrue("word $i should start before word ${i + 1}", m.posMsForWord(i) < m.posMsForWord(i + 1))
        }
    }

    @Test
    fun wordIndexRoundTripsThroughItsStartTime() {
        val m = ReaderModel(words, 350)
        for (k in 0 until m.size) {
            assertEquals(k, m.wordIndexAt(m.posMsForWord(k)))
        }
    }

    @Test
    fun clockPositionClampsAtBothEnds() {
        val m = ReaderModel(words, 400)
        assertEquals(0, m.wordIndexAt(-100L))
        assertEquals(m.size - 1, m.wordIndexAt(Long.MAX_VALUE / 2))
    }

    @Test
    fun changingSpeedKeepsTheReaderOnTheSameWord() {
        val m = ReaderModel(words, 300)
        val word = 3
        val posBefore = m.posMsForWord(word)
        assertEquals(word, m.wordIndexAt(posBefore))
        m.setWpm(600)
        // The same word now sits at a new time, but still maps back to itself.
        assertEquals(word, m.wordIndexAt(m.posMsForWord(word)))
        assertTrue("faster wpm should shorten the timeline", m.posMsForWord(m.size - 1) < posBefore * 2)
    }

    @Test
    fun sentenceSteppingLandsOnBoundaries() {
        // Sentence starts: 0, 2 (after "cat."), 5 (after "runs.").
        val m = ReaderModel(words, 300)
        assertEquals(5, m.sentenceStep(3, +1))   // skip forward to next sentence
        assertEquals(2, m.sentenceStep(3, -1))   // rewind to start of current sentence
        assertEquals(0, m.sentenceStep(2, -1))   // already at boundary -> previous sentence
        assertEquals(2, m.sentenceStep(0, +1))   // forward from first sentence
    }

    @Test
    fun windowRangeStaysWithinBoundsAndCarriesBackContext() {
        val big = List(1000) { "w$it" }
        val m = ReaderModel(big, 350)
        val near = m.windowRange(5, windowSize = 60)
        assertEquals(0, near.first)                 // clamps at the start
        assertEquals(60, near.count())              // full window width (60 words)
        val mid = m.windowRange(500, windowSize = 60)
        assertEquals(488, mid.first)                // 500 - BACK_CONTEXT(12)
        assertEquals(60, mid.count())               // look-ahead ahead of the read head
        assertTrue(mid.last < big.size)
    }

    @Test
    fun defaultWindowIsSmallEnoughToStreamOverCxr() {
        val big = List(1000) { "word$it" }
        val m = ReaderModel(big, 350)
        assertEquals(ReaderModel.DEFAULT_WINDOW, m.windowRange(500).count())
        assertTrue("window must stay small for a CXR-only link", m.windowRange(500).count() <= 80)
    }
}
