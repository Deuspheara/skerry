package com.twitchplayer.tv.core.network.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    val scope: List<String> = emptyList(),
)

class DeviceCodeAuth(private val client: HttpClient, private val clientId: String) {
    suspend fun begin(scopes: Set<String>): DeviceCodeResponse {
        require(clientId.isNotBlank()) { "Set -PtwitchClientId=<registered public Twitch client ID> to enable sign-in." }
        val response = client.submitForm(
            "https://id.twitch.tv/oauth2/device",
            Parameters.build {
                append("client_id", clientId)
                append("scopes", scopes.joinToString(" "))
            },
        )
        check(response.status.isSuccess()) { "Twitch device authorization failed (${response.status.value})." }
        return response.body()
    }

    suspend fun awaitAuthorization(device: DeviceCodeResponse, scopes: Set<String>): TwitchTokens {
        val deadline = System.currentTimeMillis() + device.expiresIn * 1000L
        var intervalSeconds = device.interval.coerceAtLeast(1)
        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)
            val response = client.submitForm(
                "https://id.twitch.tv/oauth2/token",
                Parameters.build {
                    append("client_id", clientId)
                    append("scopes", scopes.joinToString(" "))
                    append("device_code", device.deviceCode)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                },
            )
            if (response.status.isSuccess()) {
                val token: OAuthTokenResponse = response.body()
                return TwitchTokens(token.accessToken, token.refreshToken, System.currentTimeMillis() / 1000 + token.expiresIn, token.scope.toSet())
            }
            val errorBody = response.bodyAsText()
            when {
                "authorization_pending" in errorBody -> Unit
                "slow_down" in errorBody -> intervalSeconds += 5
                "access_denied" in errorBody -> error("Twitch authorization was denied.")
                "expired_token" in errorBody || "invalid device code" in errorBody -> error("Twitch device code expired; start sign-in again.")
                else -> error("Twitch token request failed (${response.status.value}).")
            }
        }
        error("Twitch device code expired; start sign-in again.")
    }

    /** Public device-code clients rotate refresh tokens; persist the returned pair immediately. */
    suspend fun refresh(tokens: TwitchTokens): TwitchTokens {
        val response = client.submitForm(
            "https://id.twitch.tv/oauth2/token",
            Parameters.build {
                append("client_id", clientId)
                append("grant_type", "refresh_token")
                append("refresh_token", tokens.refreshToken)
            },
        )
        check(response.status.isSuccess()) { "Twitch session expired. Please connect your account again." }
        val refreshed: OAuthTokenResponse = response.body()
        return TwitchTokens(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + refreshed.expiresIn,
            scopes = refreshed.scope.toSet().ifEmpty { tokens.scopes },
        )
    }
}
