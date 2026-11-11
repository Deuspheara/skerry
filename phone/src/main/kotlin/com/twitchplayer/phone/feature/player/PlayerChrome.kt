package com.twitchplayer.phone.feature.player

/** What a player page can do with the shared chrome around the picture (see [PlayerScaffold]). */
class PlayerChrome internal constructor(
    val controls: ControlsVisibility,
    /** True when the picture fills a landscape screen. */
    val landscape: Boolean,
    private val onToggleFullscreen: () -> Unit,
) {
    fun toggleFullscreen() = onToggleFullscreen()
}
