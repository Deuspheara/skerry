package com.twitchplayer.phone.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What to do after a tap on the microphone button, or after the permission dialog answered. */
enum class MicStep { StartVoice, AskPermission, Nothing }

/**
 * The microphone permission path of voice search, apart from Android so it can be tested: a tap starts listening when the permission
 * is held and asks for it otherwise; a refusal is remembered (the screen explains it) and does not start anything; the next tap
 * forgets the refusal and tries again.
 */
class MicPermissionFlow {
    /** True after the viewer refused the permission, until the next tap. */
    var denied: Boolean by mutableStateOf(false)
        private set

    fun onMicTapped(permissionGranted: Boolean): MicStep {
        denied = false
        return if (permissionGranted) MicStep.StartVoice else MicStep.AskPermission
    }

    fun onPermissionResult(granted: Boolean): MicStep {
        denied = !granted
        return if (granted) MicStep.StartVoice else MicStep.Nothing
    }
}
