package com.stremio.mobile.presentation.util

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
@Config(sdk = [35], application = Application::class)
class TrailerLauncherTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val item = CatalogItem("tt1375666", "movie", "Inception", null, null, "2010", null)

    @Test
    fun opensTheYouTubeAppFirstThenTheLink() {
        val urls = TrailerLauncher.intents("YoHD9XEInc0").map { it.dataString }
        assertEquals(listOf("vnd.youtube:YoHD9XEInc0", "https://www.youtube.com/watch?v=YoHD9XEInc0"), urls)
    }

    @Test
    fun detailsShowTheTrailerButtonOnlyWhenThereIsOne() {
        var details by androidx.compose.runtime.mutableStateOf(MetaDetails(item, description = "x", trailerYouTubeId = "YoHD9XEInc0"))
        composeRule.setContent { DetailSheet(details = details, inLibrary = false, onBack = {}, onToggleLibrary = {}, onOpenStreams = {}) }
        composeRule.onNodeWithContentDescription("Trailer").assertExists()
        composeRule.runOnIdle { details = details.copy(trailerYouTubeId = null) }
        composeRule.onNodeWithContentDescription("Trailer").assertDoesNotExist()
    }
}
