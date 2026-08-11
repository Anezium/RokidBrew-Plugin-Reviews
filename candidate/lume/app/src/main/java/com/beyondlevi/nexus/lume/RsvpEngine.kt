package com.beyondlevi.nexus.lume

import kotlin.math.max

/**
 * RSVP pacing: how long each word stays on screen at a given speed.
 *
 * The base delay comes from the words-per-minute dial; longer words, words
 * that close a clause/sentence and paragraph breaks hold longer, which keeps
 * fast reading comfortable.
 *
 * Ported verbatim from the original standalone Lume glasses app. In the Nexus
 * port these per-word delays are accumulated into a timeline and handed to the
 * hub as a time-synced surface, so the glasses play the RSVP stream on their
 * own clock instead of the phone streaming word-by-word over Bluetooth.
 */
object RsvpEngine {
    const val MIN_WPM = 150
    const val MAX_WPM = 700
    const val DEFAULT_WPM = 350
    const val WPM_STEP = 25

    fun delayMsFor(rawWord: String, wpm: Int): Long {
        val word = TextMarks.strip(rawWord)
        val clamped = wpm.coerceIn(MIN_WPM, MAX_WPM)
        val base = 60_000.0 / clamped
        var factor = 1.0
        if (word.length >= 8) factor += 0.3
        if (word.length >= 12) factor += 0.3
        val last = word.lastOrNull { !it.isWhitespace() }
        when (last) {
            '.', '!', '?', '…' -> factor += 1.4
            ',', ';', ':', ')', '"', '”', '»' -> factor += 0.6
        }
        if (TextMarks.endsParagraph(rawWord)) factor += 1.0
        return max(40.0, base * factor).toLong()
    }

    /**
     * Optimal recognition point: the letter the eye should lock onto. Retained
     * from the original for parity and possible future intra-word emphasis;
     * the Nexus HUD renders whole rows, so the letter is not highlighted today.
     */
    fun focusIndex(word: String): Int {
        val letters = word.count { it.isLetterOrDigit() }
        val length = if (letters > 0) letters else word.length
        return when {
            length <= 1 -> 0
            length <= 5 -> 1
            length <= 9 -> 2
            length <= 13 -> 3
            else -> 4
        }.coerceAtMost(word.length - 1)
    }
}
