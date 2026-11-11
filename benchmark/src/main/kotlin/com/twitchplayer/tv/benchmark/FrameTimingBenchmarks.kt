package com.twitchplayer.tv.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing (frameDurationCpuMs / frameOverrunMs percentiles) per journey. Run with
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest`; compare [CompilationMode.None] with the baseline profile.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun run(mode: CompilationMode, journey: (androidx.benchmark.macro.MacrobenchmarkScope) -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); TvScenarios.launch(this) },
        measureBlock = { journey(this) },
    )

    /** Frame timing from the moment playback runs: the stream is opened in setup, so only the journey is measured. */
    private fun runPlayer(mode: CompilationMode, journey: (androidx.benchmark.macro.MacrobenchmarkScope) -> Unit) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), FrameTimingGfxInfoMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); TvScenarios.launch(this); TvScenarios.openStreamAndPlay(this) },
        measureBlock = { journey(this) },
    )

    @Test fun homeNavigationNoProfile() = run(CompilationMode.None(), TvScenarios::homeNavigation)
    @Test fun homeNavigationBaselineProfile() = run(CompilationMode.Partial(BaselineProfileMode.Require), TvScenarios::homeNavigation)
    @Test fun browseGridBaselineProfile() = run(CompilationMode.Partial(BaselineProfileMode.Require), TvScenarios::browseGrid)
    // Native player over the demo clip: the chat expand / collapse (the jank scenario) and a channel switch.
    @Test fun chatExpandCollapseBaselineProfile() = runPlayer(CompilationMode.Partial(BaselineProfileMode.Require)) { TvScenarios.chatExpandCollapse(it, cycles = 3) }
    @Test fun chatExpandCollapseNoProfile() = runPlayer(CompilationMode.None()) { TvScenarios.chatExpandCollapse(it, cycles = 3) }
    @Test fun switchChannelBaselineProfile() = runPlayer(CompilationMode.Partial(BaselineProfileMode.Require), TvScenarios::switchChannel)

    @Test fun playerChatAndZapperBaselineProfile() = run(CompilationMode.Partial(BaselineProfileMode.Require), TvScenarios::playerChatAndZapper)
}
