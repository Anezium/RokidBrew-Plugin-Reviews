package dev.havoc.rokidhome.phone.backup

import dev.havoc.rokidhome.phone.data.ConfigurationBackup
import dev.havoc.rokidhome.phone.security.Credentials
import kotlinx.serialization.Serializable

@Serializable
internal data class HomeAssistantBackup(
    val schemaVersion: Int = 1,
    val appId: String = APP_ID,
    val exportedAtEpochMs: Long,
    val credentials: CredentialsBackup,
    val configuration: ConfigurationBackup,
) {
    init {
        require(schemaVersion == 1) { "Unsupported Home Assistant backup version" }
        require(appId == APP_ID) { "Backup belongs to another app" }
        require(credentials.homeAssistantUrl.length <= 2_048) { "Home Assistant URL is too long" }
        require(credentials.homeAssistantToken.length <= 32_768) { "Home Assistant token is too long" }
        require(credentials.rokidToken.length <= 32_768) { "Rokid token is too long" }
    }

    companion object {
        const val APP_ID = "dev.havoc.rokidhome.phone"
    }
}

@Serializable
internal data class CredentialsBackup(
    val homeAssistantUrl: String,
    val homeAssistantToken: String,
    val rokidToken: String,
) {
    fun toCredentials() = Credentials(homeAssistantUrl, homeAssistantToken, rokidToken)

    companion object {
        fun from(value: Credentials) = CredentialsBackup(
            value.homeAssistantUrl,
            value.homeAssistantToken,
            value.rokidToken,
        )
    }
}
