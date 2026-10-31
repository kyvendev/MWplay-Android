package com.stremio.mobile.presentation.screens.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.stremio.mobile.player.PlayerResizeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every control (with the new lock) fits on a small portrait phone and a landscape one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerControlsLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = PlayerControlsState(
        isPlaying = true, isBuffering = false, positionMs = 60_000, durationMs = 600_000, bufferedPositionMs = 0,
        currentSpeed = 1f, resizeMode = PlayerResizeMode.FIT, title = "Um título bem comprido para ocupar a barra de cima",
        showControls = true, hasInfoHash = true, isStatsVisible = false, canSelectSubtitles = true, canSelectAudio = true,
        isMuted = false, volumeFraction = 0.5f,
    )
    private val actions = PlayerControlsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onEnterPip = {}, onLock = {})

    private fun assertTopBar() {
        for (label in listOf("Bloquear tela", "Picture-in-Picture", "Transmitir com Chromecast")) {
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun modernFitsASmallPortraitPhone() {
        composeRule.setContent { ModernPlayerControls(state, actions, null, "static") }
        assertTopBar()
        composeRule.onNodeWithContentDescription("Mais opções").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-xhdpi")
    fun classicFitsASmallPortraitPhone() {
        composeRule.setContent { ClassicPlayerControls(state, actions, null) }
        assertTopBar()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xhdpi")
    fun bothFitALandscapePhone() {
        composeRule.setContent { ClassicPlayerControls(state, actions, null) }
        assertTopBar()
    }
}
