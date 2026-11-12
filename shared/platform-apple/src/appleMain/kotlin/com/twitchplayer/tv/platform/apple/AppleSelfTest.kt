package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.NumberFormatter
import com.twitchplayer.tv.core.platform.SnapshotStorage

/**
 * Exercises the Apple platform seams of the running app (snapshot files, Keychain, locale, number formatting) and reports one
 * line per check. The smoke app shows it; it is also a handy "About > diagnostics" source. The Keychain check writes and
 * removes a probe entry under its own service name, never touching the real session.
 */
class AppleSelfTest internal constructor(
    private val snapshots: SnapshotStorage,
    private val deviceProfile: DeviceProfile,
    private val numbers: NumberFormatter,
) {
    suspend fun run(): List<String> = listOf(
        snapshotCheck(),
        keychainCheck(),
        "language: ${deviceProfile.languageTag}",
        "number: ${numbers.decimal(PROBE_NUMBER, 2)}",
    )

    private suspend fun snapshotCheck(): String = runCatching {
        snapshots.write("selftest", "hello")
        if (snapshots.read("selftest") == "hello") "snapshot round trip: ok" else "snapshot round trip: FAILED (read back differs)"
    }.getOrElse { "snapshot round trip: FAILED (${it.message})" }

    private suspend fun keychainCheck(): String {
        val probe: TokenStore = KeychainTokenStore(PROBE_SERVICE)
        return runCatching {
            probe.save(TwitchTokens("a", "r", 1L, setOf("chat:read")))
            val back = probe.read()
            probe.clear()
            if (back?.accessToken == "a") "keychain round trip: ok" else "keychain round trip: FAILED (read back $back)"
        }.getOrElse { "keychain round trip: FAILED (${it.message})" }
    }

    private companion object {
        const val PROBE_SERVICE = "com.twitchplayer.selftest"
        const val PROBE_NUMBER = 1.25f
    }
}
