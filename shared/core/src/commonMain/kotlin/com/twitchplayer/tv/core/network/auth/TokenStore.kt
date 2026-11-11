package com.twitchplayer.tv.core.network.auth

import com.twitchplayer.tv.core.data.model.Account

data class TwitchTokens(val accessToken: String, val refreshToken: String, val expiresAtEpochSeconds: Long, val scopes: Set<String>)

/**
 * Where the OAuth tokens (secret) and the last validated account (public) are kept. Each platform stores them in its
 * secure store: Android Keystore + private preferences today, Keychain and others later.
 */
interface TokenStore {
    suspend fun save(tokens: TwitchTokens)

    /** The stored tokens, or null when none exist or they cannot be read (the store then clears itself). */
    suspend fun read(): TwitchTokens?

    /** The last validated account (public id and login, kept in clear) so the next start does not wait for validation. */
    suspend fun saveAccount(account: Account)
    suspend fun readAccount(): Account?
    suspend fun clear()
}
