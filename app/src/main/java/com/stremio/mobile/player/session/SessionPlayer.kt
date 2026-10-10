package com.stremio.mobile.player.session

import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player.Commands
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.stremio.mobile.player.Player
import com.stremio.mobile.player.PlayerRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the system media controls describe: the engine currently on screen plus its metadata. */
class NowPlaying(
    val player: Player,
    val title: String,
    val subtitle: String?,
    val artworkUri: String?,
    val isEpisode: Boolean,
    val onPausedChanged: (Boolean) -> Unit = {},
    val onSeekReported: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
)

/**
 * Single source for the session. PlayerScreen publishes the active engine (and republishes after an
 * EXO -> VLC -> MPV fallback swaps it) and clears it when playback closes or moves to Cast.
 */
object NowPlayingRegistry {
    private val mutableCurrent = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = mutableCurrent
    fun publish(entry: NowPlaying) { mutableCurrent.value = entry }
    fun clear(entry: NowPlaying) { mutableCurrent.compareAndSet(entry, null) }
}

private const val SeekIncrementMs = 10_000L
/** A position this far from the extrapolated one is a seek, so the session timeline must refresh. */
private const val PositionJumpMs = 2_000L

/**
 * Media3 player facade over the app's common [Player] interface. It never decodes anything: every
 * command goes to whichever engine is active, so notification, lock screen, headset buttons and the
 * PiP window keep working on ExoPlayer, VLC and MPV alike.
 */
@OptIn(UnstableApi::class)
class SessionPlayer(looper: Looper, private val scope: CoroutineScope) : SimpleBasePlayer(looper) {
    private var entry: NowPlaying? = null
    private var playIntent = false
    private var observer: Job? = null

    val isActive: Boolean get() = entry != null
    val wantsToPlay: Boolean get() = entry != null && playIntent

    fun bind(next: NowPlaying?) {
        if (next === entry) return
        observer?.cancel()
        entry = next
        playIntent = next?.player?.runtimeState?.value?.let { it.isPlaying || it.isBuffering } ?: false
        invalidateState()
        val player = next?.player ?: return
        observer = scope.launch {
            var last: PlayerRuntimeState? = null
            var lastAt = 0L
            player.runtimeState.collect { runtime ->
                val now = android.os.SystemClock.elapsedRealtime()
                val previous = last
                // Buffering keeps the user's intent; only a settled state changes it.
                if (!runtime.isBuffering) playIntent = runtime.isPlaying
                val changed = previous == null ||
                    previous.isPlaying != runtime.isPlaying || previous.isBuffering != runtime.isBuffering ||
                    previous.ended != runtime.ended || previous.durationMs != runtime.durationMs ||
                    previous.speed != runtime.speed ||
                    abs(runtime.positionMs - expectedPosition(previous, now - lastAt)) > PositionJumpMs
                last = runtime
                lastAt = now
                if (changed) invalidateState()
            }
        }
    }

    private fun expectedPosition(state: PlayerRuntimeState, elapsedMs: Long): Long =
        if (state.isPlaying) state.positionMs + (elapsedMs * state.speed).toLong() else state.positionMs

    override fun getState(): State {
        val current = entry ?: return State.Builder()
            .setAvailableCommands(Commands.EMPTY)
            .setPlaybackState(STATE_IDLE)
            .build()
        val runtime = current.player.runtimeState.value
        val seekable = runtime.durationMs > 0
        val commands = Commands.Builder()
            .addAll(COMMAND_PLAY_PAUSE, COMMAND_STOP, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_METADATA, COMMAND_GET_TIMELINE)
            .apply { if (seekable) addAll(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD) }
            .build()
        val metadata = MediaMetadata.Builder()
            .setTitle(current.title)
            .setDisplayTitle(current.title)
            .setArtist(current.subtitle)
            .setSubtitle(current.subtitle)
            .setArtworkUri(current.artworkUri?.toUri())
            .setMediaType(if (current.isEpisode) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE)
            .build()
        val item = MediaItemData.Builder(current)
            .setMediaItem(MediaItem.Builder().setMediaId(current.title).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setDurationUs(if (seekable) Util.msToUs(runtime.durationMs) else C.TIME_UNSET)
            .setIsSeekable(seekable)
            .build()
        val playbackState = when {
            runtime.ended -> STATE_ENDED
            runtime.isBuffering -> STATE_BUFFERING
            runtime.isPlaying || seekable -> STATE_READY
            else -> STATE_BUFFERING
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlayWhenReady(playIntent, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setIsLoading(runtime.isBuffering)
            .setContentPositionMs { current.player.runtimeState.value.positionMs }
            .setContentBufferedPositionMs { current.player.runtimeState.value.bufferedPositionMs }
            .setPlaybackParameters(PlaybackParameters(runtime.speed.takeIf { it > 0f } ?: 1f))
            .setSeekBackIncrementMs(SeekIncrementMs)
            .setSeekForwardIncrementMs(SeekIncrementMs)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val current = entry ?: return Futures.immediateVoidFuture()
        playIntent = playWhenReady
        if (playWhenReady) current.player.play() else current.player.pause()
        current.onPausedChanged(!playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val current = entry ?: return Futures.immediateVoidFuture()
        val duration = current.player.runtimeState.value.durationMs
        val target = (if (positionMs == C.TIME_UNSET) 0L else positionMs)
            .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        current.player.seekTo(target)
        current.onSeekReported(target, duration)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> = handleSetPlayWhenReady(false)

    override fun handleRelease(): ListenableFuture<*> {
        observer?.cancel()
        entry = null
        return Futures.immediateVoidFuture()
    }
}
