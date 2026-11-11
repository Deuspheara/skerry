package com.twitchplayer.tv.feature.player.ui.embed

import java.net.URI

/**
 * Identifies the page controlled by this app that hosts Twitch's official embed.
 * `parentDomain` must be the exact HTTPS hostname that serves the page.
 */
data class TwitchPlayerConfig(
    val hostedPageBaseUrl: String,
    val parentDomain: String,
) {
    init {
        val uri = runCatching { URI(hostedPageBaseUrl) }
            .getOrElse { throw IllegalArgumentException("Invalid hosted player URL", it) }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Hosted player page must use HTTPS." }
        require(!uri.host.isNullOrBlank()) { "Hosted player URL must include a hostname." }
        require(uri.userInfo == null) { "Hosted player URL must not contain credentials." }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Hosted player base URL must not include a query or fragment."
        }
        require(uri.host.equals(parentDomain, ignoreCase = true)) {
            "Twitch parent must match the controlled hosted page hostname."
        }
        require(parentDomain.matches(Regex("[A-Za-z0-9.-]+"))) { "Invalid Twitch parent hostname." }
    }

    /** The lower-cased hostname of the hosted page (the WebView navigation policy compares against it). */
    val host: String = URI(hostedPageBaseUrl).host.lowercase()

    /** Uses the configured controlled page; the page itself is responsible for loading Twitch's official embed. */
    fun pageUrl(channelLogin: String, autoplay: Boolean = false): String {
        require(channelLogin.matches(Regex("[A-Za-z0-9_]{1,25}"))) { "Invalid Twitch channel login." }
        val base = if (hostedPageBaseUrl.endsWith('/')) hostedPageBaseUrl else "$hostedPageBaseUrl/"
        return "$base?channel=$channelLogin&parent=$parentDomain" + if (autoplay) "&autoplay=1" else ""
    }
}

/** DI holder: Koin cannot provide null, and the hosted page may be unconfigured. */
class PlayerConfigProvider(val config: TwitchPlayerConfig?)
