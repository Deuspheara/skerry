package com.twitchplayer.phone.feature.auth

import java.net.URI

/**
 * True for an https URL on twitch.tv or one of its subdomains, without credentials and on the default port. The sign-in code and the
 * "open on Twitch" actions only ever open such a URL: the address comes from a network response, so it is checked before it is handed
 * to the system (a look-alike host such as `twitch.tv.example.com` or `evil-twitch.tv` is refused).
 */
fun isTwitchUrl(raw: String): Boolean {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.rawUserInfo == null &&
        (uri.port == -1 || uri.port == HTTPS_PORT) &&
        (host == TWITCH_HOST || host.endsWith(".$TWITCH_HOST"))
}

/** The page of a channel, for "open on Twitch"; the login is path-encoded by the caller's data (logins are `[a-z0-9_]`). */
fun channelUrl(login: String): String = "https://www.$TWITCH_HOST/${login.lowercase()}"

private const val TWITCH_HOST = "twitch.tv"
private const val HTTPS_PORT = 443
