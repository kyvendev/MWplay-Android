package com.stremio.mobile.cast

import android.content.Context
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.stremio.mobile.player.PlayerTrackOption
import java.net.URI
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Holds no Activity or player reference. The SDK owns discovery, reconnection and notifications. */
class CastPlaybackController(
    private val context: Context,
    private val onTimeChanged: (Long, Long) -> Unit,
    private val onPausedChanged: (Boolean) -> Unit,
    private val onEnded: () -> Unit,
) {
    private val mutableState = MutableStateFlow(CastPlaybackState())
    val state: StateFlow<CastPlaybackState> = mutableState
    private val moduleExecutor = Executors.newSingleThreadExecutor()
    private var castContext: CastContext? = null
    private var client: RemoteMediaClient? = null
    private var listeningSession: CastSession? = null
    private var started = false
    private var loadRevision = 0L
    private var ownedUrl: String? = null
    private var ownedLocalUri: String? = null
    private var lastOwnedResume: CastLocalResume? = null
    private var endingResume: CastLocalResume? = null
    private var endReported = false
    private var lastReportedPosition = -1L
    private var lastReportedPaused: Boolean? = null

    private val castStateListener = CastStateListener { updateDiscovery(it) }
    private val deviceListener = object : Cast.Listener() {
        override fun onVolumeChanged() = refreshMedia()
    }
    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = refreshMedia()
        override fun onMetadataUpdated() = refreshMedia()
        override fun onMediaError(error: MediaError) {
            fail("O Chromecast não conseguiu reproduzir este vídeo. Verifique o formato e o acesso ao link, ou volte ao celular.")
        }
    }
    private val progressListener = RemoteMediaClient.ProgressListener { position, duration ->
        mutableState.value = mutableState.value.copy(positionMs = position.coerceAtLeast(0), durationMs = duration.coerceAtLeast(0))
        reportProgress()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) { mutableState.value = mutableState.value.copy(connecting = true, error = null) }
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = connectionFailed()
        override fun onSessionEnding(session: CastSession) { endingResume = endingResume ?: lastOwnedResume }
        override fun onSessionEnded(session: CastSession, error: Int) = ended()
        override fun onSessionResuming(session: CastSession, sessionId: String) { mutableState.value = mutableState.value.copy(connecting = true) }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) { ended(); connectionFailed() }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            // Keep local playback paused during Wi-Fi reconnection: the receiver may still be playing.
            mutableState.value = mutableState.value.copy(connected = false, suspended = true, connecting = true)
        }
    }

    fun initialize() {
        if (started) return
        started = true
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            mutableState.value = mutableState.value.copy(initializing = false, error = "Chromecast precisa do Google Play Services atualizado. Você também pode abrir o vídeo em outro aplicativo.")
            return
        }
        // This call is made from Activity.onCreate on the main thread; module loading runs off it.
        runCatching { CastContext.getSharedInstance(context, moduleExecutor) }
            .onFailure { sdkUnavailable() }
            .getOrNull()
            ?.addOnSuccessListener { loaded ->
                castContext = loaded
                loaded.addCastStateListener(castStateListener)
                loaded.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                mutableState.value = mutableState.value.copy(ready = true, initializing = false, error = null)
                updateDiscovery(loaded.castState)
                loaded.sessionManager.currentCastSession?.let(::attach)
            }
            ?.addOnFailureListener { sdkUnavailable() }
    }

    private fun sdkUnavailable() {
        mutableState.value = mutableState.value.copy(initializing = false, ready = false,
            error = "Chromecast indisponível neste aparelho. Atualize o Google Play Services ou use outro aplicativo.")
    }

    fun retryInitialize() {
        if (mutableState.value.ready || mutableState.value.initializing) return
        started = false
        mutableState.value = mutableState.value.copy(initializing = true, error = null)
        initialize()
    }

    private fun updateDiscovery(castState: Int) {
        mutableState.value = mutableState.value.copy(devicesAvailable = castState != CastState.NO_DEVICES_AVAILABLE)
    }

    private fun attach(session: CastSession) {
        detachClient()
        listeningSession = session
        session.addCastListener(deviceListener)
        client = session.remoteMediaClient
        client?.registerCallback(mediaCallback)
        client?.addProgressListener(progressListener, 1000)
        mutableState.value = mutableState.value.copy(
            connected = session.isConnected, connecting = false, suspended = false,
            deviceName = session.castDevice?.friendlyName, error = null,
        )
        refreshMedia()
    }

    private fun detachClient() {
        listeningSession?.removeCastListener(deviceListener)
        listeningSession = null
        client?.unregisterCallback(mediaCallback)
        client?.removeProgressListener(progressListener)
        client = null
    }

    private fun ended() {
        loadRevision++
        val previous = mutableState.value
        // SDK media callbacks can clear MediaInfo before onSessionEnded is delivered.
        val resume = endingResume ?: lastOwnedResume
        detachClient()
        ownedUrl = null
        ownedLocalUri = null
        lastOwnedResume = null
        endingResume = null
        mutableState.value = CastPlaybackState(
            ready = previous.ready, initializing = false, devicesAvailable = previous.devicesAvailable,
            localResume = resume,
        )
    }

    private fun connectionFailed() {
        mutableState.value = mutableState.value.copy(connecting = false, loading = false,
            error = "Não foi possível conectar ao Chromecast. Confirme que os aparelhos estão na mesma rede Wi-Fi e tente novamente.")
    }

    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }
    fun consumeLocalResume() { mutableState.value = mutableState.value.copy(localResume = null) }
    private fun fail(message: String) { mutableState.value = mutableState.value.copy(loading = false, error = message) }

    fun attachLocalPlayback(localUri: String?, remoteUrl: String?) {
        if (mutableState.value.loading) return
        if (localUri == null || remoteUrl == null || remoteUrl != mutableState.value.mediaUrl) return
        if (client?.mediaInfo?.customData?.optBoolean("mwPlayMedia") != true) return
        ownedUrl = remoteUrl
        ownedLocalUri = localUri
        lastOwnedResume = lastOwnedResume?.copy(localUri = localUri)
        mutableState.value = mutableState.value.copy(localUri = localUri)
    }

    fun load(
        url: String?, localUri: String?, title: String, positionMs: Long, durationMs: Long,
        playing: Boolean, requiresHeaders: Boolean, subtitles: List<PlayerTrackOption>, onLoaded: () -> Unit,
    ) {
        val decision = CastMediaPolicy.evaluate(url, requiresHeaders)
        if (!decision.supported) { fail(decision.rejection ?: "Este vídeo não pode ser transmitido."); return }
        val remote = client
        if (remote == null || !mutableState.value.connected) { connectionFailed(); return }
        if (mutableState.value.loading) return
        val tracks = subtitles.filter { track ->
            !track.local && !track.embedded && CastMediaPolicy.isRemoteHttpUrl(track.url) &&
                runCatching { URI(track.url!!).path.lowercase().endsWith(".vtt") }.getOrDefault(false)
        }.distinctBy { it.url }.mapIndexed { index, track ->
            MediaTrack.Builder((index + 1).toLong(), MediaTrack.TYPE_TEXT)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES).setContentId(track.url!!)
                .setContentType("text/vtt").setName(track.label)
                .apply { track.languageCode?.let { setLanguage(it) } }.build() to track.selected
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, title) }
        val info = MediaInfo.Builder(decision.url!!).setContentType(decision.contentType!!)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata)
            .setCustomData(JSONObject().put("mwPlayMedia", true))
            .apply { if (durationMs > 0) setStreamDuration(durationMs); if (tracks.isNotEmpty()) setMediaTracks(tracks.map { it.first }) }
            .build()
        val request = MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(playing)
            .setCurrentTime(positionMs.coerceAtLeast(0))
            .apply { if (tracks.isNotEmpty()) setActiveTrackIds(tracks.filter { it.second }.map { it.first.id }.toLongArray()) }
            .build()
        val revision = ++loadRevision
        mutableState.value = mutableState.value.copy(loading = true, error = null, localResume = null)
        remote.load(request).setResultCallback { result ->
            if (revision != loadRevision || client !== remote) return@setResultCallback
            if (!result.status.isSuccess) {
                fail("O Chromecast recusou o vídeo. A TV precisa acessar o link diretamente e suportar seu formato. A reprodução no celular foi mantida.")
                return@setResultCallback
            }
            ownedUrl = decision.url
            ownedLocalUri = localUri ?: decision.url
            lastOwnedResume = CastLocalResume(ownedLocalUri!!, positionMs, durationMs, playing)
            endingResume = null
            endReported = false
            lastReportedPosition = -1
            lastReportedPaused = null
            // Only pause the phone after the receiver accepts the load request.
            onLoaded()
            mutableState.value = mutableState.value.copy(loading = false, mediaUrl = decision.url,
                localUri = ownedLocalUri, title = title, positionMs = positionMs, durationMs = durationMs, playing = playing)
            refreshMedia()
        }
    }

    private fun refreshMedia() {
        val remote = client ?: return
        val info = remote.mediaInfo
        val status = remote.mediaStatus
        val session = castContext?.sessionManager?.currentCastSession
        val url = info?.contentId
        if (url != null && ownedUrl != null && url != ownedUrl && !mutableState.value.loading) {
            // Another sender took over this receiver: its progress must not resume our phone video.
            ownedUrl = null
            ownedLocalUri = null
            lastOwnedResume = null
            endingResume = null
        }
        val owns = ownedUrl != null && url == ownedUrl
        val ids = status?.activeTrackIds.orEmpty().toSet()
        val tracks = info?.mediaTracks.orEmpty().filter { it.type == MediaTrack.TYPE_TEXT }.map {
            CastSubtitle(it.id, it.name ?: it.language ?: "Legenda", it.language, it.id in ids)
        }
        mutableState.value = mutableState.value.copy(
            mediaUrl = url, localUri = if (owns) ownedLocalUri else null,
            title = info?.metadata?.getString(MediaMetadata.KEY_TITLE), playing = remote.isPlaying,
            buffering = remote.isBuffering, positionMs = remote.approximateStreamPosition.coerceAtLeast(0),
            durationMs = remote.streamDuration.coerceAtLeast(0), subtitles = tracks,
            volume = (session?.volume ?: 1.0).toFloat().coerceIn(0f, 1f), muted = session?.isMute ?: false,
        )
        reportProgress()
        if (owns && remote.isIdle && status?.idleReason == MediaStatus.IDLE_REASON_FINISHED && !endReported) {
            endReported = true
            onEnded()
        }
    }

    private fun reportProgress() {
        val value = mutableState.value
        if (ownedUrl == null || value.mediaUrl != ownedUrl) return
        if (value.durationMs > 0 || lastOwnedResume?.durationMs == 0L || lastOwnedResume == null) {
            ownedLocalUri?.let { lastOwnedResume = CastLocalResume(it, value.positionMs, value.durationMs, value.playing) }
        }
        if (value.durationMs <= 0) return
        if (lastReportedPosition < 0 || kotlin.math.abs(value.positionMs - lastReportedPosition) >= 5000) {
            lastReportedPosition = value.positionMs
            onTimeChanged(value.positionMs, value.durationMs)
        }
        val paused = !value.playing
        if (!value.buffering && lastReportedPaused != paused) { lastReportedPaused = paused; onPausedChanged(paused) }
    }

    fun togglePlayback() {
        val remote = client ?: return
        if (mutableState.value.loading) return
        if (remote.isIdle && remote.mediaInfo != null) {
            // PLAY cannot restart an idle receiver; reload the same accepted media from its start.
            val revision = ++loadRevision
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            remote.load(MediaLoadRequestData.Builder().setMediaInfo(remote.mediaInfo)
                .setAutoplay(true).setCurrentTime(0).setActiveTrackIds(remote.mediaStatus?.activeTrackIds).build())
                .setResultCallback { result ->
                    if (revision != loadRevision || client !== remote) return@setResultCallback
                    mutableState.value = mutableState.value.copy(loading = false)
                    if (result.status.isSuccess) { endReported = false; lastReportedPosition = -1; refreshMedia() }
                    else checkResult(false)
                }
        }
        else if (remote.isPlaying) remote.pause().setResultCallback { checkResult(it.status.isSuccess) }
        else remote.play().setResultCallback { checkResult(it.status.isSuccess) }
    }
    fun seekTo(positionMs: Long) {
        val duration = mutableState.value.durationMs
        val target = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        client?.seek(MediaSeekOptions.Builder().setPosition(target).build())?.setResultCallback { checkResult(it.status.isSuccess) }
    }
    fun setVolume(volume: Float) {
        runCatching { castContext?.sessionManager?.currentCastSession?.volume = volume.toDouble().coerceIn(0.0, 1.0) }
            .onFailure { fail("Não foi possível ajustar o volume da TV.") }
        refreshMedia()
    }
    fun toggleMute() {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        runCatching { session.isMute = !session.isMute }.onFailure { fail("Não foi possível ajustar o volume da TV.") }
        refreshMedia()
    }
    fun selectSubtitle(id: Long?) {
        client?.setActiveMediaTracks(if (id == null) longArrayOf() else longArrayOf(id))
            ?.setResultCallback { checkResult(it.status.isSuccess) }
    }
    fun stopCasting() {
        endingResume = lastOwnedResume
        castContext?.sessionManager?.endCurrentSession(true)
    }
    private fun checkResult(success: Boolean) { if (!success) fail("O Chromecast não respondeu ao comando. Verifique sua conexão Wi-Fi e tente novamente.") }
}
