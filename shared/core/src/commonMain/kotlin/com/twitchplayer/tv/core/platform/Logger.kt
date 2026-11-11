package com.twitchplayer.tv.core.platform

/**
 * Where shared code writes diagnostics. Only warnings exist on purpose: on Android the implementation calls
 * `Log.w`, which survives the release build's R8 stripping of `Log.v/d/i`, and these lines (a refused playback token,
 * a dropped chat connection) are what is needed to diagnose a failure in the field.
 */
fun interface Logger {
    fun warn(tag: String, message: String)
}
