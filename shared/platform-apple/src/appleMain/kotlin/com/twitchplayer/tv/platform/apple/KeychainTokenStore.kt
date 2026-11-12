package com.twitchplayer.tv.platform.apple

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.network.auth.TokenStore
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import com.twitchplayer.tv.core.platform.ioDispatcher
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * OAuth tokens (secret) and the last validated account (public id and login) in the Keychain, one generic-password item each
 * under [service]. Same clear-text layout as the Android store (access, refresh, expiry, scopes joined by newlines) so a
 * future migration needs no format change. Items are readable after the first unlock and never leave the device
 * (no iCloud Keychain sync, no backup restore to another device).
 */
class KeychainTokenStore(private val service: String = DEFAULT_SERVICE) : TokenStore {
    override suspend fun save(tokens: TwitchTokens) = withContext(ioDispatcher) {
        val text = listOf(tokens.accessToken, tokens.refreshToken, tokens.expiresAtEpochSeconds.toString(), tokens.scopes.joinToString(" ")).joinToString("\n")
        put(TOKENS, text)
    }

    override suspend fun read(): TwitchTokens? = withContext(ioDispatcher) {
        val text = get(TOKENS) ?: return@withContext null
        runCatching {
            val parts = text.split('\n')
            TwitchTokens(parts[0], parts[1], parts[EXPIRY_PART].toLong(), parts.getOrElse(SCOPES_PART) { "" }.split(' ').filter(String::isNotBlank).toSet())
        }.getOrElse {
            remove(TOKENS)
            null
        }
    }

    override suspend fun saveAccount(account: Account) = withContext(ioDispatcher) {
        put(ACCOUNT, account.userId + "\n" + account.login)
    }

    override suspend fun readAccount(): Account? = withContext(ioDispatcher) {
        val parts = get(ACCOUNT)?.split('\n') ?: return@withContext null
        parts.firstOrNull()?.takeIf(String::isNotBlank)?.let { Account(it, parts.getOrElse(1) { "" }) }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        remove(TOKENS)
        remove(ACCOUNT)
    }

    private fun put(account: String, text: String) {
        remove(account)
        val data = text.toNsData()
        val status = query(account, { dictionary, retained ->
            val value = CFBridgingRetain(data).also(retained::add)
            CFDictionaryAddValue(dictionary, kSecValueData, value)
            CFDictionaryAddValue(dictionary, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }) { SecItemAdd(it, null) }
        check(status == errSecSuccess) { "Keychain write failed ($status)" }
    }

    private fun get(account: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = query(account, { dictionary, _ ->
            CFDictionaryAddValue(dictionary, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dictionary, kSecMatchLimit, kSecMatchLimitOne)
        }) { SecItemCopyMatching(it, result.ptr) }
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    private fun remove(account: String) {
        query(account, { _, _ -> }) { SecItemDelete(it) }
    }

    /**
     * Builds the attribute dictionary of one item (class, service, account + whatever [extra] adds) and runs [call] with it.
     * The dictionary does not retain its keys and values (null callbacks), so every value created here is released after the call.
     */
    private fun <T> query(account: String, extra: (CFMutableDictionaryRef?, MutableList<CFTypeRef?>) -> Unit, call: (CFDictionaryRef?) -> T): T {
        val dictionary = CFDictionaryCreateMutable(null, 0, null, null)
        val retained = mutableListOf<CFTypeRef?>()
        try {
            CFDictionaryAddValue(dictionary, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(dictionary, kSecAttrService, CFBridgingRetain(service).also(retained::add))
            CFDictionaryAddValue(dictionary, kSecAttrAccount, CFBridgingRetain(account).also(retained::add))
            extra(dictionary, retained)
            return call(dictionary)
        } finally {
            retained.forEach { CFRelease(it) }
            CFRelease(dictionary)
        }
    }

    private fun String.toNsData(): NSData = NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()

    companion object {
        const val DEFAULT_SERVICE = "com.twitchplayer.tokens"
        private const val TOKENS = "tokens"
        private const val ACCOUNT = "account"
        private const val EXPIRY_PART = 2
        private const val SCOPES_PART = 3
    }
}
