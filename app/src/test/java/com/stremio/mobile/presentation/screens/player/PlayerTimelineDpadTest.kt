package com.stremio.mobile.presentation.screens.player

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.stremio.mobile.presentation.components.DpadValueKeyAction
import com.stremio.mobile.presentation.components.dpadValueKeyAction
import com.stremio.mobile.player.PlayerResizeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "television", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class PlayerTimelineDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val seeks = mutableListOf<Long>()

    @Test
    fun onlyHorizontalArrowsSeek() {
        assertEquals(DpadValueKeyAction.DECREASE, dpadValueKeyAction(Key.DirectionLeft))
        assertEquals(DpadValueKeyAction.INCREASE, dpadValueKeyAction(Key.DirectionRight))
        assertEquals(DpadValueKeyAction.FOCUS_UP, dpadValueKeyAction(Key.DirectionUp))
        assertEquals(DpadValueKeyAction.FOCUS_DOWN, dpadValueKeyAction(Key.DirectionDown))
        assertEquals(DpadValueKeyAction.PASS, dpadValueKeyAction(Key.DirectionCenter))
    }

    @Test
    fun leftSeeksBackOneStep() {
        showControlsAndFocusTimeline()
        timeline().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(listOf(50_000L), seeks) }
        timeline().assertIsFocused()
    }

    @Test
    fun rightSeeksForwardOneStep() {
        showControlsAndFocusTimeline()
        timeline().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(listOf(70_000L), seeks) }
        timeline().assertIsFocused()
    }

    @Test
    fun upOnlyMovesFocusAndNeverSeeks() {
        showControlsAndFocusTimeline()
        timeline().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), seeks) }
        timeline().assertIsNotFocused()
    }

    @Test
    fun downOnlyMovesFocusAndNeverSeeks() {
        showControlsAndFocusTimeline()
        timeline().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), seeks) }
        timeline().assertIsNotFocused()
    }

    @Test
    fun eachVolumeActionAppearsOnceAndIsReachableWithTheDpad() {
        showControlsAndFocusTimeline()
        for (label in listOf("Silenciar", "Diminuir volume", "Aumentar volume")) {
            composeRule.onAllNodesWithContentDescription(label).assertCountEquals(1)
        }
        composeRule.onAllNodesWithContentDescription("Ativar som").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Silenciar").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithContentDescription("Silenciar").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Diminuir volume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Aumentar volume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        timeline().assertIsFocused()
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), seeks) }
    }

    private fun timeline() = composeRule.onNodeWithContentDescription("Progresso do vídeo")

    private fun showControlsAndFocusTimeline() {
        composeRule.setContent {
            ClassicPlayerControls(
                state = PlayerControlsState(
                    isPlaying = true, isBuffering = false, positionMs = 60_000L, durationMs = 600_000L,
                    bufferedPositionMs = 0L, currentSpeed = 1f, resizeMode = PlayerResizeMode.FIT, title = "Filme",
                    showControls = true, hasInfoHash = false, isStatsVisible = false, canSelectSubtitles = true,
                    canSelectAudio = true, isMuted = false, volumeFraction = 1f,
                ),
                actions = PlayerControlsActions(
                    onBack = {}, onPlayPause = {}, onSeekTo = { seeks += it }, onSkipForward = {}, onSkipBack = {},
                    onCycleSpeed = {}, onCycleAspect = {}, onToggleMute = {}, onShowSubtitles = {}, onShowAudio = {},
                    onToggleStats = {},
                ),
                backdrop = null,
            )
        }
        composeRule.mainClock.advanceTimeBy(200)
        timeline().performSemanticsAction(SemanticsActions.RequestFocus)
        timeline().assertIsFocused()
    }
}
