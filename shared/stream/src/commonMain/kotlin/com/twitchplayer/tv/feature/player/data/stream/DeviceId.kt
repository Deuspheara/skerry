package com.twitchplayer.tv.feature.player.data.stream

import kotlin.uuid.Uuid

/** A random 32-character lower-case hex id (the previous `UUID.randomUUID()` without dashes) for the `X-Device-Id` header. */
internal fun newDeviceId(): String = Uuid.random().toHexString()
