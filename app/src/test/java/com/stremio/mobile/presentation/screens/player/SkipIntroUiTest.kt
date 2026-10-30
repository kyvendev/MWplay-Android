package com.stremio.mobile.presentation.screens.player

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
}
