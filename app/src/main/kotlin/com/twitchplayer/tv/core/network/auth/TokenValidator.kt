package com.twitchplayer.tv.core.network.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class ValidatedToken(
    @SerialName("client_id") val clientId: String,
    val login: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val scopes: List<String> = emptyList(),
    @SerialName("expires_in") val expiresIn: Int,
)

/** Twitch requires third-party OAuth sessions to validate at startup and hourly while active. */
class TokenValidator(private val http: HttpClient, private val expectedClientId: String) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun validate(accessToken: String): ValidatedToken? {
        val response = http.get("https://id.twitch.tv/oauth2/validate") {
            header(HttpHeaders.Authorization, "OAuth $accessToken")
        }
        if (response.status.value == 401) return null
        check(response.status.value in 200..299) { "Twitch token validation failed (${response.status.value})." }
        return json.decodeFromString<ValidatedToken>(response.bodyAsText()).also {
            check(it.clientId == expectedClientId) { "This Twitch token belongs to another application." }
        }
    }
}
