package com.twitchplayer.tv.feature.player.data.chat

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.decodeFromString
import java.util.LinkedHashSet

sealed interface ChatConnectionState {
    data object Disconnected : ChatConnectionState
    data object Connecting : ChatConnectionState
    data object Connected : ChatConnectionState
    data class Error(val message: String) : ChatConnectionState
}

/** A single active chat connection. Own its coroutine in the selected-channel lifecycle. */
class EventSubChatClient(
    private val http: HttpClient,
    private val clientId: String,
    private val accessToken: () -> String,
    private val maxMessages: Int = 150,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private val ids = LinkedHashSet<String>()
    private val idMutex = Mutex()
    private val mutableEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = maxMessages.coerceAtLeast(1))
    val events: SharedFlow<ChatEvent> = mutableEvents
    private val mutableState = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
    val connectionState: StateFlow<ChatConnectionState> = mutableState.asStateFlow()
    @Volatile private var stopped = false

    suspend fun run(broadcasterId: String, userId: String) {
        require(broadcasterId.isNotBlank() && userId.isNotBlank())
        stopped = false
        var retry = 1L
        while (!stopped) {
            mutableState.value = ChatConnectionState.Connecting
            try {
                val socket = http.webSocketSession("wss://eventsub.wss.twitch.tv/ws")
                try {
                    handleSession(socket, broadcasterId, userId)
                    retry = 1L
                } finally { socket.close() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { mutableState.value = ChatConnectionState.Error(error.message ?: "Chat connection failed") }
            if (!stopped) delay(retry * 1000L)
            retry = (retry * 2).coerceAtMost(30)
        }
        mutableState.value = ChatConnectionState.Disconnected
    }

    fun stop() { stopped = true }

    private suspend fun handleSession(initial: WebSocketSession, broadcasterId: String, userId: String) {
        var socket = initial
        var sessionId: String? = null
        mutableState.value = ChatConnectionState.Connected
        while (!stopped) {
            val frame = socket.incoming.receive()
            if (frame !is Frame.Text) continue
            val envelope = json.decodeFromString<EventSubEnvelope>(frame.readText())
            when (envelope.metadata.messageType) {
                "session_welcome" -> {
                    val id = envelope.payload.session?.id ?: error("EventSub welcome omitted session ID")
                    sessionId = id
                    createSubscription(id, "channel.chat.message", "1", chatCondition(broadcasterId, userId))
                    // Everything else is a bonus: chat works without it (older tokens without a scope, the 10-point cost cap).
                    // Twitch wants every subscription within 10 s of the welcome: they go out together.
                    coroutineScope {
                        OPTIONAL.map { (type, version) ->
                            async {
                                runCatching { createSubscription(id, type, version, conditionFor(type, broadcasterId, userId)) }
                                    // Warn level survives R8: the status says whether a scope or the cost cap is the cause.
                                    .onFailure { if (it is CancellationException) throw it; android.util.Log.w("EventSub", "bonus subscription refused: ${it.message}") }
                            }
                        }.awaitAll()
                    }
                    mutableState.value = ChatConnectionState.Connected
                }
                "session_keepalive" -> Unit
                "notification" -> envelope.payload.event?.let { event ->
                    // One undecodable message must not take the whole connection down.
                    val chatEvent = runCatching { decodeEvent(envelope.metadata, event) }.getOrNull()
                    val fresh = idMutex.withLock {
                        if (!ids.add(envelope.metadata.messageId)) false
                        else { while (ids.size > maxMessages.coerceAtLeast(1)) ids.remove(ids.first()); true }
                    }
                    if (fresh && chatEvent != null) mutableEvents.emit(chatEvent)
                }
                "session_reconnect" -> {
                    val url = envelope.payload.session?.reconnectUrl ?: error("Reconnect frame omitted reconnect URL")
                    val migrated = http.webSocketSession(url)
                    // The existing session remains live until the replacement welcomes.
                    val welcome = migrated.incoming.receive()
                    if (welcome !is Frame.Text || json.decodeFromString<EventSubEnvelope>(welcome.readText()).metadata.messageType != "session_welcome") {
                        migrated.close(); error("Replacement EventSub session did not welcome")
                    }
                    socket.close()
                    socket = migrated
                    mutableState.value = ChatConnectionState.Connected
                }
                // Losing the messages ends the session (it reconnects); a bonus subscription (modes, raids...) just goes quiet.
                "revocation" -> if (envelope.metadata.subscriptionType == "channel.chat.message") error("Twitch revoked the chat subscription")
            }
        }
    }

    private fun decodeEvent(metadata: EventSubMetadata, event: kotlinx.serialization.json.JsonElement): ChatEvent? = when (metadata.subscriptionType) {
        "channel.chat.message" -> ChatEvent.Message(json.decodeFromJsonElement<ChatMessage>(event).copy(timestamp = metadata.messageTimestamp))
        "channel.chat.notification" -> json.decodeFromJsonElement<ChatNotification>(event).let { n ->
            val text = listOfNotNull(n.systemMessage.takeIf(String::isNotBlank), n.content?.text?.takeIf(String::isNotBlank)).joinToString(" · ")
            if (text.isEmpty()) null else ChatEvent.Notice(metadata.messageId, text)
        }
        else -> decodeSignal(metadata.subscriptionType, event.jsonObject)
    }

    private suspend fun createSubscription(sessionId: String, type: String, version: String, condition: JsonObject) {
        val response = http.post("https://api.twitch.tv/helix/eventsub/subscriptions") {
            header("Client-Id", clientId); header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
            contentType(ContentType.Application.Json)
            setBody(SubscriptionRequest(type = type, version = version, condition = condition, transport = WebSocketTransport(sessionId = sessionId)))
        }
        if (response.status.value !in 200..299) error("EventSub subscription $type failed (${response.status.value})")
    }

    companion object {
        /**
         * Subscriptions after the message one: (type, version). Moderation and room modes need `user:read:chat` like the
         * messages; stream.offline / channel.update / channel.raid need no scope but cost 1 each of the connection's 10 points.
         * Polls, predictions, hype trains and ad breaks are not here: Twitch only gives them to the broadcaster's own token.
         */
        internal val OPTIONAL = listOf(
            "channel.chat.notification" to "1",
            "channel.chat.message_delete" to "1",
            "channel.chat.clear_user_messages" to "1",
            "channel.chat.clear" to "1",
            "channel.chat_settings.update" to "1",
            "stream.offline" to "1",
            "channel.update" to "2",
            "channel.raid" to "1",
        )

        internal fun chatCondition(broadcasterId: String, userId: String): JsonObject = buildJsonObject {
            put("broadcaster_user_id", broadcasterId); put("user_id", userId)
        }

        internal fun conditionFor(type: String, broadcasterId: String, userId: String): JsonObject = when (type) {
            "stream.offline", "channel.update" -> buildJsonObject { put("broadcaster_user_id", broadcasterId) }
            "channel.raid" -> buildJsonObject { put("from_broadcaster_user_id", broadcasterId) }
            else -> chatCondition(broadcasterId, userId)
        }

        /** The non-message events; null for a type that is not one or a payload that does not fit. */
        internal fun decodeSignal(type: String?, e: JsonObject): ChatEvent? {
            fun str(key: String) = e[key]?.jsonPrimitive?.contentOrNull
            fun int(key: String) = (e[key] as? JsonPrimitive)?.intOrNull
            fun flag(key: String) = (e[key] as? JsonPrimitive)?.booleanOrNull == true
            return when (type) {
                "channel.chat.message_delete" -> str("message_id")?.let { ChatEvent.Deleted(it) }
                "channel.chat.clear_user_messages" -> str("target_user_login")?.let { ChatEvent.UserCleared(it) }
                "channel.chat.clear" -> ChatEvent.Cleared
                "channel.chat_settings.update" -> ChatEvent.Room(
                    RoomState(
                        emoteOnly = flag("emote_mode"),
                        followersOnlyMinutes = if (flag("follower_mode")) int("follower_mode_duration_minutes") ?: 0 else null,
                        slowSeconds = if (flag("slow_mode")) int("slow_mode_wait_time_seconds") ?: 0 else null,
                        subscribersOnly = flag("subscriber_mode"),
                        uniqueOnly = flag("unique_chat_mode"),
                    ),
                )
                "stream.offline" -> ChatEvent.Channel(ChannelNotice.Offline)
                "channel.update" -> ChatEvent.Channel(ChannelNotice.Updated(str("title")?.takeIf(String::isNotBlank), str("category_name")?.takeIf(String::isNotBlank)))
                "channel.raid" -> ChatEvent.Channel(
                    ChannelNotice.Raid(
                        fromName = str("from_broadcaster_user_name").orEmpty(), toName = str("to_broadcaster_user_name").orEmpty(),
                        toLogin = str("to_broadcaster_user_login").orEmpty(), viewers = int("viewers") ?: 0,
                    ),
                )
                else -> null
            }
        }

        fun createHttpClient() = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(WebSockets)
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
    }
}

@Serializable private data class EventSubEnvelope(val metadata: EventSubMetadata, val payload: EventSubPayload)
@Serializable private data class EventSubMetadata(@SerialName("message_type") val messageType: String,
    @SerialName("message_id") val messageId: String = "", @SerialName("subscription_type") val subscriptionType: String? = null,
    @SerialName("message_timestamp") val messageTimestamp: String? = null)
@Serializable private data class EventSubPayload(val session: EventSubSession? = null, val event: kotlinx.serialization.json.JsonElement? = null)
@Serializable private data class EventSubSession(val id: String? = null, @SerialName("reconnect_url") val reconnectUrl: String? = null)
@Serializable private data class SubscriptionRequest(val type: String, val version: String = "1",
    val condition: JsonObject, val transport: WebSocketTransport)
@Serializable private data class WebSocketTransport(val method: String = "websocket", @SerialName("session_id") val sessionId: String)

