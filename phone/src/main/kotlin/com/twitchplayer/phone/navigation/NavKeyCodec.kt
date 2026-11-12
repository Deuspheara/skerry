package com.twitchplayer.phone.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The saved form of a tab's back stack: one JSON string per page. Decoding never throws: anything it cannot read (a page a newer or
 * older build wrote, a corrupted bundle) resets that tab to its root instead of crashing the app on start.
 */
internal object NavKeyCodec {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(HomeKey::class)
                subclass(BrowseKey::class)
                subclass(SearchKey::class)
                subclass(SettingsKey::class)
                subclass(CategoryKey::class)
                subclass(ChannelKey::class)
            }
        }
    }
    private val serializer = PolymorphicSerializer(NavKey::class)

    fun encode(key: NavKey): String = json.encodeToString(serializer, key)

    fun decode(text: String): NavKey? = runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    /** The stack to show: the saved pages when every one of them reads and the stack starts at [root]; otherwise just [root]. */
    fun restore(saved: List<String>?, root: NavKey): List<NavKey> {
        val keys = saved?.map(::decode) ?: return listOf(root)
        if (keys.isEmpty() || keys.any { it == null } || keys.first() != root) return listOf(root)
        return keys.filterNotNull()
    }
}
