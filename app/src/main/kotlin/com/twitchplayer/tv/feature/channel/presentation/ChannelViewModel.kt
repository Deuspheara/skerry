package com.twitchplayer.tv.feature.channel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps the channel presenter across configuration changes; the presenter itself lives in `:shared:presenters`.
 * The focus memory below is UI state (it only restores D-pad focus when coming back), so it stays on the Android side.
 */
class ChannelViewModel(create: (CoroutineScope) -> ChannelPresenter) : ViewModel() {
    val presenter = create(viewModelScope)

    /** Slug of the clip last opened: coming back from the clip screen puts focus on its card again. */
    var lastClip: String? = null

    /** Id of the replay last opened, for the same reason. */
    var lastReplay: String? = null
}
