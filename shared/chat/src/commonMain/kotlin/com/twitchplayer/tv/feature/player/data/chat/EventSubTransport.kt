package com.twitchplayer.tv.feature.player.data.chat

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One EventSub WebSocket as the session loop sees it. */
internal interface EventSubSocket {
    /** The next frame's text, or null for a frame that is not text (pings and binary frames are skipped by the loop). Throws when the socket is closed. */
    suspend fun receive(): String?

    suspend fun close()
}

/** What the session loop needs from the network: open a socket and create a subscription. */
internal interface EventSubTransport {
    suspend fun connect(url: String): EventSubSocket

    /** Throws when Twitch refuses the subscription (any non-2xx answer). */
    suspend fun subscribe(sessionId: String, type: String, version: String, condition: JsonObject)
}

/** The real transport: Ktor WebSockets for the socket, one Helix call per subscription. Kept thin on purpose. */
internal class KtorEventSubTransport(
    private val http: HttpClient,
    private val clientId: String,
    private val accessToken: () -> String,
) : EventSubTransport {
    override suspend fun connect(url: String): EventSubSocket = KtorEventSubSocket(http.webSocketSession(url))

    override suspend fun subscribe(sessionId: String, type: String, version: String, condition: JsonObject) {
        val response = http.post("https://api.twitch.tv/helix/eventsub/subscriptions") {
            header("Client-Id", clientId)
            header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            contentType(ContentType.Application.Json)
            setBody(SubscriptionRequest(type = type, version = version, condition = condition, transport = WebSocketTransport(sessionId = sessionId)))
        }
        if (!response.status.isSuccess()) error("EventSub subscription $type failed (${response.status.value})")
    }
}

private class KtorEventSubSocket(private val session: WebSocketSession) : EventSubSocket {
    override suspend fun receive(): String? = (session.incoming.receive() as? Frame.Text)?.readText()

    override suspend fun close() = session.close()
}

@Serializable private data class SubscriptionRequest(
    val type: String,
    val version: String = "1",
    val condition: JsonObject,
    val transport: WebSocketTransport,
)

@Serializable private data class WebSocketTransport(val method: String = "websocket", @SerialName("session_id") val sessionId: String)
