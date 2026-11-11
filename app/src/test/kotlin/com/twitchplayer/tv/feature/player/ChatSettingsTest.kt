package com.twitchplayer.tv.feature.player

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twitchplayer.tv.core.data.model.AnimatedEmoteLimit
import com.twitchplayer.tv.core.data.model.ChatDensity
import com.twitchplayer.tv.core.data.model.ChatSettings
import com.twitchplayer.tv.core.data.model.ChatTextSize
import com.twitchplayer.tv.core.data.repository.DataStoreSettingsRepository
import com.twitchplayer.tv.feature.player.presentation.ChatFeed
import com.twitchplayer.tv.feature.player.presentation.ChatLine
import com.twitchplayer.tv.feature.player.presentation.ChatLineKind
import com.twitchplayer.tv.feature.player.presentation.ChatPiece
import com.twitchplayer.tv.feature.player.data.emotes.Emote
import com.twitchplayer.tv.feature.player.ui.animationAllowance
import com.twitchplayer.tv.feature.player.ui.step
import com.twitchplayer.tv.feature.player.ui.toStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file get() = java.io.File(folder.root, "settings.preferences_pb")
    private fun store() = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    @After fun tearDown() = scope.cancel()

    @Test fun defaultsMatchTheSpec() {
        val s = ChatSettings()
        assertEquals(ChatTextSize.Medium, s.textSize); assertEquals(ChatDensity.Standard, s.density)
        assertTrue(s.showBadges && s.animatedEmotes && !s.showTimestamps)
        assertEquals(AnimatedEmoteLimit.Some, s.animatedLimit)
    }

    @Test fun everyChatSettingSurvivesRestart() = runBlocking {
        val changed = ChatSettings(ChatTextSize.Large, ChatDensity.Relaxed, 60, showBadges = false, showTimestamps = true, animatedEmotes = false, animatedLimit = AnimatedEmoteLimit.Many)
        DataStoreSettingsRepository(store()).updateChat { changed }
        // "Restart": a new store over a copy of what is on disk (a second live store on the same file is refused by DataStore).
        val copy = java.io.File(folder.root, "copy.preferences_pb").also { file.copyTo(it) }
        val reopened = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { copy }))
        assertEquals(changed, reopened.settings.first().chat)
    }

    @Test fun lowMemoryTvsStartWithFewerAnimatedEmotesButTheViewerStillChooses() = runBlocking {
        val repo = DataStoreSettingsRepository(store(), ChatSettings(animatedLimit = AnimatedEmoteLimit.Few))
        assertEquals(AnimatedEmoteLimit.Few, repo.settings.first().chat.animatedLimit)
        repo.updateChat { it.copy(animatedLimit = AnimatedEmoteLimit.Many) }
        assertEquals(AnimatedEmoteLimit.Many, repo.settings.first().chat.animatedLimit)
    }

    @Test fun updatesAreAtomicTransformsOfTheCurrentValue() = runBlocking {
        val repo = DataStoreSettingsRepository(store())
        repo.updateChat { it.copy(textSize = ChatTextSize.Small) }
        repo.updateChat { it.copy(showTimestamps = true) }
        val chat = repo.settings.first().chat
        assertEquals(ChatTextSize.Small, chat.textSize); assertTrue(chat.showTimestamps)
    }

    @Test fun unknownOrOutOfRangeStoredValuesFallBackToDefaults() = runBlocking {
        val store = store()
        store.edit { it[stringPreferencesKey("chat_text_size")] = "Huge"; it[androidx.datastore.preferences.core.intPreferencesKey("chat_opacity")] = 3 }
        val chat = DataStoreSettingsRepository(store).settings.first().chat
        assertEquals(ChatTextSize.Medium, chat.textSize)
        assertEquals("clamped to the lowest step", 40, chat.backgroundOpacity)
    }

    @Test fun stylesStayInsideTheTvSizesOfTheSpec() {
        ChatTextSize.entries.forEach { size ->
            val s = ChatSettings(textSize = size).toStyle()
            assertTrue("text ${s.textSp}", s.textSp in 14f..17f)
            assertTrue("line ${s.lineHeightSp}", s.lineHeightSp in 18f..22f)
            assertTrue("emote ${s.emoteDp}", s.emoteDp in 24f..32f)
            assertTrue("badge ${s.badgeDp}", s.badgeDp in 14f..16f)
        }
        assertEquals(ChatDensity.entries.map { it.gapDp + 2 }.toList(), listOf(3, 5, 8))
    }

    @Test fun optionStepsWrapAround() {
        assertEquals(ChatTextSize.Small, step(ChatTextSize.entries, ChatTextSize.Large, 1))
        assertEquals(ChatTextSize.Large, step(ChatTextSize.entries, ChatTextSize.Small, -1))
        assertEquals(100, step(ChatSettings.BACKGROUND_OPACITIES, 40, -1))
    }

    private fun line(id: String, animated: Int): ChatLine {
        val emote = Emote("e", "s", "a")
        return ChatLine(id, ChatLineKind.Normal, "n", 0, emptyList(), listOf(ChatPiece.Emote(List(animated) { emote })), "00:00")
    }

    @Test fun animationBudgetGoesToTheNewestVisibleLinesFirst() {
        val visibleNewestFirst = listOf(line("new", 2), line("mid", 3), line("old", 4), line("none", 0))
        assertEquals(mapOf("new" to 2, "mid" to 3, "old" to 1), animationAllowance(visibleNewestFirst, limit = 6))
        assertEquals(emptyMap<String, Int>(), animationAllowance(visibleNewestFirst, limit = 0))
        assertEquals("lines outside the list given (scrolled away) never hold budget", mapOf("new" to 1), animationAllowance(visibleNewestFirst.take(1), limit = 1))
    }

    @Test fun theBufferLimitIsWithinTheArchitectureRange() {
        assertTrue(ChatFeed.MAX_LINES in 100..200)
    }
}
