package com.twitchplayer.tv.testing

import com.twitchplayer.tv.core.data.model.Account
import com.twitchplayer.tv.core.data.model.AppSettings
import com.twitchplayer.tv.core.data.model.Category
import com.twitchplayer.tv.core.data.model.CategoryGroup
import com.twitchplayer.tv.core.data.model.ChannelDetails
import com.twitchplayer.tv.core.data.model.ChannelRef
import com.twitchplayer.tv.core.data.model.ChatMode
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.Follows
import com.twitchplayer.tv.core.data.model.LiveStream
import com.twitchplayer.tv.core.data.model.PlayerEngine
import com.twitchplayer.tv.core.data.model.SearchResults
import com.twitchplayer.tv.core.data.repository.SessionRepository
import com.twitchplayer.tv.core.data.repository.SessionState
import com.twitchplayer.tv.core.data.repository.SettingsRepository
import com.twitchplayer.tv.core.data.repository.TwitchRepository
import com.twitchplayer.tv.core.network.auth.DeviceCodeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

fun stream(id: String, viewers: Int = 100, category: String = "cat") = LiveStream(id, id, id.replaceFirstChar(Char::uppercase), "title $id", category, category, viewers, "", null)

fun category(id: String, group: CategoryGroup = CategoryGroup.Games) = Category(id, id.replaceFirstChar(Char::uppercase), "", group)

class FakeSession(initial: SessionState = SessionState.SignedIn(Account("me", "camille"))) : SessionRepository {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutable
    override fun current(): String? = "token"
    override suspend fun refresh(rejected: String?): String? = "token"
    override fun start() = Unit
    override suspend fun signIn(onCode: (DeviceCodeResponse) -> Unit): Boolean = false
    override suspend fun signOut() {
        mutable.value = SessionState.SignedOut
    }
    fun set(state: SessionState) {
        mutable.value = state
    }
}

private const val MAX_RECENTS = 4

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val mutable = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = mutable
    override suspend fun setChatMode(mode: ChatMode) = mutable.update { it.copy(chatMode = mode) }
    override suspend fun updateChat(transform: (ChatSettings) -> ChatSettings) = mutable.update { it.copy(chat = transform(it.chat)) }
    override suspend fun setAnimations(enabled: Boolean) = mutable.update { it.copy(animationsEnabled = enabled) }
    override suspend fun setPlayerEngine(engine: PlayerEngine) = mutable.update { it.copy(playerEngine = engine) }
    override suspend fun setPreferredQuality(id: String?) = mutable.update { it.copy(preferredQuality = id) }
    override suspend fun setShowStats(enabled: Boolean) = mutable.update { it.copy(showStats = enabled) }
    override suspend fun setFollowRaids(enabled: Boolean) = mutable.update { it.copy(followRaids = enabled) }
    override suspend fun addRecentSearch(query: String) = mutable.update { it.copy(recentSearches = (listOf(query) + it.recentSearches.filter { r -> r != query }).take(MAX_RECENTS)) }
    override suspend fun removeRecentSearch(query: String) = mutable.update { it.copy(recentSearches = it.recentSearches.filter { r -> r != query }) }
    override suspend fun clearRecentSearches() = mutable.update { it.copy(recentSearches = emptyList()) }
    override suspend fun toggleCategory(id: String) = mutable.update { it.copy(followedCategories = if (id in it.followedCategories) it.followedCategories - id else it.followedCategories + id) }
}

class FakeRepository(
    var live: List<LiveStream> = emptyList(),
    var offline: List<ChannelRef> = emptyList(),
    var categories: List<Category> = emptyList(),
    var searchResult: SearchResults = SearchResults(emptyList(), emptyList()),
) : TwitchRepository {
    var failNext = false
    val searchCalls = mutableListOf<String>()
    private fun check() {
        if (failNext) {
            failNext = false
            error("boom")
        }
    }
    override suspend fun follows(force: Boolean): Follows {
        check()
        return Follows(live, offline)
    }
    override suspend fun recommended(): List<LiveStream> {
        check()
        return live
    }
    override suspend fun topCategories(): List<Category> {
        check()
        return categories
    }
    override suspend fun categoryStreams(categoryId: String, language: String?): List<LiveStream> {
        check()
        return live.filter { it.categoryId == categoryId }
    }
    override suspend fun search(query: String): SearchResults {
        searchCalls += query
        check()
        return searchResult
    }
    override suspend fun channel(login: String): ChannelDetails = error("unused")
    override suspend fun liveStream(login: String): LiveStream? = live.firstOrNull { it.login == login }
}
