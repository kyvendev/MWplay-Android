package com.stremio.mobile.player

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SeriesTrackPreferencesTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun audio(id: String, lang: String) = PlayerTrackOption(id, PlayerTrackType.AUDIO, lang, lang, selected = false)
    private fun subtitle(id: String, lang: String, embedded: Boolean = true) =
        PlayerTrackOption(id, PlayerTrackType.SUBTITLE, lang, lang, selected = false, embedded = embedded)

    private val audioTracks = listOf(audio("a-en", "eng"), audio("a-pt", "por"), audio("a-ja", "jpn"))
    private val subtitleTracks = listOf(subtitle("s-en", "eng"), subtitle("s-pt", "por"))

    @Before
    fun clear() {
        context.getSharedPreferences("mwplay_series_tracks", 0).edit().clear().commit()
    }

    @Test
    fun choicesArePerSeriesAndSurviveRestarts() {
        val prefs = SeriesTrackPreferences(context)
        prefs.saveAudio("series|tt1", "jpn")
        prefs.saveSubtitle("series|tt1", "por")
        assertEquals(SeriesTrackPreference(audioLanguage = "jpn", subtitleLanguage = "por"), SeriesTrackPreferences(context).get("series|tt1"))
        assertNull("other series", prefs.get("series|tt2"))
        prefs.saveSubtitle("series|tt1", null)
        assertEquals(SeriesTrackPreference(audioLanguage = "jpn", subtitlesOff = true), prefs.get("series|tt1"))
    }

    @Test
    fun nextEpisodeUsesTheSeriesAudioOverTheProfile() {
        val series = SeriesTrackPreference(audioLanguage = "jpn")
        assertEquals("a-ja", TrackDefaults.audio(audioTracks, savedTrackId = null, series = series, profileLanguage = "por")?.id)
        // Without a series choice, the profile language as before.
        assertEquals("a-pt", TrackDefaults.audio(audioTracks, null, null, "por")?.id)
        // Resuming the same episode keeps the track saved for that stream.
        assertEquals("a-en", TrackDefaults.audio(audioTracks, "a-en", series, "por")?.id)
        // The series language missing in this release: back to the profile.
        assertEquals("a-pt", TrackDefaults.audio(audioTracks.filterNot { it.id == "a-ja" }, null, series, "por")?.id)
    }

    @Test
    fun nextEpisodeUsesTheSeriesSubtitles() {
        val choose = { tracks: List<PlayerTrackOption>, series: SeriesTrackPreference?, settings: String?, auto: Boolean ->
            TrackDefaults.subtitle(tracks, null, false, null, series, settings, auto) { 0 }
        }
        assertEquals(SubtitleDefault.Select(subtitleTracks[0]), choose(subtitleTracks, SeriesTrackPreference(subtitleLanguage = "eng"), "por", true))
        // Chosen even when automatic subtitles are off in the profile: it was the user's choice.
        assertEquals(SubtitleDefault.Select(subtitleTracks[0]), choose(subtitleTracks, SeriesTrackPreference(subtitleLanguage = "eng"), null, false))
        assertEquals(SubtitleDefault.Off, choose(subtitleTracks, SeriesTrackPreference(subtitlesOff = true), "por", true))
        // The wanted language may still arrive with the addon subtitles.
        assertEquals(SubtitleDefault.Wait, choose(listOf(subtitleTracks[1]), SeriesTrackPreference(subtitleLanguage = "eng"), "por", true))
        // No series choice: the profile, as before.
        assertEquals(SubtitleDefault.Select(subtitleTracks[1]), choose(subtitleTracks, null, "por", true))
        assertEquals(SubtitleDefault.Off, choose(subtitleTracks, null, null, true))
    }

    @Test
    fun resumingTheSameEpisodeKeepsItsSavedSubtitle() {
        val series = SeriesTrackPreference(subtitlesOff = true)
        val result = TrackDefaults.subtitle(subtitleTracks, "s-en", true, "eng", series, "por", true) { 0 }
        assertEquals(SubtitleDefault.Select(subtitleTracks[0]), result)
    }
}
