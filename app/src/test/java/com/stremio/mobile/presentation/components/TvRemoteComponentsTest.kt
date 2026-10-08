package com.stremio.mobile.presentation.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.presentation.navigation.AppView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "television", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class TvRemoteComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modernButtonActivatesOnceWithRemoteSelect() {
        var clicks = 0
        composeRule.setContent {
            ModernTvContent {
                ThemedButton("Action", { clicks++ }, modifier = Modifier.testTag("action"))
            }
        }

        composeRule.onNodeWithTag("action")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun modernToggleChangesWithRemoteSelect() {
        var checked by mutableStateOf(false)
        composeRule.setContent {
            ModernTvContent {
                ThemedToggle(checked, { checked = it }, modifier = Modifier.testTag("toggle"))
            }
        }

        composeRule.onNodeWithTag("toggle")
            .assertIsOff()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .assertIsOn()
    }

    @Test
    fun disabledModernButtonIsSkippedByDirectionalNavigation() {
        var disabledClicks = 0
        composeRule.setContent {
            ModernTvContent {
                Row {
                    ThemedButton("First", {}, modifier = Modifier.testTag("first"))
                    ThemedButton("Disabled", { disabledClicks++ }, enabled = false)
                    ThemedButton("Last", {}, modifier = Modifier.testTag("last"))
                }
            }
        }

        composeRule.onNodeWithText("Disabled").assertIsNotEnabled()
        composeRule.onNodeWithTag("first")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithTag("last").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, disabledClicks) }
    }

    @Test
    fun modernSliderChangesWithLeftAndRightAndFinishesEachAdjustment() {
        var value by mutableFloatStateOf(0.5f)
        var finished = 0
        composeRule.setContent {
            ModernTvContent {
                ThemedSlider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { finished++ },
                    modifier = Modifier.testTag("slider"),
                )
            }
        }

        composeRule.onNodeWithTag("slider")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.runOnIdle {
            assertTrue(value > 0.5f)
            assertEquals(1, finished)
        }

        composeRule.onNodeWithTag("slider")
            .performKeyInput { pressKey(Key.DirectionLeft) }
            .assertIsFocused()

        composeRule.runOnIdle {
            assertEquals(0.5f, value, 0.001f)
            assertEquals(2, finished)
        }
    }

    @Test
    fun featuredPagerCanBeEnteredAndActivatedWithRemote() {
        var clicks = 0
        composeRule.setContent {
            ModernTvContent {
                Row {
                    ThemedButton("Before", {}, modifier = Modifier.width(80.dp).testTag("before"))
                    FeaturedHeroPager(
                        items = listOf(
                            CatalogItem("hero", "movie", "Featured", null, null, null, null),
                            CatalogItem("next", "movie", "Next featured", null, null, null, null),
                        ),
                        onClick = { clicks++ },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("before")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithContentDescription("Featured")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun remoteSelectOnTextFieldOpensKeyboardWithoutChangingText() {
        var keyboardShows = 0
        var value by mutableStateOf("query")
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { keyboardShows++ }
            override fun hide() = Unit
        }
        composeRule.setContent {
            ModernTvContent {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.tvTextInput().testTag("input"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("input")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()

        // Ignore any keyboard request made automatically as the text field receives focus.
        composeRule.runOnIdle { keyboardShows = 0 }
        composeRule.onNodeWithTag("input")
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle {
            assertEquals(1, keyboardShows)
            assertEquals("query", value)
        }
    }

    @Test
    fun railCollapseAndReopenTransferFocusWithoutLosingRemoteNavigation() {
        composeRule.setContent {
            ModernTvContent {
                Box(Modifier.fillMaxSize()) {
                    ThemedButton(
                        "Content",
                        {},
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 122.dp)
                            .testTag("content"),
                    )
                    StremioBottomBar(AppView.Home, backdrop = null, onSelect = {})
                }
            }
        }

        composeRule.onNodeWithText("Início")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithTag("content")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithContentDescription("Abrir menu")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText("Início").assertIsFocused()
    }

    @Composable
    private fun ModernTvContent(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalGlobalUiTheme provides GlobalUiTheme(style = "modern", hapticsEnabled = false),
            content = content,
        )
    }
}
