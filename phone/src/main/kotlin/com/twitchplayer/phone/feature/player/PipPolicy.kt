package com.twitchplayer.phone.feature.player

/** When leaving the app should turn the video into a Picture-in-Picture window, and with which shape. */
object PipPolicy {
    /** Only a picture that is actually playing on the player screen: never a paused, failed or not yet started one. */
    fun eligible(onPlayerScreen: Boolean, hasPicture: Boolean, playing: Boolean, failed: Boolean): Boolean = onPlayerScreen && hasPicture && playing && !failed

    /**
     * The window's aspect ratio as (numerator, denominator), clamped to what Android accepts (between 1:2.39 and 2.39:1).
     * Unknown sizes give 16:9.
     */
    fun aspect(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return DEFAULT
        val ratio = width.toDouble() / height
        return when {
            ratio > MAX_RATIO -> MAX_PAIR
            ratio < 1.0 / MAX_RATIO -> MIN_PAIR
            else -> width to height
        }
    }

    private val DEFAULT = 16 to 9
    private const val MAX_RATIO = 2.39
    private val MAX_PAIR = 239 to 100
    private val MIN_PAIR = 100 to 239
}
