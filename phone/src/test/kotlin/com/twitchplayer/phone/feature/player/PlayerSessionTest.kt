package com.twitchplayer.phone.feature.player

import androidx.compose.runtime.saveable.SaverScope
import com.twitchplayer.phone.navigation.PlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionTest {
    private val live = PlaybackTarget.Live("shroud")
    private val other = PlaybackTarget.Live("pokimane")
    private val open = PlayerSessionState.Open(live)

    private fun reduce(state: PlayerSessionState, vararg events: PlayerSessionEvent) = events.fold(state) { s, e -> PlayerSessionReducer.reduce(s, e) }

    @Test fun aTapOpensTheSheetExpanded() {
        assertEquals(open, reduce(PlayerSessionState.Closed, PlayerSessionEvent.Open(live)))
    }

    @Test fun theSameTargetAgainExpandsTheStrip() {
        val collapsed = reduce(open, PlayerSessionEvent.Minimise)
        assertEquals(SheetState.Collapsed, (collapsed as PlayerSessionState.Open).sheet)
        assertEquals(open, reduce(collapsed, PlayerSessionEvent.Open(live)))
    }

    @Test fun anotherTargetReplacesTheSessionAndStartsExpanded() {
        val collapsed = reduce(open, PlayerSessionEvent.Minimise)
        assertEquals(PlayerSessionState.Open(other), reduce(collapsed, PlayerSessionEvent.Open(other)))
    }

    @Test fun minimiseThenExpandComesBack() {
        assertEquals(open, reduce(open, PlayerSessionEvent.Minimise, PlayerSessionEvent.Expand))
    }

    @Test fun closeEndsTheSessionFromAnyState() {
        assertEquals(PlayerSessionState.Closed, reduce(open, PlayerSessionEvent.Close))
        assertEquals(PlayerSessionState.Closed, reduce(open, PlayerSessionEvent.Minimise, PlayerSessionEvent.Close))
        assertEquals(PlayerSessionState.Closed, reduce(PlayerSessionState.Closed, PlayerSessionEvent.Close))
    }

    @Test fun eventsOnAClosedSessionChangeNothing() {
        listOf(
            PlayerSessionEvent.Minimise,
            PlayerSessionEvent.Expand,
            PlayerSessionEvent.FullscreenChanged(true),
            PlayerSessionEvent.PipChanged(true),
        ).forEach { assertEquals(PlayerSessionState.Closed, reduce(PlayerSessionState.Closed, it)) }
    }

    @Test fun fullscreenKeepsTheSheetExpandedAndCannotBeMinimised() {
        val collapsed = reduce(open, PlayerSessionEvent.Minimise)
        val fullscreen = reduce(collapsed, PlayerSessionEvent.FullscreenChanged(true)) as PlayerSessionState.Open
        assertEquals(SheetState.Expanded, fullscreen.sheet)
        assertTrue(fullscreen.fullscreen)
        assertEquals(fullscreen, reduce(fullscreen, PlayerSessionEvent.Minimise))
    }

    @Test fun leavingFullscreenKeepsTheSheetWhereItWas() {
        val back = reduce(open, PlayerSessionEvent.FullscreenChanged(true), PlayerSessionEvent.FullscreenChanged(false))
        assertEquals(open, back)
    }

    @Test fun theWindowCannotBeMinimisedAndItsEndBringsTheSheetBack() {
        val inWindow = reduce(open, PlayerSessionEvent.PipChanged(true))
        assertEquals(inWindow, reduce(inWindow, PlayerSessionEvent.Minimise))
        assertEquals(open, reduce(inWindow, PlayerSessionEvent.PipChanged(false)))
    }

    @Test fun aCollapsedSheetThatEntersTheWindowKeepsItsRestingPlaceForAfterwards() {
        val collapsed = reduce(open, PlayerSessionEvent.Minimise) as PlayerSessionState.Open
        val inWindow = reduce(collapsed, PlayerSessionEvent.PipChanged(true)) as PlayerSessionState.Open
        assertEquals(false, inWindow.showsStrip)
        assertEquals(true, (reduce(inWindow, PlayerSessionEvent.PipChanged(false)) as PlayerSessionState.Open).showsStrip)
    }

    @Test fun backLeavesFullscreenThenMinimisesThenHandsOverButNeverCloses() {
        var state: PlayerSessionState = reduce(open, PlayerSessionEvent.FullscreenChanged(true))
        assertEquals(BackAction.ExitFullscreen, BackPolicy.action(state))
        state = reduce(state, PlayerSessionEvent.FullscreenChanged(false))
        assertEquals(BackAction.Minimise, BackPolicy.action(state))
        state = reduce(state, PlayerSessionEvent.Minimise)
        assertEquals(BackAction.PassThrough, BackPolicy.action(state))
        assertTrue(state is PlayerSessionState.Open)
    }

    @Test fun backIsNotThePlayersWhenNothingPlaysOrInTheWindow() {
        assertEquals(BackAction.PassThrough, BackPolicy.action(PlayerSessionState.Closed))
        assertEquals(BackAction.PassThrough, BackPolicy.action(open.copy(inPip = true)))
    }

    @Test fun everyTargetKindSurvivesTheSavedState() {
        val targets = listOf(
            live,
            PlaybackTarget.Clip("FunnySlug", "A clip"),
            PlaybackTarget.Replay("123", "A replay", 3600, "shroud", "Shroud", "https://x/a.png", 42, 1_700_000_000_000L),
            PlaybackTarget.Replay("124", "No avatar", 60),
        )
        val scope = SaverScope { true }
        for (target in targets) {
            for (sheet in SheetState.entries) {
                val controller = PlayerSessionController(PlayerSessionState.Open(target, sheet, fullscreen = true, inPip = true))
                val saved = with(PlayerSessionController.Saver) { scope.save(controller) }!!
                val restored = PlayerSessionController.Saver.restore(saved)!!
                // Fullscreen and the window are the system's to decide again.
                assertEquals(PlayerSessionState.Open(target, sheet), restored.state)
            }
        }
    }

    @Test fun aClosedSessionIsSavedAndRestoredClosed() {
        val scope = SaverScope { true }
        val saved = with(PlayerSessionController.Saver) { scope.save(PlayerSessionController()) }!!
        assertEquals(PlayerSessionState.Closed, PlayerSessionController.Saver.restore(saved)!!.state)
    }

    @Test fun garbageInTheSavedStateRestoresClosedInsteadOfCrashing() {
        listOf(listOf("Expanded", "live"), listOf("Nope", "live", "x"), listOf("Expanded", "unknown", "x"), listOf("Expanded", "replay", 1)).forEach {
            assertEquals(PlayerSessionState.Closed, PlayerSessionController.Saver.restore(it)!!.state)
        }
    }

    @Test fun aSessionBroughtBackAfterTheProcessWasKilledDoesNotPlayByItself() {
        val scope = SaverScope { true }
        val saved = with(PlayerSessionController.Saver) { scope.save(PlayerSessionController(PlayerSessionState.Open(PlaybackTarget.Clip("a", "t")))) }!!
        val restored = PlayerSessionController.Saver.restore(saved)!!

        assertEquals(false, restored.autoplay)
        assertEquals(true, PlayerSessionController().autoplay)
    }

    @Test fun whateverTheViewerOpensNextPlaysAsUsual() {
        val scope = SaverScope { true }
        val saved = with(PlayerSessionController.Saver) { scope.save(PlayerSessionController(PlayerSessionState.Open(PlaybackTarget.Clip("a", "t")))) }!!
        val restored = PlayerSessionController.Saver.restore(saved)!!

        restored.open(PlaybackTarget.Clip("a", "t"))
        assertEquals("the same clip tapped again is the same page, it keeps what it was told", false, restored.autoplay)
        restored.open(PlaybackTarget.Live("shroud"))
        assertEquals(true, restored.autoplay)
    }

    @Test fun noBackPressEverClosesTheSession() {
        val states = listOf(open, open.copy(fullscreen = true), open.copy(sheet = SheetState.Collapsed), open.copy(inPip = true))
        for (state in states) {
            val after = when (BackPolicy.action(state)) {
                BackAction.Minimise -> PlayerSessionReducer.reduce(state, PlayerSessionEvent.Minimise)
                BackAction.ExitFullscreen -> PlayerSessionReducer.reduce(state, PlayerSessionEvent.FullscreenChanged(false))
                BackAction.PassThrough -> state
            }
            assertEquals("$state", true, after is PlayerSessionState.Open)
        }
    }
}
