package com.stremio.mobile.presentation.util

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
import com.stremio.mobile.presentation.screens.DetailSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-television-mdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class TvTrailerButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun trailerIsReachableWithTheRemoteAfterMinhaLista() {
        assertEquals("vnd.youtube:YoHD9XEInc0", TrailerLauncher.intents("YoHD9XEInc0").first().dataString)
        composeRule.setContent {
            DetailSheet(
                details = MetaDetails(CatalogItem("tt1375666", "movie", "Inception", null, null, "2010", null), description = "x", trailerYouTubeId = "YoHD9XEInc0"),
                inLibrary = false,
                onBack = {},
                onToggleLibrary = {},
                onOpenStreams = {},
            )
        }
        composeRule.onNodeWithText("Assistir").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Minha lista").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Trailer").assertIsFocused()
    }
}
