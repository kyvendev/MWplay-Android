package com.stremio.mobile.player.session

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.stremio.mobile.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the MediaSession that mirrors the active local playback: media notification, lock screen,
 * headset/Bluetooth buttons, PiP actions and other controllers. It owns no decoder; [SessionPlayer]
 * forwards everything to the engine PlayerScreen publishes in [NowPlayingRegistry].
 */
@OptIn(UnstableApi::class)
class PlaybackSessionService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionPlayer: SessionPlayer
    private lateinit var audioOutput: AudioOutputMonitor
    private var session: MediaSession? = null
    private var noisyRegistered = false

    // Unplugging headphones or dropping Bluetooth must pause instead of switching to the speaker.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && sessionPlayer.wantsToPlay) {
                sessionPlayer.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionPlayer = SessionPlayer(Looper.getMainLooper(), scope)
        audioOutput = AudioOutputMonitor(this).also { it.start() }
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, sessionPlayer)
            .setId("mw-play-playback")
            .setSessionActivity(openPlayer)
            .build()
        scope.launch {
            NowPlayingRegistry.current.collect { entry ->
                sessionPlayer.bind(entry)
                updateNoisyReceiver(entry != null)
            }
        }
        scope.launch {
            audioOutput.output.collect { output ->
                // Exposed to controllers now; ready for a future output indicator in the player.
                session?.setSessionExtras(Bundle().apply { putString(AudioOutputExtra, output.name) })
            }
        }
    }

    private fun updateNoisyReceiver(active: Boolean) {
        if (active == noisyRegistered) return
        noisyRegistered = active
        if (active) {
            ContextCompat.registerReceiver(
                this,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            unregisterReceiver(noisyReceiver)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away ends playback instead of leaving an orphan notification.
        if (!sessionPlayer.wantsToPlay) stopSelf()
    }

    override fun onDestroy() {
        updateNoisyReceiver(false)
        audioOutput.stop()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val AudioOutputExtra = "com.stremio.mobile.AUDIO_OUTPUT"
    }
}
