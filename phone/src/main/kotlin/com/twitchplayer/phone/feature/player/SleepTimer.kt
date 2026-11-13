package com.twitchplayer.phone.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twitchplayer.phone.R
import com.twitchplayer.phone.ui.theme.PhoneColors
import kotlinx.coroutines.delay

/** What the sleep timer offers and how its countdown reads; plain functions so a JVM test pins them. */
object SleepTimerPolicy {
    /** The choices, in minutes. */
    val PRESETS_MINUTES = listOf(15, 30, 45, 60, 90)

    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60

    fun endAt(nowMs: Long, minutes: Int): Long = nowMs + minutes * MS_PER_MINUTE

    /** Time left, never negative; 0 when no timer runs ([endAtMs] <= 0). */
    fun remainingMs(endAtMs: Long, nowMs: Long): Long = if (endAtMs <= 0L) 0L else (endAtMs - nowMs).coerceAtLeast(0L)

    /** "m:ss" under an hour, "h:mm:ss" from one; the last partial second counts as a whole one so the display never shows 0:00 before it stops. */
    fun format(remainingMs: Long): String {
        val total = ((remainingMs + MS_PER_SECOND - 1) / MS_PER_SECOND).coerceAtLeast(0L)
        val seconds = (total % SECONDS_PER_MINUTE).toInt()
        val minutes = (total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR).toInt()
        val hours = (total / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)).toInt()
        val ss = seconds.toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
    }

    /** How long to wait before the display needs to change: until the next whole second, at most one second. */
    fun nextTickMs(remainingMs: Long): Long = (remainingMs % MS_PER_SECOND).let { if (it == 0L) MS_PER_SECOND else it }.coerceAtMost(MS_PER_SECOND)
}

/** A running (or idle) sleep timer: the moment it ends, kept across rotation and process death. */
@Stable
class SleepTimerState internal constructor(initialEndAtMs: Long) {
    var endAtMs by mutableLongStateOf(initialEndAtMs)
        private set

    /** Time left, refreshed about every second by [SleepTimerEffect]. */
    var remainingMs by mutableLongStateOf(0L)
        internal set

    val active: Boolean get() = endAtMs > 0L

    fun start(minutes: Int, nowMs: Long = System.currentTimeMillis()) {
        endAtMs = SleepTimerPolicy.endAt(nowMs, minutes)
        remainingMs = SleepTimerPolicy.remainingMs(endAtMs, nowMs)
    }

    fun cancel() {
        endAtMs = 0L
        remainingMs = 0L
    }
}

@Composable
fun rememberSleepTimer(): SleepTimerState {
    var saved by rememberSaveable { mutableLongStateOf(0L) }
    val state = remember { SleepTimerState(saved) }
    // Keep the saved copy in step so a restored page continues the same countdown.
    SideEffect { saved = state.endAtMs }
    return state
}

/** Counts down while [state] is active and calls [onExpire] once when it reaches zero (the timer is then off). */
@Composable
fun SleepTimerEffect(state: SleepTimerState, onExpire: () -> Unit) {
    val expire by rememberUpdatedState(onExpire)
    LaunchedEffect(state.endAtMs) {
        if (!state.active) return@LaunchedEffect
        while (true) {
            val left = SleepTimerPolicy.remainingMs(state.endAtMs, System.currentTimeMillis())
            state.remainingMs = left
            if (left <= 0L) {
                state.cancel()
                expire()
                return@LaunchedEffect
            }
            delay(SleepTimerPolicy.nextTickMs(left))
        }
    }
}

/** The countdown over the picture while a timer runs; a tap opens the sheet to change or stop it. */
@Composable
fun SleepTimerChip(state: SleepTimerState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.active) return
    val text = SleepTimerPolicy.format(state.remainingMs)
    val description = stringResource(R.string.phone_player_sleep_timer_active, text)
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        androidx.compose.material3.Icon(com.twitchplayer.phone.ui.components.PhoneIcons.Timer, null, tint = PhoneColors.OnScrim, modifier = Modifier.padding(end = 4.dp))
        Text(text, color = PhoneColors.OnScrim, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(state: SleepTimerState, onDismiss: () -> Unit) {
    // Opens fully (every preset and "turn off" in view, no half-open sheet with the list cut at the bottom) and scrolls if the screen is small.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            val title = stringResource(R.string.phone_player_sleep_timer_title)
            // While a timer runs, its countdown is in the title.
            Text(if (state.active) "$title · ${SleepTimerPolicy.format(state.remainingMs)}" else title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SleepTimerPolicy.PRESETS_MINUTES.forEach { minutes ->
                val label = when (minutes) {
                    60 -> stringResource(R.string.phone_player_sleep_timer_hour)
                    90 -> stringResource(R.string.phone_player_sleep_timer_hour_half)
                    else -> pluralStringResource(R.plurals.phone_player_sleep_timer_minutes, minutes, minutes)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = false, role = Role.Button, onClick = {
                        state.start(minutes)
                        onDismiss()
                    }).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
            }
            if (state.active) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = false, role = Role.Button, onClick = {
                        state.cancel()
                        onDismiss()
                    }).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(stringResource(R.string.phone_player_sleep_timer_cancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
