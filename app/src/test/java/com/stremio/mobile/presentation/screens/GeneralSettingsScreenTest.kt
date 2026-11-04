package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** General settings hold existing preferences; the Trakt section is gone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GeneralSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsExistingOptionsAndNoTrakt() {
        val keepScreen = mutableListOf<Boolean>()
        val updates = mutableListOf<Boolean>()
        composeRule.setContent {
            GeneralSettingsScreen(
                settings = null,
                onUpdateSettings = {},
                isKeepScreenOn = false,
                onSetKeepScreenOn = { keepScreen += it },
                isAutoUpdateEnabled = true,
                onSetAutoUpdateEnabled = { updates += it },
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Integração com Trakt").assertDoesNotExist()
        composeRule.onNodeWithText("Autenticar no Trakt").assertDoesNotExist()
        // Mobile data only where the screen is given the option.
        composeRule.onNodeWithText("Aviso de dados móveis").assertDoesNotExist()
        composeRule.onNodeWithText("Manter tela ligada").performClick()
        composeRule.onNodeWithText("Verificar atualizações automaticamente").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(true), keepScreen)
            assertEquals(listOf(false), updates)
        }
    }

    @Test
    fun mobileDataWarningAppearsWhenAvailable() {
        val warnings = mutableListOf<Boolean>()
        composeRule.setContent {
            GeneralSettingsScreen(
                settings = null,
                onUpdateSettings = {},
                isKeepScreenOn = false,
                onSetKeepScreenOn = {},
                isAutoUpdateEnabled = true,
                onSetAutoUpdateEnabled = {},
                isMobileDataWarning = true,
                onSetMobileDataWarning = { warnings += it },
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Aviso de dados móveis").performClick()
        composeRule.runOnIdle { assertEquals(listOf(false), warnings) }
    }
}
