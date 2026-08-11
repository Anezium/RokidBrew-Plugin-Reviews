package com.beyondlevi.nexus.lume

/**
 * A document in the phone library, ready to be streamed to the glasses.
 * `source` is where it came from: "pdf" | "txt" | "share".
 * `progressWordIndex` is the last word the reader stopped at.
 *
 * Ported from the original Lume `DocumentInfo` (minus wire-only fields).
 */
data class DocumentInfo(
    val id: String = "",
    val title: String = "",
    val totalWords: Int = 0,
    val progressWordIndex: Int = 0,
    val addedAtMs: Long = 0L,
    val source: String = "txt",
)
