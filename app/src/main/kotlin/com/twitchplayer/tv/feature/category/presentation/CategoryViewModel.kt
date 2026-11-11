package com.twitchplayer.tv.feature.category.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.presentation.Load
import com.twitchplayer.tv.core.presentation.Loader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class CategoryUiState(val streams: ImmutableList<LiveStream>, val favorite: Boolean)

/** Twitch stream language code for [locale] (ISO 639-1, with Java's legacy codes and Norwegian mapped). */
fun twitchLanguage(locale: Locale): String = when (val l = locale.language) {
    "in" -> "id"
    "iw" -> "he"
    "nb", "nn" -> "no"
    "fil" -> "tl"
    "zh" -> if (locale.country == "HK") "zh-hk" else "zh"
    else -> l
}

class CategoryViewModel(
    private val categoryId: String,
    repository: TwitchRepository,
    session: SessionRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val myLanguage = twitchLanguage(Locale.getDefault())
    // Starts on the viewer's language: a category's top streams are otherwise mostly in other languages.
    private val languageOnly = MutableStateFlow(true)
    private val loader = Loader(viewModelScope, session) {
        repository.categoryStreams(categoryId, myLanguage.takeIf { languageOnly.value }).toImmutableList()
    }

    val state: StateFlow<Load<CategoryUiState>> = combine(loader.state, settings.settings.map { categoryId in it.followedCategories }) { load, favorite ->
        when (load) {
            is Load.Ready -> Load.Ready(CategoryUiState(load.data, favorite))
            Load.Loading -> Load.Loading
            Load.SignedOut -> Load.SignedOut
            Load.Failed -> Load.Failed
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, Load.Loading)

    fun toggleFavorite() { viewModelScope.launch { settings.toggleCategory(categoryId) } }
    fun retry() = loader.reload()

    /** The language shown in the header, and whether the list is narrowed to it (also while loading). */
    val language: StateFlow<Pair<String, Boolean>> = languageOnly.map { myLanguage to it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, myLanguage to true)

    fun toggleLanguage() {
        languageOnly.value = !languageOnly.value
        loader.reload()
    }
}
