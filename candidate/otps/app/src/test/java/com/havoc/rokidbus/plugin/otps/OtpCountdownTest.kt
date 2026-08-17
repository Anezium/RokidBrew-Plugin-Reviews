package com.havoc.rokidbus.plugin.otps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpCountdownTest {
    @Test
    fun startsEmptyWithTheWholeDurationRemaining() {
        val frame = OtpCountdown.frame(durationMs = 12_000L, elapsedMs = 0L)

        assertEquals(0, frame.progressPercent)
        assertEquals(12, frame.remainingSeconds)
        assertFalse(frame.finished)
    }

    @Test
    fun reportsElapsedProgressAndRoundsRemainingTimeUp() {
        val frame = OtpCountdown.frame(durationMs = 12_000L, elapsedMs = 6_501L)

        assertEquals(54, frame.progressPercent)
        assertEquals(6, frame.remainingSeconds)
        assertFalse(frame.finished)
    }

    @Test
    fun clampsBeforeStartAndAfterDeadline() {
        val before = OtpCountdown.frame(durationMs = 2_000L, elapsedMs = -100L)
        val after = OtpCountdown.frame(durationMs = 2_000L, elapsedMs = 2_500L)

        assertEquals(0, before.progressPercent)
        assertEquals(2, before.remainingSeconds)
        assertEquals(100, after.progressPercent)
        assertEquals(0, after.remainingSeconds)
        assertTrue(after.finished)
    }
}
