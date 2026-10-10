package com.stremio.mobile.presentation.screens.player

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle

/** What the player asks the activity for: PiP availability, its shape and whether to auto-enter. */
data class PlayerPipRequest(val aspectWidth: Int, val aspectHeight: Int, val autoEnter: Boolean)

/** Implemented by the activity that hosts the player, which owns the platform PiP calls. */
interface PictureInPictureHost {
    fun updatePlayerPip(request: PlayerPipRequest?)
    fun enterPlayerPip(): Boolean
}

/** Android rejects PiP aspect ratios outside about 1:2.39..2.39:1, so extreme videos are clamped. */
internal fun pipAspect(videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
    if (videoWidth <= 0 || videoHeight <= 0) return 16 to 9
    val ratio = videoWidth.toFloat() / videoHeight
    return when {
        ratio > MaxPipRatio -> 239 to 100
        ratio < 1f / MaxPipRatio -> 100 to 239
        else -> videoWidth to videoHeight
    }
}

private const val MaxPipRatio = 2.39f

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
fun Activity.supportsPlayerPip(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

/**
 * Publishes PiP eligibility while a local video is on screen and withdraws it when the player
 * leaves, so the app never shrinks into PiP from catalog screens. Auto-enter only while playing:
 * a paused video simply goes to the background.
 */
@Composable
internal fun PlayerPictureInPictureEffect(
    activity: Activity?,
    enabled: Boolean,
    playing: Boolean,
    videoWidth: Int,
    videoHeight: Int,
) {
    val host = activity as? PictureInPictureHost ?: return
    LaunchedEffect(host, enabled, playing, videoWidth, videoHeight) {
        val (w, h) = pipAspect(videoWidth, videoHeight)
        host.updatePlayerPip(if (enabled) PlayerPipRequest(w, h, autoEnter = playing) else null)
    }
    DisposableEffect(host) {
        onDispose { host.updatePlayerPip(null) }
    }
}

/**
 * Whether the activity is currently shown as a PiP window. Closing the window stops the activity
 * first and then leaves PiP while it is no longer started; that order tells a dismissal apart from
 * expanding back to full screen (or the screen turning off while in PiP).
 */
@Composable
internal fun rememberIsInPictureInPicture(activity: Activity?, onDismissed: () -> Unit): Boolean {
    val componentActivity = activity as? ComponentActivity ?: return false
    var inPip by remember(componentActivity) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && componentActivity.isInPictureInPictureMode)
    }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    DisposableEffect(componentActivity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            if (!info.isInPictureInPictureMode && !componentActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                currentOnDismissed()
            }
        }
        componentActivity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}
