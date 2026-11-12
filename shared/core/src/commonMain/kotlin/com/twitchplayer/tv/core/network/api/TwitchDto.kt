package com.twitchplayer.tv.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HelixPage<T>(val data: List<T> = emptyList(), val pagination: Pagination = Pagination())

@Serializable
data class Pagination(val cursor: String? = null)

@Serializable
data class TwitchUser(
    val id: String,
    val login: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("profile_image_url") val avatarUrl: String = "",
)

@Serializable
data class TwitchGame(
    val id: String,
    val name: String,
    @SerialName("box_art_url") val boxArtUrl: String = "",
)

@Serializable
data class TwitchStream(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_login") val userLogin: String,
    @SerialName("user_name") val userName: String,
    val title: String,
    @SerialName("game_id") val categoryId: String = "",
    @SerialName("game_name") val category: String = "",
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("started_at") val startedAt: String = "",
    val tags: List<String> = emptyList(),
    val language: String = "",
)

@Serializable
data class FollowedChannel(
    @SerialName("broadcaster_id") val broadcasterId: String,
    @SerialName("broadcaster_login") val login: String,
    @SerialName("broadcaster_name") val name: String,
)

@Serializable
data class TwitchChannelSearch(
    @SerialName("id") val id: String,
    @SerialName("broadcaster_login") val login: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("game_name") val category: String = "",
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("thumbnail_url") val avatarUrl: String = "",
)

@Serializable
data class TwitchVideo(
    val id: String,
    val title: String = "",
    val duration: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("view_count") val viewCount: Int = 0,
)

@Serializable
data class TwitchBadgeSet(
    @SerialName("set_id") val setId: String,
    val versions: List<TwitchBadgeVersion> = emptyList(),
)

@Serializable
data class TwitchBadgeVersion(
    val id: String,
    @SerialName("image_url_1x") val image1x: String = "",
    @SerialName("image_url_2x") val image2x: String = "",
    @SerialName("image_url_4x") val image4x: String = "",
    val title: String = "",
)

/**
 * An emote of `GET /chat/emotes/global`, `GET /chat/emotes` (a channel's) or `GET /chat/emotes/user`. [emoteType] is `subscriptions`,
 * `bitstier`, `follower`, `globals`, `smilies`... (free text: the API lists more over time); [ownerId] is the channel it belongs to.
 * The image is the documented CDN template, so only the id and the available [format]s matter (`static`, plus `animated` for some).
 */
@Serializable
data class TwitchEmote(
    val id: String,
    val name: String,
    @SerialName("emote_type") val emoteType: String = "",
    @SerialName("emote_set_id") val emoteSetId: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    val format: List<String> = emptyList(),
    val tier: String = "",
)

/** `GET /clips`: [id] is the clip's slug (what Twitch's player interface takes). Helix gives no playable URL. */
@Serializable
data class TwitchClip(
    val id: String,
    val title: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    val duration: Double = 0.0,
)

/** `GET /chat/settings`: the room's modes. Durations are minutes (followers) and seconds (slow); null when the mode is off. */
@Serializable
data class TwitchChatSettings(
    @SerialName("emote_mode") val emoteMode: Boolean = false,
    @SerialName("follower_mode") val followerMode: Boolean = false,
    @SerialName("follower_mode_duration") val followerModeDuration: Int? = null,
    @SerialName("slow_mode") val slowMode: Boolean = false,
    @SerialName("slow_mode_wait_time") val slowModeWaitTime: Int? = null,
    @SerialName("subscriber_mode") val subscriberMode: Boolean = false,
    @SerialName("unique_chat_mode") val uniqueChatMode: Boolean = false,
)

@Serializable
data class SendChatRequest(
    @SerialName("broadcaster_id") val broadcasterId: String,
    @SerialName("sender_id") val senderId: String,
    val message: String,
    @SerialName("reply_parent_message_id") val replyParentMessageId: String? = null,
)

@Serializable
data class SendChatResult(
    @SerialName("message_id") val messageId: String = "",
    @SerialName("is_sent") val isSent: Boolean = false,
    @SerialName("drop_reason") val dropReason: DropReason? = null,
)

@Serializable
data class DropReason(val code: String = "", val message: String = "")

/** `GET /schedule`: the data is one object holding the segments (not a list), and the whole call is a 404 without a schedule. */
@Serializable
data class ScheduleEnvelope(val data: ScheduleData = ScheduleData())

@Serializable
data class ScheduleData(val segments: List<ScheduleSegment>? = null)

@Serializable
data class ScheduleSegment(
    val id: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String? = null,
    val title: String = "",
    @SerialName("canceled_until") val canceledUntil: String? = null,
    val category: ScheduleCategory? = null,
)

@Serializable
data class ScheduleCategory(val id: String = "", val name: String = "")
