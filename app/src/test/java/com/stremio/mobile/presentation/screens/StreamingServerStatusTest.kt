package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.stremio.mobile.server.StreamingServerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The status card must follow every controller state change, and flag a stub-only build. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-television-mdpi", application = Application::class)
class StreamingServerStatusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var serverState by mutableStateOf<StreamingServerState>(StreamingServerState.Stopped)
    private var nativeAvailable by mutableStateOf(true)

    private fun setScreen() {
        composeRule.setContent {
            StreamingSettingsScreen(
                serverState = serverState,
                isNativeServerAvailable = nativeAvailable,
                serverSettings = null,
                isSeedingEnabled = false,
                minSeedsThreshold = 1,
                minDownloadSpeedBps = 50_000L,
                preferredQuality = "Any",
                isAutoSwitchOnDeadStream = false,
                onUpdateServerSettings = {},
                onSetSeedingEnabled = {},
                onSetMinSeedsThreshold = {},
                onSetMinDownloadSpeedBps = {},
                onSetPreferredQuality = {},
                onSetAutoSwitchOnDeadStream = {},
                onBack = {},
            )
        }
    }

    @Test
    fun statusFollowsLifecycleTransitions() {
        setScreen()
        composeRule.onNodeWithText("Status: Parado").assertIsDisplayed()

        composeRule.runOnIdle { serverState = StreamingServerState.Starting }
        composeRule.onNodeWithText("Status: Iniciando…").assertIsDisplayed()

        composeRule.runOnIdle { serverState = StreamingServerState.Ready("http://127.0.0.1:11470") }
        composeRule.onNodeWithText("Status: Em execução").assertIsDisplayed()
        composeRule.onNodeWithText("URL: http://127.0.0.1:11470").assertIsDisplayed()

        composeRule.runOnIdle { serverState = StreamingServerState.Failed("failed to bind 11470") }
        composeRule.onNodeWithText("Status: Erro: A porta 11470 já está em uso", substring = true).assertIsDisplayed()

        composeRule.runOnIdle { serverState = StreamingServerState.Stopped }
        composeRule.onNodeWithText("Status: Parado").assertIsDisplayed()
    }

    @Test
    fun missingNativeServerIsReportedInsteadOfStopped() {
        nativeAvailable = false
        setScreen()
        composeRule.onNodeWithText("Status: Indisponível nesta versão").assertIsDisplayed()
        composeRule.onNodeWithText("Status: Parado").assertDoesNotExist()
    }
}
