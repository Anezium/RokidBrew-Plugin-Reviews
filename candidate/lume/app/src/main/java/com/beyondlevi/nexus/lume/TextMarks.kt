package com.beyondlevi.nexus.lume

/**
 * In-band markers appended to tokenized words. The library tags them while
 * tokenizing; the reader strips them before display and uses them for pacing.
 *
 * Ported verbatim from the original standalone Lume (`shared-contracts`).
 */
object TextMarks {
    /** Appended to the last word of a paragraph (U+2029 PARAGRAPH SEPARATOR). */
    const val PARAGRAPH = " "

    fun strip(word: String): String = word.removeSuffix(PARAGRAPH)

    fun endsParagraph(word: String): Boolean = word.endsWith(PARAGRAPH)
}
