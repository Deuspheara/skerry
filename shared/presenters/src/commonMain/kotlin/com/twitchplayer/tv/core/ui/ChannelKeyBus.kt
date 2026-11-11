package com.twitchplayer.tv.core.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Hardware CHANNEL +/- (and page) keys, forwarded by the activity: +1 next, -1 previous. */
class ChannelKeyBus {
    private val mutable = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events: SharedFlow<Int> get() = mutable
    fun emit(delta: Int) = mutable.tryEmit(delta)
}
