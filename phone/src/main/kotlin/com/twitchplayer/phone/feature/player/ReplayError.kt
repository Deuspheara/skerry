package com.twitchplayer.phone.feature.player

import androidx.annotation.StringRes
import com.twitchplayer.phone.R
import com.twitchplayer.tv.feature.player.data.stream.VodAccessException
import com.twitchplayer.tv.feature.player.data.stream.VodFailure
import java.io.IOException

/** Why a replay page shows an error; [retryable] decides whether it offers Retry. */
enum class ReplayError(@StringRes val title: Int, @StringRes val line: Int, val retryable: Boolean) {
    Gone(R.string.vod_gone, R.string.vod_gone_line, false),
    SubscribersOnly(R.string.vod_subs, R.string.vod_subs_line, false),
    Refused(R.string.vod_failed, R.string.vod_failed_line, true),
    Network(R.string.vod_failed, R.string.vod_failed_net_line, true),
    Playback(R.string.vod_failed, R.string.vod_failed_line, true),
}

/** Maps what the resolver throws to the message shown (the same mapping as the TV replay screen). */
fun Throwable.toReplayError(): ReplayError = when {
    this is VodAccessException -> when (failure) {
        VodFailure.NOT_FOUND -> ReplayError.Gone
        VodFailure.SUBSCRIBER_ONLY -> ReplayError.SubscribersOnly
        VodFailure.TOKEN, VodFailure.UNAVAILABLE -> ReplayError.Refused
    }

    this is IOException -> ReplayError.Network

    else -> ReplayError.Refused
}

private val CLIP_SLUG = Regex("[A-Za-z0-9_-]{1,100}")

/** "title\nhttps://clips.twitch.tv/slug" for the share sheet; null for a slug that is not a plain clip id (never build a link from it). */
fun clipShareText(title: String, slug: String): String? = if (CLIP_SLUG.matches(slug)) "${title.trim()}\nhttps://clips.twitch.tv/$slug".trim() else null
