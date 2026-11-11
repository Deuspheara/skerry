package com.twitchplayer.phone.feature.player

import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
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

/** A playback error that is the network's fault (no connection, a timeout) rather than a file or codec problem. */
fun PlaybackException.isNetworkError(): Boolean = errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

/** [ReplayError.Network] or [ReplayError.Playback] for an error raised by the player itself. */
fun PlaybackException.toReplayError(): ReplayError = if (isNetworkError()) ReplayError.Network else ReplayError.Playback

/** Why a clip page shows an error; both offer Retry (a clip's address carries a short-lived token, so a retry resolves it again). */
enum class ClipFailure(@StringRes val title: Int, @StringRes val line: Int) {
    Network(R.string.clip_unavailable, R.string.vod_failed_net_line),
    Unavailable(R.string.clip_unavailable, R.string.clip_unavailable_line),
}

/** What the clip resolver throws: a network failure is told apart from a clip that is gone or refused. */
fun Throwable.toClipFailure(): ClipFailure = if (this is IOException) ClipFailure.Network else ClipFailure.Unavailable

fun PlaybackException.toClipFailure(): ClipFailure = if (isNetworkError()) ClipFailure.Network else ClipFailure.Unavailable
