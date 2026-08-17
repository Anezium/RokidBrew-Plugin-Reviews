package com.havoc.rokidbus.plugin.otps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpModelsTest {
    @Test
    fun historyKeepsNewestTenAndMovesDuplicateToFront() {
        val history = OtpHistory()
        (1..11).forEach { index -> history.add(OtpRecord("App $index", "$index$index$index$index", index.toLong())) }

        assertEquals(10, history.snapshot().size)
        assertEquals("App 11", history.snapshot().first().app)
        assertEquals("App 2", history.snapshot().last().app)

        history.add(OtpRecord("App 5", "5555", 20L))
        assertEquals("App 5", history.snapshot().first().app)
        assertEquals(10, history.snapshot().size)
    }

    @Test
    fun deduplicatorSuppressesRepostsButExpiresThem() {
        val deduplicator = OtpDeliveryDeduplicator(retentionMs = 1_000L)

        assertTrue(deduplicator.shouldDeliver("notification", "123456", 0L))
        assertFalse(deduplicator.shouldDeliver("notification", "123456", 500L))
        assertTrue(deduplicator.shouldDeliver("notification", "654321", 600L))
        assertTrue(deduplicator.shouldDeliver("notification", "123456", 1_501L))
    }
}
