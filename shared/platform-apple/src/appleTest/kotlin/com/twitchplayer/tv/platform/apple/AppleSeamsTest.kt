package com.twitchplayer.tv.platform.apple

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.network.auth.TwitchTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LARGE_NUMBER = 12345f

private fun scratchDirectory(): String = NSTemporaryDirectory().trimEnd('/') + "/twitchplayer-test-" + NSUUID().UUIDString

class AppleSnapshotStorageTest {
    @Test fun aWrittenSnapshotIsReadBack() = runTest {
        val storage = AppleSnapshotStorage(scratchDirectory())
        storage.write("home_1", """{"streams":["é","日本"]}""")
        assertEquals("""{"streams":["é","日本"]}""", storage.read("home_1"))
    }

    @Test fun aMissingSnapshotIsNullAndNeverThrows() = runTest {
        assertNull(AppleSnapshotStorage(scratchDirectory()).read("nothing"))
    }

    @Test fun writingAgainReplacesTheText() = runTest {
        val storage = AppleSnapshotStorage(scratchDirectory())
        storage.write("a", "one")
        storage.write("a", "two")
        assertEquals("two", storage.read("a"))
    }

    @Test fun clearRemovesEveryEntryAndTheStorageStillWorks() = runTest {
        val storage = AppleSnapshotStorage(scratchDirectory())
        storage.write("a", "one")
        storage.clear()
        assertNull(storage.read("a"))
        storage.write("b", "two")
        assertEquals("two", storage.read("b"))
    }
}

class AppleNumberFormatterTest {
    @Test fun trailingZerosAreDroppedAndTheDecimalsAreCapped() {
        val text = AppleNumberFormatter().decimal(1.25f, 1)
        assertTrue(text == "1.2" || text == "1,2" || text == "1.3" || text == "1,3", text)
        assertEquals("1", AppleNumberFormatter().decimal(1.0f, 2))
    }

    @Test fun noGroupingSeparatorIsAdded() {
        assertEquals(LARGE_NUMBER.toInt().toString(), AppleNumberFormatter().decimal(LARGE_NUMBER, 0))
    }
}

class AppleDataStoreTest {
    @Test fun aWrittenValueIsReadBackAndTheFileLandsInTheDirectory() = runTest {
        val directory = scratchDirectory().also(::ensureDirectory)
        val key = stringPreferencesKey("k")
        val store = AppleDataStores.create("settings", directory)
        store.edit { it[key] = "v" }
        assertEquals("v", store.data.first()[key])
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath("$directory/settings.preferences_pb"))
    }
}

/**
 * The Keychain needs the app's keychain entitlement, which the unsigned Kotlin/Native test executable of the iOS simulator
 * does not have (`errSecMissingEntitlement`): there the round trip is exercised by the smoke app, which is a signed app.
 */
@OptIn(ExperimentalNativeApi::class)
private val keychainAvailableInTests: Boolean = Platform.osFamily == OsFamily.MACOSX

/** The real directories of the platform (not scratch ones): on tvOS only Caches and tmp are writable, and the files must really be written there. */
@OptIn(ExperimentalNativeApi::class)
class ApplePathsTest {
    @Test fun theSupportDirectoryExistsAndIsWritableOnThisPlatform() = runTest {
        val directory = ApplePaths.supportDirectory()
        assertTrue(NSFileManager.defaultManager.isWritableFileAtPath(directory), "not writable: $directory")
        if (Platform.osFamily == OsFamily.TVOS) assertTrue("/Caches/" in directory, "tvOS must use Caches: $directory")
    }

    @Test fun aDataStoreInTheDefaultDirectoryWritesAndReadsBack() = runTest {
        val key = stringPreferencesKey("k")
        val store = AppleDataStores.create("paths-test-" + NSUUID().UUIDString)
        store.edit { it[key] = "v" }
        assertEquals("v", store.data.first()[key])
    }

    @Test fun theDefaultSnapshotStorageWritesReadsAndClears() = runTest {
        val storage = AppleSnapshotStorage()
        storage.write("paths_test", "x")
        assertEquals("x", storage.read("paths_test"))
        storage.clear()
        assertNull(storage.read("paths_test"))
    }

    @Test fun anImpossibleDirectoryIsReportedNotSwallowed() {
        val failure = runCatching { ensureDirectory("/System/twitchplayer-cannot-exist/x") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && "Could not create" in (failure.message ?: ""), failure.toString())
    }
}

class KeychainTokenStoreTest {
    private val service = "com.twitchplayer.tests." + NSUUID().UUIDString

    @Test fun tokensAndAccountRoundTripThenClear() = runTest {
        if (!keychainAvailableInTests) return@runTest
        val store = KeychainTokenStore(service)
        val tokens = TwitchTokens("access", "refresh", 1_800_000_000L, setOf("user:read:follows", "chat:read"))
        store.save(tokens)
        store.saveAccount(Account("42", "somebody"))
        assertEquals(tokens, store.read())
        assertEquals(Account("42", "somebody"), store.readAccount())
        store.clear()
        assertNull(store.read())
        assertNull(store.readAccount())
    }

    @Test fun savingTwiceKeepsTheLatest() = runTest {
        if (!keychainAvailableInTests) return@runTest
        val store = KeychainTokenStore(service)
        store.save(TwitchTokens("a1", "r1", 1L, emptySet()))
        store.save(TwitchTokens("a2", "r2", 2L, setOf("x")))
        assertEquals(TwitchTokens("a2", "r2", 2L, setOf("x")), store.read())
        store.clear()
    }

    @Test fun anotherStoreOnTheSameServiceSeesTheItems() = runTest {
        if (!keychainAvailableInTests) return@runTest
        KeychainTokenStore(service).save(TwitchTokens("shared", "r", 1L, emptySet()))
        assertEquals("shared", KeychainTokenStore(service).read()?.accessToken)
        KeychainTokenStore(service).clear()
    }

    @Test fun nothingStoredReadsAsNull() = runTest {
        assertNull(KeychainTokenStore(service).read())
    }
}
