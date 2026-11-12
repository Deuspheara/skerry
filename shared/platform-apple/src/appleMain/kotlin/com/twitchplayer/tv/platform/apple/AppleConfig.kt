package com.twitchplayer.tv.platform.apple

/** What only the app knows. The Twitch client id is public by design (there is no secret). */
data class AppleConfig(val twitchClientId: String, val keychainService: String = KeychainTokenStore.DEFAULT_SERVICE)
