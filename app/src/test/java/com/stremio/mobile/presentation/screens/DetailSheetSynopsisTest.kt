package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.MetaDetails
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The details sheet shows the original while translating, then the translation with a note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DetailSheetSynopsisTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val item = CatalogItem("tt0903747", "series", "Breaking Bad", null, null, "2008", null)

    private fun show(details: MetaDetails) = composeRule.setContent {
        DetailSheet(details = details, inLibrary = false, onBack = {}, onToggleLibrary = {}, onOpenStreams = {})
    }

    @Test
    fun originalStaysVisibleWhileTranslating() {
        show(MetaDetails(item, description = "A chemistry teacher turns to crime.", translatingDescription = true))
        composeRule.onNodeWithText("A chemistry teacher turns to crime.").assertExists()
        composeRule.onNodeWithText("Traduzindo sinopse…").assertExists()
    }

    @Test
    fun translatedSynopsisIsLabelled() {
        show(MetaDetails(item, description = "Um professor de química entra para o crime.", descriptionTranslated = true))
        composeRule.onNodeWithText("Um professor de química entra para o crime.").assertExists()
        composeRule.onNodeWithText("Sinopse traduzida automaticamente").assertExists()
        composeRule.onNodeWithText("Traduzindo sinopse…").assertDoesNotExist()
    }

    @Test
    fun missingSynopsisSaysSo() {
        show(MetaDetails(item, description = null))
        composeRule.onNodeWithText("Sinopse indisponível.").assertExists()
    }
}
