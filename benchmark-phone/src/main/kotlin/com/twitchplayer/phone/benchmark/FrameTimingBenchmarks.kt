package com.twitchplayer.phone.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing (frameDurationCpuMs / frameOverrunMs percentiles) per journey: Home scroll, chat scroll in the live player, the
 * fullscreen toggle and the sheet drag (minimise to the strip and back). Run with `benchmark-phone/tools/run-benchmarks.sh <label>`; each journey runs with no AOT compilation
 * and with the Baseline Profile.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun home(mode: CompilationMode, journey: (MacrobenchmarkScope) -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            HomeScenarios.launch(this)
        },
        measureBlock = { journey(this) },
    )

    /** The live player is opened in setup (kept open between iterations: the first one starts from Home), so only the journey is measured. */
    private fun player(mode: CompilationMode, journey: (MacrobenchmarkScope) -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        // No pressHome(): a playing live page would enter Picture-in-Picture (as it should) and leave a window behind.
        setupBlock = { PlayerScenarios.ensureLivePlayer(this) },
        measureBlock = { journey(this) },
    )

    private val profile = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test fun homeScrollNoProfile() = home(CompilationMode.None(), HomeScenarios::homeScroll)

    @Test fun homeScrollBaselineProfile() = home(profile, HomeScenarios::homeScroll)

    @Test fun chipSwitchNoProfile() = home(CompilationMode.None(), HomeScenarios::chipSwitch)

    @Test fun chipSwitchBaselineProfile() = home(profile, HomeScenarios::chipSwitch)

    @Test fun discoverSwipeNoProfile() = home(CompilationMode.None(), HomeScenarios::discoverSwipe)

    @Test fun discoverSwipeBaselineProfile() = home(profile, HomeScenarios::discoverSwipe)

    @Test fun chatScrollNoProfile() = player(CompilationMode.None(), PlayerScenarios::chatScroll)

    @Test fun chatScrollBaselineProfile() = player(profile, PlayerScenarios::chatScroll)

    @Test fun fullscreenToggleNoProfile() = player(CompilationMode.None(), PlayerScenarios::fullscreenToggle)

    @Test fun fullscreenToggleBaselineProfile() = player(profile, PlayerScenarios::fullscreenToggle)

    @Test fun sheetDragNoProfile() = player(CompilationMode.None(), PlayerScenarios::sheetDrag)

    @Test fun sheetDragBaselineProfile() = player(profile, PlayerScenarios::sheetDrag)

    private companion object {
        const val ITERATIONS = 5
    }
}
