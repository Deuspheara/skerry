package com.twitchplayer.tv.core.network.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.twitchplayer.tv.core.data.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TwitchTokens(val accessToken: String, val refreshToken: String, val expiresAtEpochSeconds: Long, val scopes: Set<String>)

/** Ciphertext lives in private preferences; its AES key is generated and retained by Android Keystore. */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("twitch_secure_tokens", Context.MODE_PRIVATE)

    suspend fun save(tokens: TwitchTokens) = withContext(Dispatchers.IO) {
        val clear = listOf(tokens.accessToken, tokens.refreshToken, tokens.expiresAtEpochSeconds.toString(), tokens.scopes.joinToString(" ")).joinToString("\n")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(clear.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size).putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
        check(prefs.edit().putString("payload", Base64.encodeToString(payload, Base64.NO_WRAP)).commit())
    }

    suspend fun read(): TwitchTokens? = withContext(Dispatchers.IO) {
        val encoded = prefs.getString("payload", null) ?: return@withContext null
        runCatching {
            val data = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val iv = ByteArray(data.int).also(data::get)
            val ciphertext = ByteArray(data.remaining()).also(data::get)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val parts = cipher.doFinal(ciphertext).toString(Charsets.UTF_8).split('\n')
            TwitchTokens(parts[0], parts[1], parts[2].toLong(), parts.getOrElse(3) { "" }.split(' ').filter(String::isNotBlank).toSet())
        }.getOrElse {
            clear()
            null
        }
    }

    /** The last validated account (public id and login, kept in clear) so the next start does not wait for validation. */
    suspend fun saveAccount(account: Account) = withContext(Dispatchers.IO) {
        prefs.edit().putString("user_id", account.userId).putString("login", account.login).apply()
    }

    suspend fun readAccount(): Account? = withContext(Dispatchers.IO) {
        val id = prefs.getString("user_id", null) ?: return@withContext null
        Account(id, prefs.getString("login", null).orEmpty())
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().commit()
        Unit
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build(),
            )
            generateKey()
        }
    }
    private companion object {
        const val KEY_ALIAS = "twitch_player_oauth_tokens"
    }
}
