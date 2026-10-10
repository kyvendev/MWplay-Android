package com.stremio.mobile.player.session

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.media3.common.Player as Media3Player
import com.stremio.mobile.player.ExternalSubtitle
import com.stremio.mobile.player.Player
import com.stremio.mobile.player.PlayerEngine
import com.stremio.mobile.player.PlayerResizeMode
import com.stremio.mobile.player.PlayerRuntimeState
import com.stremio.mobile.player.PlayerSubtitleStyle
import com.stremio.mobile.presentation.screens.player.pipAspect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionPlayerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = SessionPlayer(Looper.getMainLooper(), scope)
    private val paused = mutableListOf<Boolean>()
    private val seeks = mutableListOf<Long>()

    @After fun tearDown() { session.release(); scope.cancel() }

    private fun entry(player: FakeEngine, title: String = "Ruptura") = NowPlaying(
        player = player,
        title = title,
        subtitle = "Temporada 2 · Episódio 3",
        artworkUri = "https://img/backdrop.jpg",
        isEpisode = true,
        onPausedChanged = { paused += it },
        onSeekReported = { position, _ -> seeks += position },
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun mirrorsTheActiveEngineStateAndMetadata() {
        val engine = FakeEngine(PlayerEngine.VLC, PlayerRuntimeState(isPlaying = true, positionMs = 42_000, durationMs = 3_600_000))
        session.bind(entry(engine))
        idle()
        assertEquals(Media3Player.STATE_READY, session.playbackState)
        assertTrue(session.playWhenReady)
        assertEquals(42_000L, session.currentPosition)
        assertEquals(3_600_000L, session.duration)
        assertEquals("Ruptura", session.mediaMetadata.title.toString())
        assertEquals("Temporada 2 · Episódio 3", session.mediaMetadata.artist.toString())
        assertTrue(session.isCommandAvailable(Media3Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))

        engine.state.value = engine.state.value.copy(isPlaying = false)
        idle()
        assertFalse(session.playWhenReady)
    }

    @Test
    fun commandsReachTheEngineAndAreReportedLikeTheOnScreenControls() {
        val engine = FakeEngine(PlayerEngine.MPV, PlayerRuntimeState(isPlaying = true, durationMs = 600_000))
        session.bind(entry(engine))
        idle()
        session.pause()
        session.seekTo(120_000)
        session.play()
        idle()
        assertEquals(listOf("pause", "seek:120000", "play"), engine.calls)
        assertEquals(listOf(true, false), paused)
        assertEquals(listOf(120_000L), seeks)
    }

    @Test
    fun followsTheFallbackEngineAndGoesIdleWhenPlaybackCloses() {
        val exo = FakeEngine(PlayerEngine.EXO, PlayerRuntimeState(isPlaying = true, durationMs = 600_000))
        val vlc = FakeEngine(PlayerEngine.VLC, PlayerRuntimeState(isPlaying = true, durationMs = 600_000))
        session.bind(entry(exo))
        idle()
        session.bind(entry(vlc))
        idle()
        session.pause()
        idle()
        assertEquals(emptyList<String>(), exo.calls)
        assertEquals(listOf("pause"), vlc.calls)

        session.bind(null)
        idle()
        assertEquals(Media3Player.STATE_IDLE, session.playbackState)
        assertFalse(session.isActive)
    }

    @Test
    fun liveStreamsWithoutDurationAreNotSeekable() {
        session.bind(entry(FakeEngine(PlayerEngine.EXO, PlayerRuntimeState(isPlaying = true))))
        idle()
        assertFalse(session.isCommandAvailable(Media3Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(session.isCommandAvailable(Media3Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun mediaFollowsTheConnectedPrivateOutput() {
        assertEquals(AudioOutputType.SPEAKER, mediaOutputFor(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
        assertEquals(AudioOutputType.WIRED, mediaOutputFor(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertEquals(AudioOutputType.BLUETOOTH, mediaOutputFor(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertEquals(AudioOutputType.USB, mediaOutputFor(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_USB_HEADSET)))
        assertEquals(AudioOutputType.HDMI, mediaOutputFor(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_HDMI)))
        // A call-only Bluetooth link does not take media away from the speaker.
        assertEquals(AudioOutputType.SPEAKER, mediaOutputFor(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)))
        assertTrue(AudioOutputType.BLUETOOTH.isPrivate)
        assertFalse(AudioOutputType.SPEAKER.isPrivate)
    }

    @Test
    fun pipKeepsTheVideoShapeWithinTheSystemLimits() {
        assertEquals(1920 to 1080, pipAspect(1920, 1080))
        assertEquals(239 to 100, pipAspect(3840, 1080))
        assertEquals(100 to 239, pipAspect(400, 1600))
        assertEquals(16 to 9, pipAspect(0, 0))
    }
}

private class FakeEngine(override val engine: PlayerEngine, initial: PlayerRuntimeState) : Player {
    val state = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    override val runtimeState = state
    override fun createView(context: Context): View = View(context)
    override fun load(uri: Uri, startPositionMs: Long, subtitles: List<ExternalSubtitle>, preferredSubtitleLang: String?, settings: com.stremio.core.types.profile.Profile.Settings?) = Unit
    override fun retry() = Unit
    override fun play() { calls += "play"; state.value = state.value.copy(isPlaying = true) }
    override fun pause() { calls += "pause"; state.value = state.value.copy(isPlaying = false) }
    override fun seekTo(positionMs: Long) { calls += "seek:$positionMs"; state.value = state.value.copy(positionMs = positionMs) }
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun setResizeMode(mode: PlayerResizeMode) = Unit
    override fun selectAudioTrack(id: String) = Unit
    override fun selectSubtitleTrack(id: String) = Unit
    override fun disableSubtitles() = Unit
    override fun setSubtitleStyle(style: PlayerSubtitleStyle) = Unit
    override fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) = Unit
    override fun addLocalSubtitle(track: ExternalSubtitle) = Unit
    override fun release() = Unit
}
