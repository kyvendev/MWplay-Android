package com.stremio.mobile.presentation.screens.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi", application = Application::class)
class SkipIntroUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun skipButtonRunsItsAction() {
        var taps = 0
        composeRule.setContent { SkipIntroButton(onClick = { taps++ }) }
        composeRule.onNodeWithText("Pular abertura").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun markingUsesThePlaybackPositionAndOnlySavesAPlausibleOpening() {
        var position by mutableLongStateOf(62_000L)
        var saved: Triple<Long, Long, Boolean>? = null
        composeRule.setContent {
            IntroMarkDialog(
                currentPositionMs = position,
                durationMs = 1_440_000,
                existing = null,
                onSave = { start, end, series -> saved = Triple(start, end, series) },
                onClear = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Salvar").assertIsNotEnabled()
        composeRule.onNodeWithText("Início", substring = true).performClick()
        // End at the same spot as the start: rejected.
        composeRule.onNodeWithText("Fim", substring = true).performClick()
        composeRule.onNodeWithText("Salvar").assertIsNotEnabled()

        composeRule.runOnIdle { position = 151_000L }
        composeRule.onNodeWithText("Fim", substring = true).performClick()
        composeRule.onNodeWithText("Usar em todos os episódios desta série").performClick()
        composeRule.onNodeWithText("Salvar").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(Triple(62_000L, 151_000L, true), saved) }
    }
}
