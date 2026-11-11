package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the profiles AGP reads from phone/src/main/baselineProfiles/ (run `benchmark-phone/tools/generate-profiles.sh`: it runs this
 * on the emulator only and copies the files there).
 * - [startup]: launch to Home showing its streams. Becomes the startup profile: R8 lays those classes out first in classes.dex, so it
 *   must stay limited to what startup runs.
 * - [journeys]: every main journey after startup. Merged with the startup rules into the Baseline Profile (AOT).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        HomeScenarios.launch(this)
    }

    @Test fun journeys() = rule.collect(packageName = TARGET_PACKAGE, maxIterations = 5, stableIterations = 2) {
        pressHome()
        HomeScenarios.launch(this)
        HomeScenarios.homeScroll(this)
        HomeScenarios.homeTabs(this)
        HomeScenarios.browseAndCategory(this)
        HomeScenarios.search(this)
        HomeScenarios.channel(this)
        HomeScenarios.settings(this)
        PlayerScenarios.replay(this)
        // Live player: portrait with chat, scroll the chat, fullscreen both ways, leave.
        PlayerScenarios.openLivePlayer(this)
        PlayerScenarios.chatScroll(this)
        PlayerScenarios.fullscreenToggle(this)
        PlayerScenarios.leavePlayer(this)
    }
}
