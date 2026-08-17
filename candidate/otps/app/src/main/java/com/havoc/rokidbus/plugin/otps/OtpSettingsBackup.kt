package com.havoc.rokidbus.plugin.otps

import org.json.JSONObject

internal data class OtpSettingsBackup(
    val enabled: Boolean,
    val autoClose: Boolean,
    val durationSeconds: Int,
) {
    fun encode(): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("appId", APP_ID)
        .put("exportedAtEpochMs", System.currentTimeMillis())
        .put("enabled", enabled)
        .put("autoClose", autoClose)
        .put("durationSeconds", durationSeconds)
        .toString()

    companion object {
        const val APP_ID = "com.havoc.rokidbus.plugin.otps"
        private const val SCHEMA_VERSION = 1

        fun decode(value: String): OtpSettingsBackup {
            val json = JSONObject(value)
            require(json.getInt("schemaVersion") == SCHEMA_VERSION) {
                "Unsupported OTPs backup version"
            }
            require(json.getString("appId") == APP_ID) { "Backup belongs to another app" }
            val duration = json.getInt("durationSeconds")
            require(duration in OtpSettings.MIN_DURATION_SECONDS..OtpSettings.MAX_DURATION_SECONDS) {
                "Invalid OTP duration"
            }
            return OtpSettingsBackup(
                enabled = json.getBoolean("enabled"),
                autoClose = json.getBoolean("autoClose"),
                durationSeconds = duration,
            )
        }
    }
}
