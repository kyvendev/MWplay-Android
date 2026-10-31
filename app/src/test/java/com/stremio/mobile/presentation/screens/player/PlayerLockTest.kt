package com.stremio.mobile.presentation.screens.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.stremio.mobile.player.PlayerResizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lock layer over a stand-in for the player's own touch handling (tap = play/pause,
 * drag = seek / volume / brightness). The lock never talks to the engine, so the same
 * behaviour holds for EXO, VLC and MPV.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi", application = Application::class)
class PlayerLockTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var taps = 0
    private var drags = 0
    private var unlocks = 0
    private var locked by mutableStateOf(true)
    private var playing by mutableStateOf(true)

    private fun setPlayer() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                // Stand-in for playerGestures + controls underneath (reads `playing` so pause/resume recomposes it).
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(playing) { detectTapGestures { taps++ } }
                        .pointerInput(Unit) { detectDragGestures { _, _ -> drags++ } },
                )
                if (locked) PlayerLockLayer(onUnlock = { unlocks++; locked = false })
            }
        }
    }

    private fun lockButton() = composeRule.onNodeWithContentDescription("Desbloquear tela")

    @Test
    fun tapsOnTheLockedScreenNeverPauseOrResume() {
        setPlayer()
        repeat(3) { composeRule.onNodeWithTag(PLAYER_LOCK_LAYER_TAG).performTouchInput { click(center.copy(y = 40f)) } }
        composeRule.runOnIdle {
            assertEquals(0, taps)
            assertEquals(0, unlocks)
        }
    }

    @Test
    fun dragsCannotSeekOrChangeVolumeOrBrightness() {
        setPlayer()
        composeRule.onNodeWithTag(PLAYER_LOCK_LAYER_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(PLAYER_LOCK_LAYER_TAG).performTouchInput { swipe(topCenter, bottomCenter) }
        composeRule.runOnIdle { assertEquals(0, drags) }
    }

    @Test
    fun aTapOnlyRevealsTheLockWhichHidesAgain() {
        setPlayer()
        // Shown right after locking, then hidden after a few seconds.
        lockButton().assertExists()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(PLAYER_LOCK_HINT_MS + 1_000)
        composeRule.onAllNodesWithContentDescription("Desbloquear tela").assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithTag(PLAYER_LOCK_LAYER_TAG).performTouchInput { click(center.copy(y = 40f)) }
        lockButton().assertExists()
        assertEquals(0, taps)
        assertEquals(0, unlocks)
    }

    @Test
    fun tappingTheLockUnlocksWithoutAPlaybackAction() {
        setPlayer()
        lockButton().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertEquals(1, unlocks)
            assertFalse(locked)
            assertEquals(0, taps)
        }
        // Unlocked: the player's own touches work again.
        composeRule.onNodeWithTag(PLAYER_LOCK_LAYER_TAG).assertDoesNotExist()
    }

    @Test
    fun staysLockedAcrossPauseAndResume() {
        setPlayer()
        composeRule.runOnIdle { playing = false }
        composeRule.runOnIdle { playing = true }
        composeRule.onNodeWithTag(PLAYER_LOCK_LAYER_TAG).performTouchInput { click(center.copy(y = 40f)) }
        composeRule.runOnIdle {
            assertTrue(locked)
            assertEquals(0, taps)
        }
    }

    private val controlsState = PlayerControlsState(
        isPlaying = true, isBuffering = false, positionMs = 60_000, durationMs = 600_000, bufferedPositionMs = 0,
        currentSpeed = 1f, resizeMode = PlayerResizeMode.FIT, title = "Filme", showControls = true, hasInfoHash = false,
        isStatsVisible = false, canSelectSubtitles = true, canSelectAudio = true, isMuted = false, volumeFraction = 0.5f,
    )
    private fun actions(onLock: () -> Unit) = PlayerControlsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onLock = onLock)

    @Test
    fun classicControlsOfferTheLock() {
        var locks = 0
        composeRule.setContent { ClassicPlayerControls(controlsState, actions { locks++ }, null) }
        composeRule.onNodeWithContentDescription("Bloquear tela").performClick()
        composeRule.runOnIdle { assertEquals(1, locks) }
    }

    @Test
    fun modernControlsOfferTheLock() {
        var locks = 0
        composeRule.setContent { ModernPlayerControls(controlsState, actions { locks++ }, null, "static") }
        composeRule.onNodeWithContentDescription("Bloquear tela").performClick()
        composeRule.runOnIdle { assertEquals(1, locks) }
    }
}
