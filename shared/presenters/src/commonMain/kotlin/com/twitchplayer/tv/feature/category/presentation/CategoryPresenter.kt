package com.twitchplayer.tv.feature.category.presentation

import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.SnapshotCache
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.platform.DeviceProfile
import com.twitchplayer.tv.core.platform.ForegroundSignal
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.LoadCache
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

data class CategoryUiState(val streams: ImmutableList<LiveStream>, val favorite: Boolean)

/**
 * What the category screen shows: the streams ([load]), and the language of the header chip. [language] is the viewer's
 * Twitch language code and [languageOnly] whether the list is narrowed to it (both also while loading).
 */
data class CategoryScreenState(val load: Load<CategoryUiState>, val language: String, val languageOnly: Boolean)

/** Twitch stream language code for a BCP 47 [languageTag] (ISO 639-1, with the legacy codes and Norwegian mapped). */
fun twitchLanguage(languageTag: String): String {
    val parts = languageTag.split('-', '_')
    val language = parts.first().lowercase()
    val country = parts.drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }?.uppercase().orEmpty()
    return when (language) {
        "in" -> "id"
        "iw" -> "he"
        "nb", "nn" -> "no"
        "fil" -> "tl"
        "zh" -> if (country == "HK") "zh-hk" else "zh"
        else -> language
    }
}

sealed interface CategoryEvent {
    data object ToggleFavorite : CategoryEvent
    data object ToggleLanguage : CategoryEvent
    data object Retry : CategoryEvent
}

class CategoryPresenter(
    private val categoryId: String,
    repository: TwitchRepository,
    session: SessionRepository,
    private val settings: SettingsRepository,
    device: DeviceProfile,
    private val scope: CoroutineScope,
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
    snapshots: SnapshotCache? = null,
    foreground: ForegroundSignal? = null,
) {
    private val myLanguage = twitchLanguage(device.languageTag)

    // Starts on the viewer's language: a category's top streams are otherwise mostly in other languages.
    private val languageOnly = MutableStateFlow(true)

    // One stored copy per category and language filter, so the list comes back at once the next time the viewer opens it.
    private val snapshotName: String get() = "category-$categoryId-${if (languageOnly.value) myLanguage else "all"}"
    private val cache = snapshots?.let { store ->
        LoadCache<ImmutableList<LiveStream>>(
            read = { user -> store.read(snapshotName, user, ListSerializer(LiveStream.serializer()))?.toImmutableList() },
            write = { user, streams -> store.write(snapshotName, user, ListSerializer(LiveStream.serializer()), streams) },
        )
    }
    private val loader = Loader(scope, session, loadDispatcher, cache, foreground) {
        repository.categoryStreams(categoryId, myLanguage.takeIf { languageOnly.value }).toImmutableList()
    }

    private val content: StateFlow<Load<CategoryUiState>> = combine(loader.state, settings.settings.map { categoryId in it.followedCategories }) { load, favorite ->
        when (load) {
            is Load.Ready -> load.withData(CategoryUiState(load.data, favorite))
            Load.Loading -> Load.Loading
            Load.SignedOut -> Load.SignedOut
            Load.Failed -> Load.Failed
        }
    }.stateIn(scope, SharingStarted.Lazily, Load.Loading)

    val state: StateFlow<CategoryScreenState> = combine(content, languageOnly) { load, only -> CategoryScreenState(load, myLanguage, only) }
        .stateIn(scope, SharingStarted.Eagerly, CategoryScreenState(Load.Loading, myLanguage, true))

    fun onEvent(event: CategoryEvent) {
        when (event) {
            CategoryEvent.ToggleFavorite -> {
                scope.launch { settings.toggleCategory(categoryId) }
            }

            CategoryEvent.ToggleLanguage -> {
                languageOnly.value = !languageOnly.value
                loader.reload()
            }

            CategoryEvent.Retry -> loader.reload()
        }
    }
}
