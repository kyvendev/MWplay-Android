package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.data.model.TvCategories
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Touch navigation of the TV tab's categories on the phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TvCategoryChipsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingEsportesSelectsItsCategory() {
        val all = ResourceRequest(base = "https://addon.example/", path = com.stremio.core.types.addon.ResourcePath(resource = "catalog", type = "tv", id = "canais", extra = emptyList()))
        val sports = all.copy(path = all.path.copy(extra = listOf(com.stremio.core.types.addon.ExtraValue(name = "genre", value = "Esportes"))))
        val row = TvCategories.build(
            options = listOf(TvCategories.Option(null, true, all), TvCategories.Option("Esportes", false, sports)),
            sportsByMetadata = false,
            label = { it },
        )
        var picked: TvCategories.Chip<ResourceRequest>? = null
        composeRule.setContent { TvCategoryChipsRow(row = row, onSelect = { picked = it }) }
        composeRule.onNodeWithText("Todos os canais").assertIsSelected()
        composeRule.onNodeWithText("Esportes").performClick()
        composeRule.runOnIdle {
            assertEquals(TvCategories.Kind.SPORTS, picked?.kind)
            assertEquals(sports, picked?.request)
        }
    }
}
