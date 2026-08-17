package com.havoc.rokidbus.plugin.otps

import kotlin.math.ceil

internal data class OtpCountdownFrame(
    val progressPercent: Int,
    val remainingSeconds: Int,
    val finished: Boolean,
)

internal object OtpCountdown {
    fun frame(durationMs: Long, elapsedMs: Long): OtpCountdownFrame {
        require(durationMs > 0L)
        val elapsed = elapsedMs.coerceIn(0L, durationMs)
        val remainingMs = durationMs - elapsed
        return OtpCountdownFrame(
            progressPercent = ((elapsed * 100L) / durationMs).toInt(),
            remainingSeconds = ceil(remainingMs / 1_000.0).toInt(),
            finished = elapsedMs >= durationMs,
        )
    }
}
