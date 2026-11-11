package com.twitchplayer.phone.feature.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.twitchplayer.phone.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The real [PlayerHost]: owned by `MainActivity`, which forwards `onUserLeaveHint` and `onPictureInPictureModeChanged` to it. */
class ActivityPlayerHost(private val activity: ComponentActivity) : PlayerHost {
    private val mutableInPip = MutableStateFlow(false)
    override val inPictureInPicture: StateFlow<Boolean> = mutableInPip.asStateFlow()

    override val canPictureInPicture: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private var eligible = false
    private var playing = false
    private var width = 0
    private var height = 0
    private var sourceBounds: Rect? = null
    private var togglePlay: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TOGGLE_PLAY) togglePlay?.invoke()
        }
    }

    init {
        if (canPictureInPicture) {
            ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION_TOGGLE_PLAY), ContextCompat.RECEIVER_NOT_EXPORTED)
            activity.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        activity.unregisterReceiver(receiver)
                    }
                },
            )
        }
    }

    override fun updatePictureInPicture(eligible: Boolean, playing: Boolean, videoWidth: Int, videoHeight: Int, sourceBounds: Rect?, onTogglePlay: () -> Unit) {
        this.eligible = eligible
        this.playing = playing
        width = videoWidth
        height = videoHeight
        this.sourceBounds = sourceBounds
        togglePlay = onTogglePlay
        applyParams()
    }

    override fun clearPictureInPicture() {
        eligible = false
        togglePlay = null
        applyParams()
    }

    override fun enterPictureInPicture() = enterNow()

    /** `Activity.onUserLeaveHint`: before Android 12 the window is entered here (12+ does it by itself, see [params]). */
    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterNow()
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        mutableInPip.value = inPip
    }

    private fun enterNow() {
        if (canPictureInPicture && eligible) enterPictureInPictureModeApi26()
    }

    private fun applyParams() {
        if (canPictureInPicture) setParamsApi26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPictureInPictureModeApi26() {
        activity.enterPictureInPictureMode(params())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setParamsApi26() {
        activity.setPictureInPictureParams(params())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun params(): PictureInPictureParams {
        val (num, den) = PipPolicy.aspect(width, height)
        val label = activity.getString(if (playing) R.string.player_pause else R.string.player_play)
        val icon = Icon.createWithResource(activity, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val intent = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(ACTION_TOGGLE_PLAY).setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(num, den))
            .setActions(listOf(RemoteAction(icon, label, label, intent)))
        sourceBounds?.let { builder.setSourceRectHint(it) }
        // Android 12+: leaving the app enters the window by itself while a picture is playing, with no flash of the full screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(eligible)
        return builder.build()
    }

    override fun lockOrientation(lock: OrientationLock) {
        activity.requestedOrientation = when (lock) {
            OrientationLock.None -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationLock.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    override fun setImmersive(enabled: Boolean) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (enabled) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    private companion object {
        const val ACTION_TOGGLE_PLAY = "com.twitchplayer.phone.PIP_TOGGLE_PLAY"
    }
}
