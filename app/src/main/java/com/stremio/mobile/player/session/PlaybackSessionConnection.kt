package com.stremio.mobile.player.session

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Keeps the media session alive for the lifetime of the player screen and points it at [entry].
 * Connecting a controller starts [PlaybackSessionService]; a null entry (no engine yet, Cast owns
 * playback) leaves the session idle so no stale notification is shown.
 */
@Composable
fun PlaybackSessionEffect(entry: NowPlaying?) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync()
        onDispose { MediaController.releaseFuture(controller) }
    }
    DisposableEffect(entry) {
        if (entry != null) NowPlayingRegistry.publish(entry)
        onDispose { if (entry != null) NowPlayingRegistry.clear(entry) }
    }
}
