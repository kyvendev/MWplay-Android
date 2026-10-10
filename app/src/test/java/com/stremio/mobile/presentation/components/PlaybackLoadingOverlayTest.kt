package com.stremio.mobile.presentation.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-television-mdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
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
    fun titleRevealCyclesFromDimToLitAndBackIndefinitely() {
        val start = loadingPhaseAt(0)
        assertEquals(true, start.revealing)
        assertEquals(0f, start.sweep, 0.0001f)
        val midReveal = loadingPhaseAt(RevealPeriodMs / 2)
        assertEquals(true, midReveal.revealing)
        assertEquals(0.5f, midReveal.sweep, 0.0001f)
        val dimming = loadingPhaseAt(RevealPeriodMs + RevealPeriodMs / 2)
        assertEquals(false, dimming.revealing)
        // Two full periods later the same phase repeats: no end state, no progress percentage.
        assertEquals(midReveal.copy(slowDrift = 0f), loadingPhaseAt(RevealPeriodMs / 2 + 2 * RevealPeriodMs).copy(slowDrift = 0f))
    }

    @Test
    fun overlayFollowsLoadingStateWithoutTakingRemoteFocus() {
        var loading by mutableStateOf(true)
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                val first = remember { FocusRequester() }
                Row {
                    ThemedButton("Primeiro", { clicks++ }, modifier = Modifier.focusRequester(first).testTag("first"))
                    ThemedButton("Segundo", { clicks++ }, modifier = Modifier.testTag("second"))
                }
                PlaybackLoadingOverlay(
                    info = StreamsUiState(forItem = movie).toPlaybackLoadingInfo(),
                    visible = loading,
                    modifier = Modifier.fillMaxSize(),
                )
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    first.requestFocus()
                }
            }
        }
        composeRule.onNodeWithText("Duna: Parte Dois").assertExists()
        composeRule.onNodeWithText("Preparando a reprodução").assertExists()

        // D-pad keeps working underneath: the overlay has no focus targets of its own.
        composeRule.onNodeWithTag("first").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("second").assertIsFocused()

        composeRule.runOnIdle { loading = false }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Preparando a reprodução").assertDoesNotExist()
        composeRule.onNodeWithTag("second").assertIsFocused()
    }
}
