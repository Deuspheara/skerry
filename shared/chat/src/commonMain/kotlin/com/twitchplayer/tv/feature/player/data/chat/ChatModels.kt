package com.twitchplayer.tv.feature.player.data.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [format] lists "static" and/or "animated" (EventSub `channel.chat.message` fragment emote). */
@Serializable data class ChatEmote(val id: String, @SerialName("emote_set_id") val emoteSetId: String? = null, val format: List<String> = emptyList())

@Serializable data class ChatMention(@SerialName("user_login") val login: String = "")

@Serializable data class ChatCheermote(val prefix: String = "", val bits: Int = 0, val tier: Int = 0)

@Serializable data class ChatFragment(
    val type: String,
    val text: String,
    val emote: ChatEmote? = null,
    val mention: ChatMention? = null,
    val cheermote: ChatCheermote? = null,
)

@Serializable data class ChatBadge(@SerialName("set_id") val setId: String, val id: String, val info: String = "")

@Serializable data class ChatCheer(val bits: Int = 0)

@Serializable data class ChatMessage(
    @SerialName("message_id")
    val id: String,
    @SerialName("broadcaster_user_id") val broadcasterId: String,
    @SerialName("chatter_user_id") val userId: String,
    @SerialName("chatter_user_name") val username: String,
    @SerialName("chatter_user_login") val login: String,
    @SerialName("message") val content: ChatMessageContent,
    /** "text", "channel_points_highlighted", "channel_points_sub_only", "user_intro", "power_ups_*". */
    @SerialName("message_type") val messageType: String = "",
    @SerialName("badges") val badges: List<ChatBadge> = emptyList(),
    @SerialName("color") val color: String? = null,
    @SerialName("cheer") val cheer: ChatCheer? = null,
    @SerialName("message_timestamp") val timestamp: String? = null,
)

@Serializable data class ChatMessageContent(val text: String, val fragments: List<ChatFragment> = emptyList())

/** `channel.chat.notification` (sub, resub, gift, raid, announcement...): Twitch writes the line itself in [systemMessage]. */
@Serializable data class ChatNotification(
    @SerialName("system_message") val systemMessage: String = "",
    @SerialName("message") val content: ChatMessageContent? = null,
)

/** What happened on the watched channel, outside the chat lines. */
sealed interface ChannelNotice {
    /** The channel sent its viewers to [toLogin]. [following] is set once the app follows them. */
    data class Raid(val fromName: String, val toName: String, val toLogin: String, val viewers: Int, val following: Boolean = false) : ChannelNotice

    /** New title and / or category (null = unchanged is not sent by Twitch: both are always present, blank means unknown). */
    data class Updated(val title: String?, val category: String?) : ChannelNotice
    data object Offline : ChannelNotice
}

/** What the transport delivers; the renderer only ever sees [com.twitchplayer.tv.feature.player.presentation.ChatLine]. */
sealed interface ChatEvent {
    data class Message(val message: ChatMessage) : ChatEvent

    /** [id] is unique per notice (EventSub message id). */
    data class Notice(val id: String, val text: String) : ChatEvent

    /** A moderator removed one message. */
    data class Deleted(val messageId: String) : ChatEvent

    /** A timeout or ban: every message of [login] goes. */
    data class UserCleared(val login: String) : ChatEvent

    /** A moderator cleared the whole chat. */
    data object Cleared : ChatEvent
    data class Room(val state: RoomState) : ChatEvent
    data class Channel(val notice: ChannelNotice) : ChatEvent
}
