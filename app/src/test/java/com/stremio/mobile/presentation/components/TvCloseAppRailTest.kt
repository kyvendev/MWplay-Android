package com.stremio.mobile.presentation.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.stremio.mobile.presentation.navigation.AppView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Fechar app" in the rail, on the smallest common TV layout (1080p at 540 dp tall). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class TvCloseAppRailTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fitsBelowTheSectionsAndOkRunsIt() {
        var closes = 0
        composeRule.setContent {
            TvNavigationScaffold(
                selectedView = AppView.Settings,
                backdrop = null,
                onSelect = {},
                contentFocusRequester = androidx.compose.runtime.remember { FocusRequester() },
                onCloseApp = { closes++ },
            ) { contentModifier -> Box(contentModifier.fillMaxSize()) }
        }
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val button = composeRule.onNodeWithText("Fechar app").fetchSemanticsNode().boundsInRoot
        assertTrue("The button must fit the rail: $button / $screen", button.bottom <= screen.bottom)

        composeRule.onNodeWithText("Ajustes").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithText("Ajustes").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Fechar app").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(1, closes) }
    }
}
