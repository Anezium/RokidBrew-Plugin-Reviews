package com.beyondlevi.nexus.lume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the original standalone Lume RsvpTest — pacing parity. */
class RsvpEngineTest {

    @Test
    fun focalLetterDriftsRightAsWordsGrow() {
        assertEquals(0, RsvpEngine.focusIndex("a"))
        assertEquals(1, RsvpEngine.focusIndex("sim"))
        assertEquals(1, RsvpEngine.focusIndex("livro"))
        assertEquals(2, RsvpEngine.focusIndex("porque"))
        assertEquals(2, RsvpEngine.focusIndex("conversas"))
        assertEquals(3, RsvpEngine.focusIndex("independente"))
        assertEquals(4, RsvpEngine.focusIndex("responsabilidades"))
    }

    @Test
    fun baseDelayFollowsWpm() {
        assertEquals(200L, RsvpEngine.delayMsFor("casa", 300))
        assertEquals(100L, RsvpEngine.delayMsFor("casa", 600))
    }

    @Test
    fun punctuationAndParagraphsHoldLonger() {
        val plain = RsvpEngine.delayMsFor("fim", 300)
        val sentence = RsvpEngine.delayMsFor("fim.", 300)
        val comma = RsvpEngine.delayMsFor("fim,", 300)
        val paragraph = RsvpEngine.delayMsFor("fim." + TextMarks.PARAGRAPH, 300)
        assertTrue(sentence > comma)
        assertTrue(comma > plain)
        assertTrue(paragraph > sentence)
    }

    @Test
    fun longWordsHoldLonger() {
        assertTrue(RsvpEngine.delayMsFor("extraordinariamente", 300) > RsvpEngine.delayMsFor("hoje", 300))
    }
}
