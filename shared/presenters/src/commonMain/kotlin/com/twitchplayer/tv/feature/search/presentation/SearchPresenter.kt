package com.twitchplayer.tv.feature.search.presentation

import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Voice capture seam so the presenter stays testable. Returns the best transcript, or null when nothing was understood. */
interface VoiceInput {
    fun isAvailable(): Boolean
    suspend fun listen(): String?
}

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class Heard(val text: String) : VoiceState
    data object Failed : VoiceState
}

/** What the right-hand column shows. [Trends] fills the empty and no-result states. */
sealed interface ResultsUi {
    data class Trends(val channels: ImmutableList<ChannelRef>, val noResult: Boolean = false) : ResultsUi

    data class Found(val channels: ImmutableList<ChannelRef>, val categories: ImmutableList<Category>) : ResultsUi
}

data class SearchUiState(
    val query: String = "",
    val recents: ImmutableList<String> = persistentListOf(),
    val results: ResultsUi = ResultsUi.Trends(persistentListOf()),
    val voice: VoiceState = VoiceState.Idle,
)

sealed interface SearchEvent {
    data class Append(val text: String) : SearchEvent
    data object Space : SearchEvent
    data object Delete : SearchEvent
    data object Clear : SearchEvent
    data class SetQuery(val value: String) : SearchEvent

    /** Sent when a result is opened so the query becomes a recent search. */
    data object CommitQuery : SearchEvent
    data object StartVoice : SearchEvent
    data object CancelVoice : SearchEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchPresenter(
    private val repository: TwitchRepository,
    private val settings: SettingsRepository,
    private val voiceInput: VoiceInput,
    private val scope: CoroutineScope,
    debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private val query = MutableStateFlow("")
    private val voice = MutableStateFlow<VoiceState>(VoiceState.Idle)
    private var voiceJob: Job? = null

    private val trends = flow {
        emit(
            runCatching { repository.recommended().take(TRENDS).map { ChannelRef(it.channelId, it.login, it.name, it.avatarUrl, true, it.viewers) } }
                .getOrDefault(emptyList()).toImmutableList(),
        )
    }.stateIn(scope, SharingStarted.Lazily, persistentListOf())

    private val results = query.map { it.trim() }.distinctUntilChanged().debounce { if (it.isEmpty()) 0 else debounceMs }
        .flatMapLatest { q ->
            if (q.isEmpty()) {
                trends.map<_, ResultsUi> { ResultsUi.Trends(it) }
            } else {
                flow {
                    val found = runCatching { repository.search(q) }.getOrNull()
                    emit(
                        when {
                            found == null || (found.channels.isEmpty() && found.categories.isEmpty()) -> ResultsUi.Trends(trends.value, noResult = true)
                            else -> ResultsUi.Found(found.channels.toImmutableList(), found.categories.toImmutableList())
                        },
                    )
                }
            }
        }

    val state: StateFlow<SearchUiState> = combine(query, settings.settings.map { it.recentSearches }, results, voice) { q, recents, r, v ->
        SearchUiState(q, recents.toImmutableList(), r, v)
    }.stateIn(scope, SharingStarted.Lazily, SearchUiState())

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.Append -> query.update { (it + event.text).take(MAX_QUERY) }

            SearchEvent.Space -> query.update { if (it.isEmpty() || it.endsWith(' ')) it else "$it " }

            SearchEvent.Delete -> query.update { it.dropLast(1) }

            SearchEvent.Clear -> query.update { "" }

            is SearchEvent.SetQuery -> query.update { event.value.take(MAX_QUERY) }

            SearchEvent.CommitQuery -> commitQuery()

            SearchEvent.StartVoice -> startVoice()

            SearchEvent.CancelVoice -> {
                voiceJob?.cancel()
                voice.value = VoiceState.Idle
            }
        }
    }

    private fun commitQuery() {
        val q = query.value.trim()
        if (q.isNotEmpty()) scope.launch { settings.addRecentSearch(q) }
    }

    private fun startVoice() {
        if (voiceJob?.isActive == true) return
        voiceJob = scope.launch {
            if (!voiceInput.isAvailable()) {
                failVoice()
                return@launch
            }
            voice.value = VoiceState.Listening
            val heard = try {
                voiceInput.listen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (heard == null) {
                failVoice()
                return@launch
            }
            voice.value = VoiceState.Heard(heard)
            delay(HEARD_MS)
            query.value = heard.take(MAX_QUERY)
            settings.addRecentSearch(heard)
            voice.value = VoiceState.Idle
        }
    }

    private suspend fun failVoice() {
        voice.value = VoiceState.Failed
        delay(FAILED_MS)
        voice.value = VoiceState.Idle
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 300L
        const val TRENDS = 4
        const val MAX_QUERY = 60
        const val HEARD_MS = 1_000L
        const val FAILED_MS = 1_200L
    }
}
