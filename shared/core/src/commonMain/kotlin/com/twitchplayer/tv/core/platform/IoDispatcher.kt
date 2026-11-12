package com.twitchplayer.tv.core.platform

import kotlinx.coroutines.CoroutineDispatcher

/** The dispatcher for blocking disk and network-free I/O: `Dispatchers.IO` where it exists, `Dispatchers.Default` on Native. */
expect val ioDispatcher: CoroutineDispatcher
