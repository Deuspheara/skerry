package com.twitchplayer.phone.feature.auth

/** How a device-code sign-in ended, once the code disappeared from the screen. */
enum class SignInOutcome { Success, Cancelled, Failed }

/**
 * The presenter clears the code whenever sign-in ends, whatever the reason: the outcome is told from what the viewer did and
 * what the session says afterwards. A session means success; otherwise, a cancel by the viewer is not an error, anything else
 * (the code expired, the grant was denied, the network failed) is.
 */
fun signInOutcome(cancelledByViewer: Boolean, signedInAfterwards: Boolean): SignInOutcome = when {
    signedInAfterwards -> SignInOutcome.Success
    cancelledByViewer -> SignInOutcome.Cancelled
    else -> SignInOutcome.Failed
}

/** "m:ss" for the code's remaining life; never negative. */
fun countdownText(remainingSeconds: Int): String {
    val total = remainingSeconds.coerceAtLeast(0)
    val seconds = total % SECONDS_PER_MINUTE
    return "${total / SECONDS_PER_MINUTE}:${seconds.toString().padStart(2, '0')}"
}

private const val SECONDS_PER_MINUTE = 60
