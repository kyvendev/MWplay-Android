package com.stremio.mobile.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The start / exhausted signals used by the source switch, with fake engines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackManagerFailoverTest {
    private class FakePlayer(override val engine: PlayerEngine) : Player {
        val state = MutableStateFlow(PlayerRuntimeState(isBuffering = true))
        var released = false
        override val runtimeState: StateFlow<PlayerRuntimeState> = state
        override fun createView(context: Context): View = View(context)
        override fun load(uri: Uri, startPositionMs: Long, subtitles: List<ExternalSubtitle>, preferredSubtitleLang: String?, settings: com.stremio.core.types.profile.Profile.Settings?) = Unit
        override fun retry() = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun setResizeMode(mode: PlayerResizeMode) = Unit
        override fun selectAudioTrack(id: String) = Unit
        override fun selectSubtitleTrack(id: String) = Unit
        override fun disableSubtitles() = Unit
        override fun setSubtitleStyle(style: PlayerSubtitleStyle) = Unit
        override fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) = Unit
        override fun addLocalSubtitle(track: ExternalSubtitle) = Unit
        override fun release() { released = true }
    }

    private val created = mutableListOf<FakePlayer>()
    private val manager = PlaybackManager(RuntimeEnvironment.getApplication()) { _, engine, _ ->
        FakePlayer(engine).also { created += it }
    }

    private fun idle() = ShadowLooper.idleMainLooper()
    private fun live() = created.filterNot { it.released }

    private fun fail(player: FakePlayer) {
        player.state.value = PlayerRuntimeState(error = "Playback failed")
        idle()
    }

    @Test
    fun enginesFallBackExoVlcMpvAndThenReportExhausted() {
        manager.load(Uri.parse("https://example.com/a.mp4"))
        idle()
        fail(created[0])
        fail(created[1])
        assertFalse(manager.state.value.enginesExhausted)
        fail(created[2])
        assertEquals(listOf(PlayerEngine.EXO, PlayerEngine.VLC, PlayerEngine.MPV), created.map { it.engine })
        assertTrue(manager.state.value.enginesExhausted)
        assertFalse(manager.state.value.playbackStarted)
    }

    @Test
    fun neverTwoPlayersAtOnceAcrossEnginesAndSources() {
        manager.load(Uri.parse("https://example.com/a.mp4"))
        idle()
        fail(created[0])
        assertEquals(1, live().size)
        // The next source: the previous engine is released first.
        manager.load(Uri.parse("https://example.com/b.mp4"))
        idle()
        assertEquals(1, live().size)
        manager.release()
        assertEquals(0, live().size)
    }

    @Test
    fun realPlaybackSetsStartedAndANewLoadResetsTheSignals() {
        manager.load(Uri.parse("https://example.com/a.mp4"))
        idle()
        val player = created.single()
        player.state.value = PlayerRuntimeState(isPlaying = true, positionMs = 0)
        idle()
        player.state.value = PlayerRuntimeState(isPlaying = true, positionMs = 500)
        idle()
        assertFalse(manager.state.value.playbackStarted)
        player.state.value = PlayerRuntimeState(isPlaying = true, positionMs = 1_100)
        idle()
        assertTrue(manager.state.value.playbackStarted)

        manager.load(Uri.parse("https://example.com/b.mp4"))
        idle()
        assertFalse(manager.state.value.playbackStarted)
        assertFalse(manager.state.value.enginesExhausted)
    }
}
