package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.MetaDetails
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The full-screen TV detail page must be usable with only the remote. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "w1280dp-h720dp-land-television-mdpi",
    application = Application::class,
)
@OptIn(ExperimentalTestApi::class)
class TvDetailFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var watchClicks = 0
    private var listClicks = 0

    private fun setDetails(isLoading: Boolean = false) {
        composeRule.setContent {
            DetailSheet(
                details = MetaDetails(
                    item = CatalogItem("tt1", "movie", "Movie", null, null, "2024", "8.0"),
                    description = "Synopsis",
                    isLoading = isLoading,
                ),
                inLibrary = false,
                onBack = {},
                onToggleLibrary = { listClicks++ },
                onOpenStreams = { watchClicks++ },
            )
        }
    }

    @Test
    fun watchIsInitiallyFocusedAndRemoteMovesBetweenActions() {
        setDetails()
        composeRule.onNodeWithText("Assistir").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Minha lista").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Minha lista")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Assistir").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            assertEquals(1, listClicks)
            assertEquals(1, watchClicks)
        }
    }

    @Test
    fun loadingDetailsFocusTheListActionInsteadOfDisabledWatch() {
        setDetails(isLoading = true)
        composeRule.onNodeWithText("Minha lista").assertIsFocused()
    }
}
