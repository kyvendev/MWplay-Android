package com.stremio.mobile.presentation.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.presentation.state.StreamsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi", application = Application::class)
class PlaybackLoadingOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val movie = CatalogItem("tt1", "movie", "Duna: Parte Dois", "poster.jpg", "backdrop.jpg", "2024", "8.5")
    private val series = CatalogItem("tt2", "series", "Ruptura", "poster2.jpg", null, "2022", "8.7")
    private val episode = EpisodeOption("tt2:2:3", 2, 3, "Quem é quem", "thumb.jpg", null, watched = false, isCurrent = false)

    @Test
    fun movieUsesTitleAndOfficialBackdrop() {
        val info = StreamsUiState(forItem = movie).toPlaybackLoadingInfo()!!
        assertEquals("Duna: Parte Dois", info.title)
        assertEquals("backdrop.jpg", info.backdropUrl)
        assertNull(info.episodeLabel)
        assertNull(info.episodeTitle)
    }

    @Test
    fun episodeShowsSeasonEpisodeAndNameFromLoadedEpisodes() {
        val info = StreamsUiState(
            forItem = series,
            isSeries = true,
            episodes = listOf(episode),
            selectedVideoId = episode.videoId,
        ).toPlaybackLoadingInfo()!!
        assertEquals("Ruptura", info.title)
        assertEquals("Temporada 2 · Episódio 3", info.episodeLabel)
        assertEquals("Quem é quem", info.episodeTitle)
        // No series background: falls back to the episode still before the poster.
        assertEquals("thumb.jpg", info.backdropUrl)
    }

    @Test
    fun nextEpisodeOutsideLoadedSeasonReusesExistingLabel() {
        val info = StreamsUiState(
            forItem = series,
            isSeries = true,
            selectedVideoId = "tt2:3:1",
            selectedEpisodeLabel = "Novo começo · S3E1",
        ).toPlaybackLoadingInfo()!!
        assertEquals("Novo começo · S3E1", info.episodeLabel)
        assertEquals("poster2.jpg", info.backdropUrl)
    }

    @Test
    fun itemWithoutAnyArtworkStillBuildsTheScreen() {
        val info = StreamsUiState(forItem = movie.copy(poster = null, background = null)).toPlaybackLoadingInfo()!!
        assertNull(info.backdropUrl)
        assertEquals("Duna: Parte Dois", info.title)
    }

    @Test
    fun titleRevealCyclesFromDimToLitAndBackIndefinitely() {
        val start = loadingPhaseAt(0)
        assertEquals(true, start.revealing)
        assertEquals(0f, start.sweep, 0.0001f)
        val midReveal = loadingPhaseAt(RevealPeriodMs / 2)
        assertEquals(true, midReveal.revealing)
        assertEquals(0.5f, midReveal.sweep, 0.0001f)
        assertEquals(false, loadingPhaseAt(RevealPeriodMs + RevealPeriodMs / 2).revealing)
        // Two full periods later the same phase repeats: no end state, no progress percentage.
        assertEquals(midReveal.copy(slowDrift = 0f), loadingPhaseAt(RevealPeriodMs / 2 + 2 * RevealPeriodMs).copy(slowDrift = 0f))
    }

    @Test
    fun overlayBlocksHiddenControlsWhileVisibleAndReleasesThemAfterwards() {
        var loading by mutableStateOf(true)
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Button(onClick = { clicks++ }, modifier = Modifier.fillMaxSize()) { Text("Fonte oculta") }
                PlaybackLoadingOverlay(
                    info = StreamsUiState(forItem = movie).toPlaybackLoadingInfo(),
                    visible = loading,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNodeWithText("Duna: Parte Dois").assertExists()
        composeRule.onNodeWithText("Preparando a reprodução").assertExists()

        // A tap on the loading screen must not reach the invisible control underneath.
        composeRule.onNodeWithText("Preparando a reprodução").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, clicks) }

        composeRule.runOnIdle { loading = false }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Preparando a reprodução").assertDoesNotExist()
        composeRule.onNodeWithText("Fonte oculta").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
