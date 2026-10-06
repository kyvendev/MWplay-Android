package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val activeUri: String? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    /** Changes whenever the underlying player instance is replaced so Compose recreates its view. */
    val playerRevision: Long = 0L,
)

class PlaybackManager(
    private val context: Context,
) {
    private val mutableState = MutableStateFlow(PlaybackState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: Player? = null
    private var playerObserverJob: Job? = null
    private var playerRevision = 0L

    private data class LoadRequest(
        val uri: Uri,
        val title: String?,
        val startPositionMs: Long,
        val subtitles: List<ExternalSubtitle>,
        val preferredSubtitleLang: String?,
        val settings: com.stremio.core.types.profile.Profile.Settings?,
    )

    private var activeRequest: LoadRequest? = null
    private var mpvFallbackAttempted = false

    val state: StateFlow<PlaybackState> = mutableState

    fun load(
        uri: Uri,
        title: String? = null,
        startPositionMs: Long = 0,
        subtitles: List<ExternalSubtitle> = emptyList(),
        preferredSubtitleLang: String? = null,
        engine: PlayerEngine = PlayerEngine.EXO,
        settings: com.stremio.core.types.profile.Profile.Settings? = null,
    ) {
        playerObserverJob?.cancel()
        player?.release()
        activeRequest = LoadRequest(uri, title, startPositionMs, subtitles, preferredSubtitleLang, settings)
        mpvFallbackAttempted = engine == PlayerEngine.MPV

        val fallbackMessage = if (engine == PlayerEngine.MPV) "MPV unavailable; using ExoPlayer." else null
        player = runCatching {
            PlayerFactory.create(context, engine, settings).also {
                it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                it.play()
            }
        }.getOrElse { failure ->
            if (engine != PlayerEngine.MPV) throw failure
            ExoStreamPlayer(context, settings).also {
                it.load(uri, startPositionMs, subtitles, preferredSubtitleLang, settings)
                it.play()
                it.reportNonFatalError(fallbackMessage)
            }
        }

        publishPlayerState(uri, title, true)
        observeForExoFormatFallback(player)
    }

    private fun observeForExoFormatFallback(observedPlayer: Player?) {
        playerObserverJob?.cancel()
        if (observedPlayer == null || observedPlayer.engine != PlayerEngine.EXO || mpvFallbackAttempted) return

        playerObserverJob = scope.launch {
            observedPlayer.runtimeState.collect { runtime ->
                if (player !== observedPlayer || mpvFallbackAttempted) return@collect
                val error = runtime.error ?: return@collect
                if (!isFormatError(error)) return@collect

                val request = activeRequest ?: return@collect
                mpvFallbackAttempted = true
                val resumePosition = runtime.positionMs.takeIf { it > 0L } ?: request.startPositionMs
                switchToMpv(request, resumePosition, observedPlayer)
            }
        }
    }

    private fun isFormatError(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("formato da transmissão não suportado") ||
            normalized.contains("unsupported format") ||
            normalized.contains("unsupported container") ||
            normalized.contains("unsupported manifest")
    }

    private fun switchToMpv(request: LoadRequest, startPositionMs: Long, oldPlayer: Player) {
        val replacement = runCatching {
            PlayerFactory.create(context, PlayerEngine.MPV, request.settings).also {
                it.load(
                    request.uri,
                    startPositionMs,
                    request.subtitles,
                    request.preferredSubtitleLang,
                    request.settings,
                )
                it.play()
            }
        }.getOrNull() ?: return

        if (player !== oldPlayer) {
            replacement.release()
            return
        }

        playerObserverJob?.cancel()
        oldPlayer.release()
        player = replacement
        publishPlayerState(request.uri, request.title, true)
    }

    private fun publishPlayerState(uri: Uri, title: String?, isPlaying: Boolean) {
        playerRevision += 1
        mutableState.value = PlaybackState(
            activeUri = uri.toString(),
            title = title,
            isPlaying = isPlaying,
            playerRevision = playerRevision,
        )
    }

    fun attachView(view: android.view.View) = Unit

    fun detachView() = Unit

    fun play() {
        player?.play()
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    fun pause() {
        player?.pause()
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) {
        player?.addExternalSubtitleTracks(tracks)
    }

    fun addLocalSubtitle(track: ExternalSubtitle) {
        player?.addLocalSubtitle(track)
    }

    fun release() {
        playerObserverJob?.cancel()
        playerObserverJob = null
        player?.release()
        player = null
        activeRequest = null
        mpvFallbackAttempted = false
        playerRevision += 1
        mutableState.value = PlaybackState(playerRevision = playerRevision)
    }

    fun getPlayer(): Player? = player
}
