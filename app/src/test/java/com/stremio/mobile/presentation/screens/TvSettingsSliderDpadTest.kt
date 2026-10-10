package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.stremio.mobile.presentation.components.ThemedButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings value controls: UP/DOWN navigate between rows, only LEFT/RIGHT change the value. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class TvSettingsSliderDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableFloatStateOf(0.3f)

    private fun slider() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    private fun showSettings() {
        composeRule.setContent {
            Column {
                ThemedButton("Acima", {})
                SettingsSliderRow(title = "Transparência do vidro", value = value, onValueChange = { value = it }, valueRange = 0f..0.6f)
                ThemedButton("Abaixo", {})
            }
        }
        slider().performSemanticsAction(SemanticsActions.RequestFocus)
        slider().assertIsFocused()
    }

    @Test
    fun upMovesToTheRowAboveWithoutChangingTheValue() {
        showSettings()
        slider().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Acima").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0.3f, value, 0.0001f) }
    }

    @Test
    fun downMovesToTheRowBelowWithoutChangingTheValue() {
        showSettings()
        slider().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Abaixo").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0.3f, value, 0.0001f) }
    }

    @Test
    fun leftAndRightStepTheValueByFivePercentOfTheRange() {
        showSettings()
        slider().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(0.33f, value, 0.0001f) }
        slider().performKeyInput { pressKey(Key.DirectionLeft) }
        slider().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle { assertEquals(0.27f, value, 0.0001f) }
        slider().assertIsFocused()
    }

    @Test
    fun valueStaysInsideItsRange() {
        value = 0.59f
        showSettings()
        slider().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle { assertEquals(0.6f, value, 0.0001f) }
    }
}
