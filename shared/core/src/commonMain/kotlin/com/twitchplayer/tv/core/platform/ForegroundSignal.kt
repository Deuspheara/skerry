package com.twitchplayer.tv.core.platform

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * "The app came back to the foreground". The platform calls [appCameToForeground] (Android: the activity's `onStart`); screens that
 * show a live feed listen to [events] and refresh themselves when their data is old enough (see `Loader`). A burst of signals
 * collapses to one: nothing is replayed to a screen that was not listening.
 */
class ForegroundSignal {
    private val mutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<Unit> = mutable

    fun appCameToForeground() {
        mutable.tryEmit(Unit)
    }
}
