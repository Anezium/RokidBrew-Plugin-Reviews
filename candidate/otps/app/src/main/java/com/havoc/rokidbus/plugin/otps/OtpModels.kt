package com.havoc.rokidbus.plugin.otps

data class OtpDetection(
    val code: String,
    val confidence: Int,
)

data class OtpRecord(
    val app: String,
    val code: String,
    val receivedAtMs: Long,
)

internal class OtpHistory(private val limit: Int = 10) {
    private val records = mutableListOf<OtpRecord>()

    fun replaceWith(items: List<OtpRecord>) {
        records.clear()
        records += items.take(limit)
    }

    fun add(record: OtpRecord): List<OtpRecord> {
        records.removeAll { it.app == record.app && it.code == record.code }
        records.add(0, record)
        if (records.size > limit) records.subList(limit, records.size).clear()
        return snapshot()
    }

    fun clear() = records.clear()

    fun snapshot(): List<OtpRecord> = records.toList()
}

internal class OtpDeliveryDeduplicator(
    private val retentionMs: Long = 10 * 60 * 1000L,
) {
    private val lastSeen = linkedMapOf<String, Long>()

    fun shouldDeliver(notificationKey: String, code: String, nowMs: Long): Boolean {
        val identity = "$notificationKey\u0000$code"
        val previous = lastSeen[identity]
        lastSeen.entries.removeAll { nowMs - it.value > retentionMs }
        lastSeen[identity] = nowMs
        return previous == null || nowMs - previous > retentionMs
    }
}
