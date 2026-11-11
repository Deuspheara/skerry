package com.twitchplayer.tv.feature.vod.ui

import androidx.annotation.StringRes
import com.twitchplayer.tv.R
import com.twitchplayer.tv.feature.player.data.stream.VodAccessException
import com.twitchplayer.tv.feature.player.data.stream.VodFailure
import java.io.IOException

/** Why the replay screen shows an error; [retryable] decides whether it offers Retry. */
enum class VodError(@StringRes val title: Int, @StringRes val line: Int, val retryable: Boolean) {
    Gone(R.string.vod_gone, R.string.vod_gone_line, false),
    SubscribersOnly(R.string.vod_subs, R.string.vod_subs_line, false),
    Refused(R.string.vod_failed, R.string.vod_failed_line, true),
    Network(R.string.vod_failed, R.string.vod_failed_net_line, true),
    Playback(R.string.vod_failed, R.string.vod_failed_line, true),
}

internal fun Throwable.toVodError(): VodError = when {
    this is VodAccessException -> when (failure) {
        VodFailure.NOT_FOUND -> VodError.Gone
        VodFailure.SUBSCRIBER_ONLY -> VodError.SubscribersOnly
        VodFailure.TOKEN, VodFailure.UNAVAILABLE -> VodError.Refused
    }

    this is IOException -> VodError.Network

    else -> VodError.Refused
}
