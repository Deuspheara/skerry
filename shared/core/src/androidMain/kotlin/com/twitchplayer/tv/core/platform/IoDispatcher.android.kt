package com.twitchplayer.tv.core.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// The one place the IO dispatcher is named; everything else receives it (SnapshotCache takes it as a parameter).
@Suppress("InjectDispatcher")
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
