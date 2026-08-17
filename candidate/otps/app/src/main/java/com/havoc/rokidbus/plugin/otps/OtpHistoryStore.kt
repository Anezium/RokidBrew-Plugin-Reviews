package com.havoc.rokidbus.plugin.otps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class OtpHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val history = OtpHistory(HISTORY_LIMIT).apply { replaceWith(read()) }

    @Synchronized
    fun add(record: OtpRecord): List<OtpRecord> = history.add(record).also(::write)

    @Synchronized
    fun snapshot(): List<OtpRecord> = history.snapshot()

    @Synchronized
    fun clear() {
        history.clear()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun read(): List<OtpRecord> = runCatching {
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        buildList {
            for (index in 0 until minOf(array.length(), HISTORY_LIMIT)) {
                val item = array.optJSONObject(index) ?: continue
                val app = item.optString("app").trim()
                val code = item.optString("code").trim()
                if (app.isEmpty() || code.isEmpty()) continue
                add(OtpRecord(app, code, item.optLong("receivedAtMs")))
            }
        }
    }.getOrDefault(emptyList())

    private fun write(records: List<OtpRecord>) {
        val array = JSONArray().apply {
            records.forEach { record ->
                put(
                    JSONObject()
                        .put("app", record.app)
                        .put("code", record.code)
                        .put("receivedAtMs", record.receivedAtMs),
                )
            }
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "otp_history"
        const val KEY_HISTORY = "records"
        const val HISTORY_LIMIT = 10
    }
}
