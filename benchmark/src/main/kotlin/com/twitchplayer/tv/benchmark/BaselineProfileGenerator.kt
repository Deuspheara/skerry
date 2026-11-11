package com.twitchplayer.tv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the profiles AGP reads from app/src/main/baselineProfiles/ (run `benchmark/tools/generate-profiles.sh`: it
 * runs this on the emulator only and copies the files there).
 * - [startup]: launch to Home content only. Becomes the startup profile: R8 lays those classes out first in
 *   classes.dex, so it must stay limited to what startup runs.
 * - [journeys]: every main journey after startup. Merged with the startup rules into the Baseline Profile (AOT).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        TvScenarios.launch(this)
    }

    // Each pass is long (every screen plus the native player journey): a few passes are enough to see the profile settle.
    @Test fun journeys() = rule.collect(packageName = TARGET_PACKAGE, maxIterations = 5, stableIterations = 2) {
        pressHome()
        TvScenarios.launch(this)
        TvScenarios.homeNavigation(this)
        TvScenarios.follows(this)
        TvScenarios.searchKeyboard(this)
        TvScenarios.browseGrid(this)
        pressBack(); device.waitForIdle() // top-level Back returns to Home
        TvScenarios.playerChatAndZapper(this)
        // Native player: open a stream, play, expand / collapse the chat, switch channel, leave.
        TvScenarios.openStreamAndPlay(this)
        TvScenarios.chatExpandCollapse(this)
        TvScenarios.switchChannel(this)
        TvScenarios.leavePlayer(this)
    }
}
